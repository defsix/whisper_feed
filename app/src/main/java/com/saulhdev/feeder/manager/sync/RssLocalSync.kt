/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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

package com.saulhdev.feeder.manager.sync

import android.content.Context
import android.util.Log
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.ID_ALL
import com.saulhdev.feeder.data.db.ID_UNSET
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.entity.JsonFeed
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.viewmodels.STATS_WINDOW_DAYS
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.manager.models.getResponse
import com.saulhdev.feeder.manager.models.scheduleFullTextParse
import com.saulhdev.feeder.utils.HttpIdentity.asFeedReader
import com.saulhdev.feeder.utils.blobFile
import com.saulhdev.feeder.utils.blobOutputStream
import com.saulhdev.feeder.utils.getSyncDays
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis
import kotlin.time.Clock
import kotlin.time.Instant

val syncMutex = Mutex()
val prefs: FeedPreferences by inject(FeedPreferences::class.java)
val singleThreadedSync = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
const val TAG = "RssLocalSync"

/**
 * One client for every feed. A fresh OkHttpClient was being built per feed, so a
 * sync of an imported OPML created dozens of connection pools and thread pools
 * that could share nothing — no connection reuse, no keep-alive across feeds.
 */
/**
 * How many feeds are fetched and parsed at the same time.
 *
 * Not a throughput knob: it is what keeps the heap survivable. Each feed being
 * parsed holds its whole payload, its Article objects and their content bodies
 * live at once, so the peak is this number multiplied by the largest feed
 * rather than by the whole subscription list.
 */
private const val MAX_CONCURRENT_FEEDS = 4

private val syncHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder().asFeedReader().onlyPublicHttps().build()
}

suspend fun syncFeeds(
    context: Context,
    feedId: Long = ID_UNSET,
    feedTag: String = "",
    forceNetwork: Boolean = false,
    minFeedAgeMinutes: Int = 5
): Boolean {
    return syncMutex.withLock {
        withContext(singleThreadedSync) {
            syncFeeds(
                context = context,
                feedId = feedId,
                feedTag = feedTag,
                maxFeedItemCount = prefs.itemsPerFeed.getValue().toInt(),
                forceNetwork = forceNetwork,
                minFeedAgeMinutes = minFeedAgeMinutes
            )
        }
    }
}

