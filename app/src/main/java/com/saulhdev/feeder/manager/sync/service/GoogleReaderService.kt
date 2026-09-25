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

import com.saulhdev.feeder.manager.sync.greader.isCatchAllFolder
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
import com.saulhdev.feeder.utils.normalizeFeedUrl
import com.saulhdev.feeder.utils.isUnmetered
import com.saulhdev.feeder.utils.getSyncDays
import com.saulhdev.feeder.manager.sync.prefs
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderState
import com.saulhdev.feeder.manager.sync.greader.planSubscriptions
import com.saulhdev.feeder.manager.sync.greader.readChanges
import com.saulhdev.feeder.manager.sync.greader.rememberAfter
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
            // The order is the design. Feeds first, both ways, so the
            // articles fetched next come from the right list. Then the
            // matching, which says which of those articles the server knows.
            // Then what changed here goes up, before anything comes down: a
            // read made in Whisper and not yet sent would otherwise be undone
            // by the server's older answer, which is what happened to every
            // read in the first version of this.
            val token = api.writeToken(auth)
            syncSubscriptions(auth, token)

            // Articles still come from the feeds themselves; see the note above.
            val feeds = syncFeeds(context = context, forceNetwork = forceNetwork)

            mapRemoteIds(auth)
            val sent = pushChanges(auth, token)
            if (sent) {
                pullReadState(auth)
                pullStars(auth)
            } else {
                Log.w(TAG, "Changes not sent; the server's read state waits for the next sync")
            }

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
     * Makes the two subscription lists agree, in both directions.
     *
     * See [planSubscriptions] for how each side's additions and removals are
     * told apart. Removals on the server are still never applied here:
     * deleting somebody's feeds because a server did not mention them is
     * unrecoverable, and a partial response, a server mid-migration or the
     * wrong account are exactly what a first version meets. A feed removed
     * in Whisper is removed from the server, because that is what the reader
     * did, here, on purpose.
     *
     * Folders from the server win for a feed both sides have: they are what
     * the reader set on whichever device they set it.
     */
    private suspend fun syncSubscriptions(auth: String, token: String?) {
        val remote = api.subscriptions(auth)
        val local = sources.getAllSubscriptions()
        val localByKey = local.associateBy { normalizeFeedUrl(it.url) }
        val remoteByKey = remote.mapNotNull { sub ->
            runCatching { URL(sub.feedUrl) }.getOrNull()?.let { normalizeFeedUrl(it) to sub }
        }.toMap()

        val lastLocal = GoogleReaderState.lastLocal(context)
        val everOnServer = GoogleReaderState.everOnServer(context)
        val plan = planSubscriptions(localByKey.keys, remoteByKey.keys, lastLocal, everOnServer)

        plan.addLocal.forEach { key ->
            val sub = remoteByKey.getValue(key)
            val url = URL(sub.feedUrl)
            sources.insertSource(
                Feed(
                    title = sub.title.ifBlank { url.host },
                    url = url,
                    tag = sub.folders.joinToString(","),
                    isEnabled = true,
                )
            )
        }

        // Only with a write token: without one every edit would be refused,
        // and the plan is simply made again next time.
        val subscribed = mutableSetOf<String>()
        val unsubscribed = mutableSetOf<String>()
        if (token != null) {
            plan.subscribe.forEach { key ->
                val feed = localByKey.getValue(key)
                val folder = feed.tags.firstOrNull { it.isNotBlank() }
                if (api.editSubscription(auth, token, "subscribe", feed.url.toString(), feed.title, folder)) {
                    subscribed += key
                }
            }
            plan.unsubscribe.forEach { key ->
                // By the server's own id for it, which every server accepts.
                val sub = remoteByKey.getValue(key)
                if (api.editSubscription(auth, token, "unsubscribe", sub.id.ifBlank { sub.feedUrl })) {
                    unsubscribed += key
                }
            }
        }
        if (plan.subscribe.isNotEmpty() || plan.unsubscribe.isNotEmpty() || plan.addLocal.isNotEmpty()) {
            Log.i(
                TAG,
                "Feeds: ${subscribed.size} of ${plan.subscribe.size} sent, " +
                    "${unsubscribed.size} of ${plan.unsubscribe.size} removed, ${plan.addLocal.size} added here"
            )
        }

        // Feeds both sides have: the server's folders.
        remote.forEach { sub ->
            val url = runCatching { URL(sub.feedUrl) }.getOrNull() ?: return@forEach
            val existing = local.firstOrNull { isSameFeedUrl(it.url, url) } ?: return@forEach
            val tag = sub.folders.joinToString(",")
            if (existing.tag != tag && tag.isNotEmpty()) {
                sources.updateSource(existing.copy(tag = tag))
            } else if (tag.isEmpty() && existing.tags.isNotEmpty() && existing.tags.all(::isCatchAllFolder)) {
                // Taken as a category by an earlier version; see isCatchAllFolder.
                sources.updateSource(existing.copy(tag = ""))
            }
        }

        // What is remembered is what was done, so a subscribe that failed is
        // tried again next time rather than taken for a feed the server dropped.
        val (nextLocal, nextEver) = rememberAfter(
            plan = plan,
            local = localByKey.keys,
            server = remoteByKey.keys,
            lastLocal = lastLocal,
            everOnServer = everOnServer,
            subscribed = subscribed,
            unsubscribed = unsubscribed,
        )
        GoogleReaderState.rememberSubscriptions(context, nextLocal, nextEver)
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
     *
     * Only what arrived since the last match, with an hour's overlap: each
     * item comes with its whole article, and asking for the lot every half
     * hour would download the server's copy of everything, every time.
     *
     * An article newly matched keeps what the reader did here: read in
     * Whisper before the server knew it, it is sent up as read, rather than
     * being marked unread by a server that has simply not heard yet. The same
     * for saved.
     */
    private suspend fun mapRemoteIds(auth: String) {
        val startedAt = System.currentTimeMillis()
        val before = articles.mappedArticles().mapTo(HashSet()) { it.uuid }
        val last = GoogleReaderState.mappedAt(context)
        // The first match brings the server's copy of every article in the
        // window, which after signing in with a hundred feeds is tens of
        // megabytes. It waits for Wi-Fi; after that each match is the last
        // half hour's worth, and small.
        if (last == 0L && !isUnmetered(context)) {
            Log.i(TAG, "First match of articles waits for Wi-Fi")
            return
        }
        // As far back as Whisper keeps articles, and a day more: anything
        // older has already gone from here, and there is nothing to match.
        val since = if (last > 0) last - MAP_OVERLAP_MS else startedAt - (getSyncDays(prefs) + 1) * DAY_MS
        val page = api.allStreamContents(auth, since = since)
        var attached = 0
        page.items.forEach { item ->
            val (link, remoteId) = item.mapping() ?: return@forEach
            attached += articles.attachRemoteId(link, remoteId)
        }
        val newlyMapped = articles.mappedArticles().filter { it.uuid !in before }
        GoogleReaderState.updateOutbox(context) { outbox ->
            outbox
                .withRead(newlyMapped.filter { it.readAt != 0L && it.uuid !in outbox.unread }.map { it.uuid }, true)
                .let { o ->
                    newlyMapped.filter { it.bookmarked && it.uuid !in o.unstar }
                        .fold(o) { acc, a -> acc.withStar(a.uuid, true) }
                }
        }
        if (page.complete) GoogleReaderState.setMappedAt(context, startedAt)
        Log.i(TAG, "Mapped $attached of ${page.items.size} server items, ${newlyMapped.size} newly")
    }

    /**
     * Sends what the reader changed here: reads, unreads, saves, unsaves.
     *
     * By the server's id, so only for articles it has claimed. An article it
     * has not claimed by now has nothing to be sent to - its feed is not on
     * the server - and is let go rather than kept for ever.
     *
     * False if anything failed, and then nothing is let go: it all waits for
     * the next sync, and the server's read state is not applied this time,
     * because it would be older than what is still waiting to go.
     */
    private suspend fun pushChanges(auth: String, token: String?): Boolean {
        val outbox = GoogleReaderState.outbox(context)
        if (outbox.isEmpty) return true
        if (token == null) return false
        val remoteIds = articles.mappedArticles().associate { it.uuid to it.remoteId }
        val batches = listOf(
            Triple(outbox.read, GoogleReaderIds.TAG_READ, true),
            Triple(outbox.unread, GoogleReaderIds.TAG_READ, false),
            Triple(outbox.star, GoogleReaderIds.TAG_STARRED, true),
            Triple(outbox.unstar, GoogleReaderIds.TAG_STARRED, false),
        )
        var ok = true
        batches.forEach { (ids, tag, add) ->
            ids.mapNotNull(remoteIds::get).chunked(EDIT_BATCH).forEach { chunk ->
                val done = api.editTag(
                    auth = auth,
                    token = token,
                    itemIds = chunk,
                    addTag = if (add) tag else null,
                    removeTag = if (add) null else tag,
                )
                if (!done) ok = false
            }
        }
        if (ok) {
            // Everything in the outbox as it was read above is done with:
            // sent, or never sendable. Anything the reader did during the
            // sending is still there.
            GoogleReaderState.updateOutbox(context) { it.without(outbox) }
        }
        Log.i(TAG, "Sent ${outbox.pending.count { it in remoteIds }} changes; ${if (ok) "all accepted" else "some refused, kept"}")
        return ok
    }

    /**
     * Brings read state down from the server and applies it.
     *
     * **Only articles the server has claimed are touched.** An article with
     * no `remoteId` is one the server has never mentioned, so its absence from
     * a list of unread ids means nothing about whether it has been read.
     *
     * The unread list is read page by page, and only a *complete* one is
     * allowed to mark anything read: "not in the list" means read only when
     * the list is all of it. An incomplete one still marks unread what it
     * does name. An empty one changes nothing - everything read, or a server
     * that answered oddly, and the second is the one to guard against.
     *
     * Nothing still waiting in the outbox is touched: what the reader did
     * here is newer than anything the server can say.
     */
    private suspend fun pullReadState(auth: String) {
        val page = api.allItemIds(
            auth = auth,
            stream = GoogleReaderIds.STREAM_READING_LIST,
            excludeTag = GoogleReaderIds.TAG_READ,
        )
        val unread = page.items.toHashSet()
        val waiting = GoogleReaderState.outbox(context).pending
        val change = readChanges(articles.mappedArticles(), unread, page.complete, waiting)
        articles.applyServerRead(read = change.first, unread = change.second)
        Log.i(
            TAG,
            "Read state applied: ${change.first.size} read, ${change.second.size} unread, " +
                "${unread.size} unread on the server${if (page.complete) "" else " (partial)"}"
        )
    }

    /**
     * Stars from the server, as saves here. Additions only: an empty or
     * partial list must not unsave anything, and a save is the one thing the
     * reader would least forgive losing. Unsaving here does reach the server.
     */
    private suspend fun pullStars(auth: String) {
        val starred = api.allItemIds(auth = auth, stream = GoogleReaderIds.STREAM_STARRED).items.toHashSet()
        if (starred.isEmpty()) return
        val waiting = GoogleReaderState.outbox(context).pending
        val toSave = articles.mappedArticles()
            .filter { !it.bookmarked && it.remoteId in starred && it.uuid !in waiting }
            .map { it.uuid }
        if (toSave.isNotEmpty()) articles.applyServerStars(toSave)
        Log.i(TAG, "Stars applied: ${toSave.size} saved")
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

        /** Items per edit-tag call; the protocol repeats a parameter per item. */
        const val EDIT_BATCH = 100

        /** Overlap with the last match, for items the server crawled late. */
        const val MAP_OVERLAP_MS = 60 * 60_000L

        const val DAY_MS = 24 * 60 * 60_000L
    }
}
