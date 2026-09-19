/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
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

package com.saulhdev.feeder.data.repository

import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.utils.blobInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleRepository(db: NeoFeedDb) {
    private val cc = Dispatchers.IO
    private val jcc = Dispatchers.IO + SupervisorJob()
    private val articlesDao = db.feedArticleDao()
    private val feedsDao = db.feedSourceDao()

    suspend fun deleteArticles(ids: List<String>) = withContext(jcc) {
        articlesDao.deleteArticles(ids)
    }

    suspend fun deleteArticlesForFeed(feedId: Long) = withContext(jcc) {
        articlesDao.deleteFeedArticle(feedId)
    }

    suspend fun deleteArticlesMatchingWords(words: Set<String>, filesDir: File) = withContext(jcc) {
        val blocked = words.map { it.lowercase() }.filter { it.isNotBlank() }
        if (blocked.isEmpty()) return@withContext
        val articles = articlesDao.loadAllEnabledArticles()
        val toDelete = articles.mapNotNull { article ->
            val haystack = buildString {
                append(article.title)
                append(article.plainTitle)
                append(article.description)
                append(article.plainSnippet)
                article.author?.let { append(it) }
                article.link?.let { append(it) }
                try {
                    blobInputStream(article.uuid, filesDir).bufferedReader().use { append(it.readText()) }
                } catch (_: Throwable) {
                }
            }.lowercase()
            if (blocked.any { haystack.contains(it) }) article.uuid else null
        }
        if (toDelete.isNotEmpty()) {
            articlesDao.deleteArticles(toDelete)
        }
    }

    suspend fun getArticleByGuid(guid: String, feedId: Long): Article? {
        return withContext(jcc) {
            articlesDao.loadArticle(guid = guid, feedId = feedId)
        }
    }

    fun getArticleById(articleId: String): Flow<Article?> =
        articlesDao.loadArticleById(id = articleId)
            .flowOn(cc)

    fun getEnabledFeedItems(limit: Int = FEED_WINDOW): Flow<List<FeedItem>> =
        articlesDao.getAllEnabledFeedItems(limit)
        .flowOn(cc)

    /**
     * Articles from every source carrying any of [tags].
     *
     * This used to be a single query matching `Feeds.tag IN (:tags)`, which
     * only ever matched a feed whose *entire* tag string equalled a chip. A
     * feed tagged "Tech,News" therefore matched neither the Tech chip nor the
     * News chip and simply disappeared when either was selected — invisibly,
     * because single-category feeds still worked.
     *
     * Matching a comma list needs one LIKE per tag, which Room cannot express
     * against a set in a static query, so the feeds are resolved first and the
     * articles fetched by id. The id list is deduplicated before it reaches the
     * article query: a sync writes `currentlySyncing` to Feeds constantly, and
     * without that every one of those writes would restart the query for an
     * identical set of ids.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getFeedItemsByTags(tags: Set<String>, limit: Int = FEED_WINDOW): Flow<List<FeedItem>> =
        feedsDao.getEnabledFeeds()
            .map { feeds ->
                feeds.filter { feed -> feed.tags.any { it in tags } }.map(Feed::id)
            }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList())
                else articlesDao.getFeedItemsByFeedIdsFlow(ids, limit)
            }
            .flowOn(cc)

    /**
     * Persists articles, then writes their bodies to disk *outside* the database
     * transaction — the writes are per-article file I/O and have no business
     * holding the write lock while they run.
     */
    suspend fun updateOrInsertArticle(
        itemsWithText: List<Pair<Article, String>>,
        block: suspend (Article, String) -> Unit
    ) = withContext(jcc) {
        val stored = articlesDao.insertOrUpdate(itemsWithText)
        stored.forEach { (article, text) -> block(article, text) }
    }

    /** Records that an article was opened. First open wins; re-opens are a no-op. */
    suspend fun markRead(articleId: String) = withContext(jcc) {
        articlesDao.markRead(articleId, System.currentTimeMillis())
    }

    /**
     * Records an article being opened to read in full.
     *
     * The strongest signal the app has, and the only unambiguous one:
     * everything else is inferred from a card going past. Written at the
     * moment of the tap rather than on the way back, because there may be no
     * way back — an article opened in the browser from the launcher overlay
     * often never returns to Whisper at all, and treating that as a failure to
     * read would punish exactly the articles somebody went furthest to read.
     */
    suspend fun markOpened(articleId: String) = withContext(jcc) {
        articlesDao.markOpened(articleId, System.currentTimeMillis())
    }

    /** Where a recent article from this feed actually lives; see the DAO. */
    suspend fun publisherLink(feedId: Long): String? = withContext(jcc) {
        articlesDao.latestArticleLink(feedId)
    }

    /** Adds to an article's accumulated time on screen, up to [DWELL_CAP_MS]. */
    suspend fun addDwell(articleId: String, millis: Long) = withContext(jcc) {
        if (millis <= 0L) return@withContext
        articlesDao.addDwell(articleId, millis, DWELL_CAP_MS)
    }

    /**
     * Marks every unread article read, returning what it changed.
     *
     * The ids come back so the action can be undone: after the write every
     * article carries the same timestamp, and nothing distinguishes the ones
     * this marked from the ones that were already read.
     *
     * Chunked because SQLite binds a limited number of host parameters per
     * statement — a few hundred unread articles is an ordinary feed, and one
     * IN clause holding all of them fails rather than truncating.
     */
    suspend fun markAllRead(): List<String> = withContext(jcc) {
        val ids = articlesDao.unreadIds()
        val now = System.currentTimeMillis()
        ids.chunked(SQLITE_ARG_LIMIT).forEach { articlesDao.markReadBatch(it, now) }
        ids
    }

    /** Puts back what [markAllRead], or a run of scroll marks, took. */
    suspend fun unmarkRead(ids: List<String>) = withContext(jcc) {
        ids.chunked(SQLITE_ARG_LIMIT).forEach { articlesDao.unmarkRead(it) }
    }

    /** Reads per source since [since], for the reading-habit weight term. */
    fun readsPerSource(since: Long): Flow<Map<Long, Int>> =
        articlesDao.readsPerSource(since)
            .map { rows -> rows.associate { it.feedId to it.reads } }
            .flowOn(cc)

    /** Every article in the database, read or not, enabled source or not. */
    suspend fun countAll(): Int = withContext(cc) { articlesDao.countAll() }

    fun countUnread(): Flow<Int> = articlesDao.countUnread().flowOn(cc)

    fun countReadSince(since: Long): Flow<Int> = articlesDao.countReadSince(since).flowOn(cc)

    /**
     * Saves an article, and only that.
     *
     * This used to set `pinned` to the same value, so saving something put it
     * at the top of the feed and unsaving took it down again. The two are
     * different things — saving is "I want to find this later", pinning is "I
     * am following this, keep it in front of me" — and conflating them meant
     * neither could be used without the other happening.
     */
    suspend fun bookmarkArticle(
        articleId: String,
        bookmark: Boolean,
    ) = withContext(jcc) {
        articlesDao.getArticleById(articleId)?.let {
            articlesDao.updateFeedArticle(it.copy(bookmarked = bookmark))
        }
    }

    /** Holds an article at the top of the feed, or lets it go. */
    suspend fun setPinned(
        articleId: String,
        pinned: Boolean,
    ) = withContext(jcc) {
        articlesDao.getArticleById(articleId)?.let {
            articlesDao.updateFeedArticle(it.copy(pinned = pinned))
        }
    }

    suspend fun getItemsToBeCleanedFromFeed(feedId: Long, minKeptPubDate: Long) = withContext(jcc) {
        articlesDao.getItemsToBeCleanedFromFeed(
            feedId = feedId,
            minKeptPubDate = minKeptPubDate
        )
    }

    fun getFeedsItemsWithDefaultFullTextParse(allFeeds: Boolean): Flow<List<ArticleIdWithLink>> =
        articlesDao.getArticleIdLinks(allFeeds)
            .flowOn(cc)

    fun getBookmarkedFeedItems(): Flow<List<FeedItem>> = articlesDao.getAllBookmarkedFeedItems()

    /** Attaches a server's id to the article at that address. */
    suspend fun attachRemoteId(link: String, remoteId: String): Int = withContext(cc) {
        articlesDao.attachRemoteId(link, remoteId)
    }

    /** The server's id for one article, if a server has claimed it. */
    suspend fun remoteIdFor(uuid: String): String? = withContext(cc) {
        articlesDao.remoteIdFor(uuid)
    }

    /**
     * Applies a server's unread list, in chunks SQLite will accept.
     *
     * `NOT IN (:list)` becomes one bind parameter per id and SQLite stops at
     * 999 by default, so a thousand unread articles would throw rather than
     * sync. The read pass has to see the whole list at once to be correct —
     * chunking a NOT IN would mark an article read for being absent from a
     * chunk it was never going to be in — so the guard is on the count, and a
     * list too long to bind is left alone rather than half-applied.
     */
    suspend fun markReadFromServer(unreadRemoteIds: List<String>): Int = withContext(cc) {
        if (unreadRemoteIds.size > SQLITE_ARG_LIMIT) 0
        else articlesDao.markReadExcept(unreadRemoteIds, System.currentTimeMillis())
    }

    /** The other direction, which chunks safely because it is an IN. */
    suspend fun markUnreadFromServer(unreadRemoteIds: List<String>): Int = withContext(cc) {
        unreadRemoteIds.chunked(SQLITE_ARG_LIMIT).sumOf { articlesDao.markUnread(it) }
    }

    /** How many articles a server has claimed. */
    suspend fun countWithRemoteId(): Int = withContext(cc) { articlesDao.countWithRemoteId() }

    /** When each feed last had an article, for the source list's sort. */
    fun latestArticlePerFeed(): Flow<Map<Long, Long>> =
        articlesDao.latestArticlePerFeed()
            .map { rows -> rows.associate { it.feedId to it.latest } }
            .flowOn(cc)
        .flowOn(cc)
}