internal suspend fun syncFeeds(
    context: Context,
    feedId: Long = ID_UNSET,
    feedTag: String = "",
    maxFeedItemCount: Int = 100,
    forceNetwork: Boolean = false,
    minFeedAgeMinutes: Int = 5
): Boolean {
    var result = false
    val feedsRepo: SourcesRepository by inject(SourcesRepository::class.java)
    val articlesRepo: ArticleRepository by inject(ArticleRepository::class.java)
    val downloadTime = Clock.System.now()
    var needFullTextSync = false
    // Read once: the answer cannot change halfway through a sync, and reading
    // it per feed would hit the DataStore once for every source.
    val fullTextForAll = prefs.fullTextForAllFeeds.getValue()

    // The reading tally, trimmed to what the statistics screen can still show.
    // A generous margin past the window, because the charts are the only
    // reader and a few extra rows cost nothing next to the alternative of
    // discarding a day somebody could still be looking at.
    articlesRepo.pruneTally(
        java.time.LocalDate.now()
            .minusDays(STATS_WINDOW_DAYS.toLong() + 7)
            .toString()
    )
    val time = measureTimeMillis {
        try {
            supervisorScope {
                val sRepository: SourcesRepository by inject(SourcesRepository::class.java)
                val staleTime: Long = if (forceNetwork) {
                    Clock.System.now().toEpochMilliseconds()
                } else {
                    Clock.System.now().minus(minFeedAgeMinutes.toLong(), DateTimeUnit.MINUTE)
                        .toEpochMilliseconds()
                }

                val coroutineContext =
                    Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                        Log.e(TAG, "Error during sync", throwable)
                    }

                val feedsToFetch = feedsToSync(
                    repository = sRepository,
                    feedId = feedId,
                    tag = feedTag,
                    staleTime = staleTime,
                    forceNetwork = forceNetwork
                )

                Log.d(TAG, "Feeds to sync: ${feedsToFetch.size}")

                // Every feed at once was the arrangement, and it does not
                // survive a real subscription list. Forty-five feeds launched
                // together each parse their payload and build their articles
                // at the same moment, so every one of those strings is live
                // simultaneously: a device log showed the heap pinned at
                // 244MB of 256MB with five hundred blocking collections, the
                // main thread stalled for over a second at a time, and a feed
                // that could not draw because nothing could get a frame in.
                val gate = Semaphore(MAX_CONCURRENT_FEEDS)
                val jobs = feedsToFetch.map { feed ->
                    needFullTextSync = needFullTextSync ||
                            feed.fullTextByDefault || fullTextForAll
                    launch(coroutineContext) {
                        gate.withPermit {
                            try {
                                // Mark as syncing START
                                feedsRepo.setCurrentlySyncingOn(feedId = feed.id, syncing = true)

                                syncFeed(
                                    context = context,
                                    feedsRepo = feedsRepo,
                                    articleRepo = articlesRepo,
                                    feedSql = feed,
                                    filesDir = context.filesDir,
                                    maxFeedItemCount = maxFeedItemCount,
                                    forceNetwork = forceNetwork,
                                    downloadTime = downloadTime
                                )

                                // Successful sync, update lastSync
                                feedsRepo.setCurrentlySyncingOn(
                                    feedId = feed.id,
                                    syncing = false,
                                    lastSync = Clock.System.now(),
                                )
                                // And forget any run of failures: whatever was
                                // wrong is not wrong now.
                                feedsRepo.clearFailures(feed.id)
                            } catch (e: Throwable) {
                                Log.e(TAG, "Failed to sync ${feed.title}: ${feed.url}", e)
                                // Error, clear syncing flag but don't update lastSync
                                feedsRepo.setCurrentlySyncingOn(feedId = feed.id, syncing = false)
                                // Counted rather than only logged. Without this
                                // a feed that has stopped working is
                                // indistinguishable from one with nothing to
                                // say, and the reader finds out by noticing a
                                // silence months later.
                                feedsRepo.recordFailure(feed.id)
                            }
                        }
                    }
                }

                jobs.joinAll()
                result = feedsToFetch.isNotEmpty()

            }
        } catch (e: Throwable) {
            Log.e(TAG, "Outer error", e)
        } finally {
            if (needFullTextSync) {
                scheduleFullTextParse()
            }
        }
    }
    Log.d(TAG, "Completed in $time ms")
    return result
}

