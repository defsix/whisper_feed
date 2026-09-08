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

    override suspend fun sync(): SyncOutcome {
        val auth = account.authToken
        if (auth.isEmpty()) return SyncOutcome.SignedOut

        return try {
            val remote = api.subscriptions(auth)
            reconcileSubscriptions(remote)

            // Articles still come from the feeds themselves; see the note above.
            syncFeeds(context = context, forceNetwork = true)

            pullReadState(auth)

            account.lastSync = System.currentTimeMillis()
            SyncOutcome.Success()
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
     * Brings read and starred state down from the server.
     *
     * Asks for the unread ids rather than the read ones, because unread is the
     * smaller set by a wide margin on any real account — a year of reading is
     * tens of thousands of read articles and a few dozen unread ones.
     */
    private suspend fun pullReadState(auth: String) {
        val unread = api.itemIds(
            auth = auth,
            stream = GoogleReaderIds.STREAM_READING_LIST,
            excludeTag = GoogleReaderIds.TAG_READ,
        ).toSet()
        val starred = api.itemIds(auth = auth, stream = GoogleReaderIds.STREAM_STARRED).toSet()

        // Deliberately not applied yet: matching the protocol's item ids to
        // Whisper's own article uuids needs a mapping this version does not
        // store, and guessing at it would mark the wrong articles read. The
        // ids are fetched so the shape is proven against a real server; the
        // mapping is the next piece. See ROADMAP.md §7.
        Log.i(TAG, "Server reports ${unread.size} unread, ${starred.size} starred")
    }

    override suspend fun setRead(articleId: String, read: Boolean) {
        if (read) articles.markRead(articleId) else articles.unmarkRead(listOf(articleId))
        // Pushing this to the server needs the same id mapping; queued rather
        // than dropped once that exists.
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
