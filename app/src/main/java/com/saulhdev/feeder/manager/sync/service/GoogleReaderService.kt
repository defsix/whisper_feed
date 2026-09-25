/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.manager.sync.service

import android.content.Context
import android.util.Log
import com.saulhdev.feeder.data.content.SyncAccount
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderIds
import com.saulhdev.feeder.manager.sync.syncFeeds
import com.saulhdev.feeder.utils.isSameFeedUrl
import java.net.URL

/**
 * Whisper with a Google Reader account attached.
 *
 * The division of labour is the part worth understanding. This service does
 * **not** fetch articles from the server. It syncs the *subscription list* and
 * the *read and starred state*, and leaves the fetching to the local path that
 * already works.
 *
 * That is a deliberate choice rather than a shortcut:
 *
 * - Whisper's articles carry things the protocol has no field for — the full
 *   text it extracted, the image it picked out, the summary it built. Taking
 *   articles from the server would mean either losing those or fetching twice.
 * - Fetching locally keeps the app's behaviour identical with and without an
 *   account, which is the whole local-first arrangement. An account changes
 *   *which feeds* and *what has been read*, not what an article is.
 * - It works when the server is down. A reader whose FreshRSS box is offline
 *   still gets their news.
 *
 * What the reader gets is what they actually wanted from sync: the same
 * subscriptions and the same read state on every device.
 */
