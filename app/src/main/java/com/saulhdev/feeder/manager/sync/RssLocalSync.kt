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

import com.saulhdev.feeder.utils.usableImageUrl
import com.saulhdev.feeder.utils.SyncResult
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
import com.saulhdev.feeder.utils.deleteArticleFiles
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
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

/**
 * The feed client, with a disk cache.
 *
 * Every request already asked for revalidation - see getResponse - but the
 * client had no cache, so there was nothing to revalidate and every sync
 * downloaded every feed in full: ten megabytes an hour for a hundred and
 * twenty feeds that had mostly not changed. With a cache, OkHttp sends the
 * feed's ETag or Last-Modified back and a server with nothing new answers
 * 304 with no body. Feeds whose servers send neither still download in full;
 * nothing on this side can help those.
 *
 * Made on first use rather than at load, because the cache lives in the
 * app's cache directory and that needs a Context.
 */
private const val FEED_CACHE_BYTES = 20L * 1024 * 1024

@Volatile
private var feedClient: OkHttpClient? = null
private val feedClientLock = Any()

private fun syncHttpClient(context: Context): OkHttpClient =
    feedClient ?: synchronized(feedClientLock) {
        feedClient ?: OkHttpClient.Builder()
            .asFeedReader()
            .onlyPublicHttps()
            .cache(Cache(File(context.cacheDir, "feeds"), FEED_CACHE_BYTES))
            .build()
            .also { feedClient = it }
    }

/**
 * Whether a response is the copy already processed: a 304 from the server,
 * or the cache answering without asking (within getResponse's one-minute
 * window). Both mean the bytes are the ones the last sync parsed.
 */
internal fun servedUnchanged(networkCode: Int?, hadCachedCopy: Boolean): Boolean =
    hadCachedCopy && (networkCode == null || networkCode == 304)

suspend fun syncFeeds(
    context: Context,
    feedId: Long = ID_UNSET,
    feedTag: String = "",
    forceNetwork: Boolean = false,
    minFeedAgeMinutes: Int = 5
): SyncResult {
    // When this was asked for, before any wait for the lock. A forced sync
    // that had to queue behind another only needs what that one did not
    // fetch after this moment; see freshSince below.
    val requestedAt = Clock.System.now().toEpochMilliseconds()
    return syncMutex.withLock {
        val waited = Clock.System.now().toEpochMilliseconds() - requestedAt > QUEUED_AFTER_MS
        withContext(singleThreadedSync) {
            syncFeeds(
                context = context,
                feedId = feedId,
                feedTag = feedTag,
                maxFeedItemCount = prefs.itemsPerFeed.getValue().toInt(),
                forceNetwork = forceNetwork,
                minFeedAgeMinutes = minFeedAgeMinutes,
                freshSince = if (forceNetwork && waited) requestedAt else null,
            )
        }
    }
}

/** Longer than this for the lock means another sync was running. */
private const val QUEUED_AFTER_MS = 1_000L