private suspend fun syncFeed(
    context: Context,
    feedsRepo: SourcesRepository,
    articleRepo: ArticleRepository,
    feedSql: Feed,
    filesDir: File,
    maxFeedItemCount: Int,
    forceNetwork: Boolean = false,
    downloadTime: Instant
) {
    Log.d(TAG, "Fetching ${feedSql.title}")

    val response: Response =
        syncHttpClient.getResponse(url = feedSql.url, forceNetwork = forceNetwork)
    val feedParser = FeedParser()
    val feed: JsonFeed = response.use {
        response.body.let { responseBody ->
            when {
                !response.isSuccessful -> {
                    throw ResponseFailure("${response.code} when fetching ${feedSql.title}: ${feedSql.url}")
                }

                else                   -> {
                    Log.d(TAG, "Fetching correct ${feedSql.title}")
                    feedParser.parseFeedResponse(
                        url = response.request.url.toUrl(),
                        responseBody = responseBody
                    )
                }
            }
        }
    }.let {
        when {
            it.icon?.startsWith("data") == true -> it.copy(icon = null)
            else                                -> it
        }
    }

    val syncedFeed = feedSql.copy(lastSync = Clock.System.now())
    val items = feed.items
    Log.d(TAG, "Parsed ${items?.size ?: 0} items for ${feedSql.title}")
    val days = getSyncDays(prefs)
    val minKeptPubDate = Clock.System.now().minus(
        period = DateTimePeriod(days = days),
        timeZone = TimeZone.currentSystemDefault()
    ).toEpochMilliseconds()
    // Trimmed *before* anything is built. maxFeedItemCount was passed in and
    // then used only for the cleanup afterwards, so a feed offering 135
    // entries had 135 Article objects and 135 content bodies materialised and
    // written, and the setting that was supposed to cap it at 25 capped
    // nothing. Feeds are newest-first by convention, so the newest are taken
    // and then reversed, since insertion goes oldest first.
    val articles =
        items?.take(maxFeedItemCount)
            ?.reversed()
            ?.map { item ->
                val itemGuid = (item.id ?: item.url).toString()
                val article = (articleRepo.getArticleByGuid(
                    guid = itemGuid,
                    feedId = syncedFeed.id
                ) ?: Article(firstSyncedTime = downloadTime))
                    .updateFromParsedEntry(item, itemGuid, feed, syncedFeed.id)
                article to (item.content_html ?: item.content_text ?: "")
            }
            ?.filter { (article, _) ->
                article.pubDate !in 1..<minKeptPubDate
            }
            ?.filterBlockedWords() ?: emptyList()

    Log.d(
        TAG,
        "Prepared ${articles.size} of ${items?.size ?: 0} for ${feedSql.title} (cap $maxFeedItemCount)"
    )

    // The source's mark, for the line under each headline. Three places to
    // look, cheapest first: the feed's own <icon>, then whatever is already
    // stored, then — only when both are empty — the site itself. That last
    // step is a network round trip, so it runs once per source rather than on
    // every sync: a feed that declares no icon and whose site offers none
    // keeps looking, but a feed that has one never asks again.
    val storedIcon = syncedFeed.feedImage.toString()
    val declaredIcon = feed.icon?.takeIf { it.isNotBlank() }
    val resolvedIcon = when {
        declaredIcon != null -> declaredIcon
        storedIcon.isNotBlank() -> storedIcon
        else -> feed.home_page_url
            ?.let { runCatching { sloppyLinkToStrictURL(it) }.getOrNull() }
            ?.let { feedParser.findSiteIcon(it) }
    }

    feedsRepo.updateSource(
        syncedFeed.copy(
            title = syncedFeed.title,
            feedImage = resolvedIcon?.let { sloppyLinkToStrictURLNoThrows(it) }
                ?: syncedFeed.feedImage
        ))

    articleRepo.updateOrInsertArticle(articles) { article, text ->
        withContext(Dispatchers.IO) {
            blobOutputStream(article.uuid, filesDir).bufferedWriter().use {
                it.write(text)
            }
        }
    }

    val ids = articleRepo.getItemsToBeCleanedFromFeed(
        feedId = syncedFeed.id,
        minKeptPubDate = minKeptPubDate
    )
    Log.d(
        TAG,
        "Cleanup ${feedSql.title}: days=$days cutoff=$minKeptPubDate deleting=${ids.size}"
    )

    for (id in ids) {
        val file = blobFile(itemId = id, filesDir = filesDir)
        try {
            if (file.isFile) {
                file.delete()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to delete $file", e)
        }
    }

    articleRepo.deleteArticles(ids)
}

class ResponseFailure(message: String?) : Exception(message)

fun List<Pair<Article, String>>.filterBlockedWords(): List<Pair<Article, String>> {
    val blocked = prefs.blockedWords.getValue()
        .map { it.lowercase() }
        .filter { it.isNotBlank() }
    if (blocked.isEmpty()) return this
    return filter { (article, text) ->
        val haystack = buildString {
            append(article.title)
            append(article.plainTitle)
            append(article.description)
            append(article.plainSnippet)
            article.author?.let { append(it) }
            article.link?.let { append(it) }
            append(text)
        }.lowercase()
        blocked.none { haystack.contains(it) }
    }
}

internal suspend fun feedsToSync(
    repository: SourcesRepository,
    feedId: Long,
    tag: String,
    staleTime: Long = -1L,
    forceNetwork: Boolean = false,
): List<Feed> {

    val sources = when {
        feedId > 0 -> {
            if (forceNetwork) {
                repository.loadFeedById(feedId)?.let { listOf(it) } ?: emptyList()
            } else {
                repository.loadFeedIfStale(feedId = feedId, staleTime = staleTime)
            }
        }

        feedId == ID_ALL -> {
            Log.d(TAG, "Checking all feeds  = $forceNetwork")
            if (forceNetwork) {
                repository.getAllSources()

            } else {
                repository.loadFeedIfStale(ID_ALL, staleTime)
            }
        }

        tag.isNotEmpty() -> {
            repository.loadFeedsByTag(tag)
        }

        else -> repository.getAllSources()
    }

    return if (tag.isNotEmpty() && feedId == ID_ALL) {
        Log.d(TAG, "Filtering by tag: $tag")
        sources.filter { it.tag.contains(tag) }
    } else {
        Log.d(TAG, "No tag filtering applied $sources")
        sources
    }
}
