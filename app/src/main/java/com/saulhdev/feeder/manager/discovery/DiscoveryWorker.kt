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
package com.saulhdev.feeder.manager.discovery

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Suggestion
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.utils.blobFile
import com.saulhdev.feeder.utils.blobFullFile
import com.saulhdev.feeder.utils.blobFullInputStream
import com.saulhdev.feeder.utils.blobInputStream
import com.saulhdev.feeder.utils.isFeedHost
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Looks at what the reader actually read, and notices what it kept linking to.
 *
 * Runs weekly rather than on every sync, because the question it answers
 * changes over weeks: three articles linking somewhere is a habit, and a habit
 * is not visible in an hour. Weekly also keeps the cost invisible — this reads
 * article bodies off disk and parses them, which is not something to do while
 * somebody is holding the phone.
 */
class DiscoveryWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { discover() }
            .onFailure { Log.e(TAG, "Discovery pass failed", it) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.failure() })
    }

    private suspend fun discover() {
        val db: NeoFeedDb by inject(NeoFeedDb::class.java)
        val articles = db.feedArticleDao()
        val suggestions = db.suggestionDao()
        val feeds = db.feedSourceDao()

        val since = System.currentTimeMillis() - WINDOW_MS
        val read = articles.readArticlesSince(since, MAX_ARTICLES)
        if (read.size < LinkHarvest.MENTIONS_NEEDED) {
            Log.i(TAG, "Only ${read.size} read articles in the window; nothing to learn from")
            return
        }

        // One article at a time, so a few thousand gzipped bodies never sit in
        // memory together.
        val counts = mutableMapOf<String, Int>()
        read.forEach { article ->
            // The full page first, the feed's own summary only as a fallback.
            //
            // This read the summary alone, which is why the screen stayed
            // empty. An RSS description is a teaser — a paragraph and a link
            // back to the article — so its only outbound link is to the
            // publisher, which is excluded as the article's own host. The
            // links that say anything about what a reader might follow next
            // are in the body of the piece, and the body is in a different
            // file that full-text fetching already writes.
            val full = blobFullFile(article.uuid, context.filesDir)
            val html = if (full.isFile) {
                runCatching {
                    blobFullInputStream(article.uuid, context.filesDir).use { it.reader().readText() }
                }.getOrNull()
            } else {
                val file = blobFile(article.uuid, context.filesDir)
                if (!file.isFile) null
                else runCatching {
                    blobInputStream(article.uuid, context.filesDir).use { it.reader().readText() }
                }.getOrNull()
            } ?: return@forEach
            val ownHost = runCatching { URL(article.link ?: "").host }.getOrNull()
            LinkHarvest.tally(counts, LinkHarvest.domainsIn(html, ownHost))
        }

        // Everything already subscribed to, and everything already offered —
        // including what was refused, because saying no once should mean no.
        // A hosted feed's own address names the service, not the publisher, so
        // a FeedBurner subscription to Android Authority would register as
        // "feedburner.com" and leave androidauthority.com looking unfollowed
        // — and then be suggested, as a site the reader already reads. The
        // articles know where they live even when the feed address does not.
        val subscribed = feeds.loadAllFeeds()
            .flatMap { feed ->
                val own = runCatching { feed.url.host }.getOrNull()
                if (own != null && !isFeedHost(own)) return@flatMap listOf(own)
                val link = articles.latestArticleLink(feed.id)
                listOfNotNull(own, runCatching { URL(link ?: "").host }.getOrNull())
            }
            .map(LinkHarvest::registrable)
            .toSet()
        val known = suggestions.knownHosts().map(LinkHarvest::registrable).toSet()

        val candidates = LinkHarvest.candidates(counts, subscribed + known)
            .take(MAX_SUGGESTIONS)

        val parser: FeedParser by inject(FeedParser::class.java)
        candidates.forEach { (host, mentions) ->
            // The same autodiscovery adding a feed by hand uses. A domain that
            // publishes no feed is not a suggestion — there would be nothing to
            // subscribe to — so it is dropped rather than recorded.
            val home = sloppyLinkToStrictURLNoThrows("https://$host")
            val found = runCatching { parser.getAlternateFeedLinksAtUrl(home) }
                .getOrDefault(emptyList())
                .firstOrNull() ?: return@forEach

            val feedUrl = found.first
            val title = runCatching { parser.parseFeedUrl(URL(feedUrl))?.title }
                .getOrNull().orEmpty().ifBlank { host }

            suggestions.insert(
                Suggestion(
                    host = LinkHarvest.registrable(host),
                    feedUrl = feedUrl,
                    title = title,
                    mentions = mentions,
                )
            )
            Log.i(TAG, "Suggesting $host on the evidence of $mentions read articles")
        }
    }

    companion object {
        private const val TAG = "Discovery"

        /**
         * How far back the evidence runs.
         *
         * Long enough that a habit shows and short enough that it is a current
         * one: what somebody was reading six months ago is not a suggestion.
         */
        private val WINDOW_MS = TimeUnit.DAYS.toMillis(30)

        /** A ceiling on the pass, so a heavy reader does not pay for it. */
        private const val MAX_ARTICLES = 500

        /**
         * How many to offer at once.
         *
         * A list of thirty is a directory, and the point of this is that each
         * one arrives with a reason worth reading.
         */
        private const val MAX_SUGGESTIONS = 5

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DiscoveryWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        // It fetches home pages looking for feeds. Nobody asked
                        // for that at this moment, so it waits for conditions
                        // that cost the reader nothing.
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "whisper_discovery",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