/**
 * How many ids to put in one `IN` clause.
 *
 * SQLite's default limit is 999 host parameters; well under it, because the
 * cost of an extra statement is nothing next to a query that throws.
 */
private const val SQLITE_ARG_LIMIT = 500

/**
 * How many articles the feed holds at once.
 *
 * A window, not a page. The distinction matters: the weighting, the
 * breaking-news clustering and the two diversity rules all reason about the
 * *whole* list — clustering has to see every article to find a story across
 * sources, and spreading a run of one publisher needs the ordering entire. A
 * pager that handed those a page at a time would change what they mean rather
 * than making them cheaper.
 *
 * So the list stays whole and is simply bounded. Five hundred is far past
 * anywhere anyone scrolls, and it makes the working set a property of this
 * constant instead of a property of how many feeds somebody happens to have
 * subscribed to.
 */
const val FEED_WINDOW = 500

/**
 * The most time on screen any one article can contribute.
 *
 * Thirty seconds. Long enough to cover reading a headline, a summary and a
 * decent extract; short enough that a phone left face-up on a desk with the
 * feed open cannot turn one article into the most-read thing anybody owns.
 *
 * The cap matters more than the number. Time on screen has no natural ceiling
 * and every other signal here does, so without one a single forgotten session
 * would dominate every comparison it took part in.
 */
const val DWELL_CAP_MS = 30_000L