internal suspend fun syncFeeds(
    context: Context,
    feedId: Long = ID_UNSET,
    feedTag: String = "",
    maxFeedItemCount: Int = 100,
    forceNetwork: Boolean = false,
    minFeedAgeMinutes: Int = 5,
    /**
     * For a forced sync that queued behind another: a feed fetched after this
     * moment already counts as fetched for it.
     *
     * A pull to refresh means "everything, now", and it forces a fetch of
     * every feed however recent. Behind a sync that was already running, that
     * meant downloading everything twice in a row - the reader asked at
     * 16:15, the running sync fetched every feed by 16:18, and the pull then
     * fetched them all again. Anything fetched after the pull was asked for
     * is exactly as fresh as the pull wanted.
     */
    freshSince: Long? = null,
): SyncResult {
    var result = SyncResult(due = 0)
    // Feeds that threw, counted for the history: a sync that reached 118 of
    // 120 feeds and one that reached none were both just "ok".
    val failedFeeds = AtomicInteger(0)
    // And the ones the server said had not changed, which cost a few hundred
    // bytes instead of the whole feed. See syncHttpClient.
    val unchangedFeeds = AtomicInteger(0)
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
                val staleTime: Long = when {
                    freshSince != null -> freshSince
                    forceNetwork -> Clock.System.now().toEpochMilliseconds()
                    else -> Clock.System.now().minus(minFeedAgeMinutes.toLong(), DateTimeUnit.MINUTE)
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
                    // Selection only: a queued forced sync picks by staleness
                    // like any other. The fetch itself is still forced.
                    forceNetwork = forceNetwork && freshSince == null
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

                                val changed = syncFeed(
                                    context = context,
                                    feedsRepo = feedsRepo,
                                    articleRepo = articlesRepo,
                                    feedSql = feed,
                                    filesDir = context.filesDir,
                                    maxFeedItemCount = maxFeedItemCount,
                                    forceNetwork = forceNetwork,
                                    downloadTime = downloadTime
                                )
                                if (!changed) unchangedFeeds.incrementAndGet()

                                // Successful sync, update lastSync
                                feedsRepo.setCurrentlySyncingOn(
                                    feedId = feed.id,
                                    syncing = false,
                                    lastSync = Clock.System.now(),
                                )
                                // And forget any run of failures: whatever was
                                // wrong is not wrong now.
                                feedsRepo.clearFailures(feed.id)
                            } catch (e: CancellationException) {
                                // Not a failure, and this catch used to treat
                                // it as one. `Throwable` includes
                                // CancellationException, so closing the
                                // launcher panel mid-sync recorded a failure
                                // against every feed still in flight — three
                                // of those and a publisher appears under
                                // "Feeds that stopped working" for the crime
                                // of the reader having swiped away. A device
                                // log showed five sources newly failing with
                                // "Job was cancelled" as the only reason.
                                //
                                // The clock is not stamped either: nothing was
                                // fetched, so the feed is exactly as stale as
                                // it was, and saying otherwise would hide a
                                // sync that never happened.
                                //
                                // NonCancellable because this coroutine is
                                // already cancelled and a suspending write
                                // would be cancelled with it, leaving the feed
                                // marked as syncing for ever — a spinner that
                                // never stops.
                                withContext(NonCancellable) {
                                    feedsRepo.setCurrentlySyncingOn(
                                        feedId = feed.id,
                                        syncing = false,
                                    )
                                }
                                // Rethrown, because swallowing it breaks the
                                // structured concurrency that cancelled us.
                                throw e
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
                                failedFeeds.incrementAndGet()
                            }
                        }
                    }
                }

                jobs.joinAll()
                result = SyncResult(
                    due = feedsToFetch.size,
                    failed = failedFeeds.get(),
                    unchanged = unchangedFeeds.get(),
                )

            }
        } catch (e: CancellationException) {
            // Leaving the feed is not an error to report; see the per-feed
            // catch above.
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Outer error", e)
            result = SyncResult.broken(e)
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
): Boolean {
    Log.d(TAG, "Fetching ${feedSql.title}")

    val response: Response =
        syncHttpClient(context).getResponse(url = feedSql.url, forceNetwork = forceNetwork)

    // Nothing new: the parse, the article writes and the icon lookup all
    // redo what the last sync did, and the parse is the part that fills the
    // heap. Only when the source has articles, though - a source whose
    // articles were cleared, or one removed and added back at the same
    // address, would otherwise stay empty until the publisher next posted.
    if (response.isSuccessful &&
        servedUnchanged(response.networkResponse?.code, response.cacheResponse != null) &&
        articleRepo.countInFeed(feedSql.id) > 0
    ) {
        response.close()
        Log.d(TAG, "Unchanged: ${feedSql.title}")
        cleanUpFeed(articleRepo, feedSql, filesDir)
        return false
    }

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
    //
    // "Already stored" is asked of usableImageUrl rather than isNotBlank. A
    // source with no icon stores `https:` - the default URL's fallback,
    // see FeedItem.feedIconUrl - which is not blank, so this step read it as
    // an icon already found and the site was never asked. Sources that
    // declare no icon in their feed therefore kept a monogram for good.
    val storedIcon = usableImageUrl(syncedFeed.feedImage.toString())
    val declaredIcon = feed.icon?.takeIf { it.isNotBlank() }
    val resolvedIcon = when {
        declaredIcon != null -> declaredIcon
        storedIcon != null -> storedIcon
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

    cleanUpFeed(articleRepo, syncedFeed, filesDir)
    return true
}

/**
 * Drops articles older than the sync range, with their files.
 *
 * Its own step so an unchanged feed still ages out: a publisher that stops
 * posting would otherwise keep last month's articles for ever.
 */
private suspend fun cleanUpFeed(articleRepo: ArticleRepository, feed: Feed, filesDir: File) {
    val days = getSyncDays(prefs)
    val minKeptPubDate = Clock.System.now().minus(
        period = DateTimePeriod(days = days),
        timeZone = TimeZone.currentSystemDefault()
    ).toEpochMilliseconds()
    val ids = articleRepo.getItemsToBeCleanedFromFeed(
        feedId = feed.id,
        minKeptPubDate = minKeptPubDate
    )
    Log.d(TAG, "Cleanup ${feed.title}: days=$days cutoff=$minKeptPubDate deleting=${ids.size}")

    withContext(Dispatchers.IO) {
        for (id in ids) {
            try {
                deleteArticleFiles(itemId = id, filesDir = filesDir)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to delete the files of $id", e)
            }
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