class GoogleReaderService(
    private val context: Context,
    private val account: SyncAccount,
    private val sources: SourcesRepository,
    private val articles: ArticleRepository,
    private val api: GoogleReaderApi = GoogleReaderApi(account.serverUrl),
) : RssService() {

    override suspend fun sync(forceNetwork: Boolean): SyncOutcome {
        val auth = account.authToken
        if (auth.isEmpty()) return SyncOutcome.SignedOut

        return try {
            val remote = api.subscriptions(auth)
            reconcileSubscriptions(remote)

            // Articles still come from the feeds themselves; see the note above.
            val feeds = syncFeeds(context = context, forceNetwork = forceNetwork)

            // Learn which of our articles the server knows about, then apply
            // what it says about them. The order matters: read state is
            // useless until the mapping exists.
            mapRemoteIds(auth)
            pullReadState(auth)

            account.lastSync = System.currentTimeMillis()
            SyncOutcome.Success(feeds = feeds)
        } catch (t: Throwable) {
            Log.e(TAG, "Sync failed", t)
            // A 401 means the token has been revoked server-side, which needs
            // the reader rather than a retry. Anything else is worth retrying
            // quietly — a server being down is not a reason to log someone out.
            if (t.message?.contains("401") == true) SyncOutcome.SignedOut
            else SyncOutcome.Failed(t)
        }
    }

    /**
     * Makes the local subscription list match the server's.
     *
     * Additions and category changes are applied. **Removals are not**, and
     * that is the one place this deliberately does less than a full sync would:
     * deleting somebody's feeds because a server did not mention them is
     * unrecoverable, and the failure modes that would trigger it — a partial
     * response, a server mid-migration, an account that is not the one they
     * thought — are exactly the ones a first version will meet. Feeds the
     * server does not know about are left alone and can be removed by hand.
     */
    private suspend fun reconcileSubscriptions(remote: List<com.saulhdev.feeder.manager.sync.greader.Subscription>) {
        val local = sources.getAllSources()

        remote.forEach { sub ->
            val url = runCatching { URL(sub.feedUrl) }.getOrNull() ?: return@forEach
            val existing = local.firstOrNull { isSameFeedUrl(it.url, url) }
            val tag = sub.folders.joinToString(",")

            if (existing == null) {
                sources.insertSource(
                    Feed(
                        title = sub.title.ifBlank { url.host },
                        url = url,
                        tag = tag,
                        isEnabled = true,
                    )
                )
            } else if (existing.tag != tag && tag.isNotEmpty()) {
                // The server's folders win for a feed it knows about: they are
                // what the reader set on whichever device they set it.
                sources.updateSource(existing.copy(tag = tag))
            }
        }
    }

    /**
     * Attaches the server's ids to the articles this app already has.
     *
     * The two sides name the same article differently and nothing connected
     * them: a local `uuid` is generated here and means nothing anywhere else,
     * while the server assigns an id of its own. Articles are fetched from the
     * feeds rather than from the server, so the server's id never arrives with
     * them — this is the call that goes and asks.
     *
     * Matched on the article's address, which is the only thing both sides
     * know. Not the guid: that is set by the publisher and has nothing to do
     * with the id the server assigned.
     */
    private suspend fun mapRemoteIds(auth: String) {
        val items = api.streamContents(auth)
        var attached = 0
        items.forEach { item ->
            val (link, remoteId) = item.mapping() ?: return@forEach
            attached += articles.attachRemoteId(link, remoteId)
        }
        Log.i(TAG, "Mapped $attached of ${items.size} server items to local articles")
    }

    /**
     * Brings read state down from the server and applies it.
     *
     * Asks for the unread ids rather than the read ones, because unread is the
     * smaller set by a wide margin on any real account — a year of reading is
     * tens of thousands of read articles and a few dozen unread ones.
     *
     * **Only articles the server has claimed are touched.** That is the whole
     * safety property of this, and the reason it can be applied at all: an
     * article with no `remoteId` is one the server has never mentioned, so its
     * absence from a list of unread ids means nothing about whether it has
     * been read. Marking those read is exactly the mistake this used to avoid
     * by throwing the answer away.
     */
    private suspend fun pullReadState(auth: String) {
        val unread = api.itemIds(
            auth = auth,
            stream = GoogleReaderIds.STREAM_READING_LIST,
            excludeTag = GoogleReaderIds.TAG_READ,
        ).toSet()

        if (unread.isEmpty()) {
            // Everything read, or a server that answered oddly. Applying a
            // blanket "mark everything read" on an empty response is precisely
            // the failure a first version meets, so it is left alone.
            Log.i(TAG, "Server reported nothing unread; leaving read state alone")
            return
        }

        val ids = unread.toList()
        val markedRead = articles.markReadFromServer(ids)
        val markedUnread = articles.markUnreadFromServer(ids)
        Log.i(
            TAG,
            "Read state applied: $markedRead read, $markedUnread unread, " +
                "of ${articles.countWithRemoteId()} mapped articles"
        )
    }

    override suspend fun setRead(articleId: String, read: Boolean) {
        if (read) articles.markRead(articleId) else articles.unmarkRead(listOf(articleId))

        // And tell the server, if it knows this article. An article with no
        // remoteId is one the server has never seen — there is nothing to tell
        // it, and inventing an id would edit somebody else's article.
        val remoteId = articles.remoteIdFor(articleId) ?: return
        val auth = account.authToken
        if (auth.isEmpty()) return
        val token = api.writeToken(auth) ?: return
        api.editTag(
            auth = auth,
            token = token,
            itemIds = listOf(remoteId),
            addTag = if (read) GoogleReaderIds.TAG_READ else null,
            removeTag = if (read) null else GoogleReaderIds.TAG_READ,
        )
    }

    override suspend fun setStarred(articleId: String, starred: Boolean) {
        articles.bookmarkArticle(articleId, starred)
    }

    override suspend fun subscribe(url: String, title: String?, folder: String?) {
        val auth = account.authToken.ifEmpty { return }
        val token = api.writeToken(auth) ?: return
        api.editSubscription(auth, token, "subscribe", url, title, folder)
    }

    override suspend fun unsubscribe(url: String) {
        val auth = account.authToken.ifEmpty { return }
        val token = api.writeToken(auth) ?: return
        api.editSubscription(auth, token, "unsubscribe", url)
    }

    override suspend fun editFeed(url: String, title: String?, folder: String?) {
        val auth = account.authToken.ifEmpty { return }
        val token = api.writeToken(auth) ?: return
        api.editSubscription(auth, token, "edit", url, title, folder)
    }

    private companion object {
        const val TAG = "GoogleReaderSync"
    }
}
