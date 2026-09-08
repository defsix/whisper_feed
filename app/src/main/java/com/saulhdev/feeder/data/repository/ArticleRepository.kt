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

    fun getEnabledFeedItems(): Flow<List<FeedItem>> = articlesDao.getAllEnabledFeedItems()
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
    fun getFeedItemsByTags(tags: Set<String>): Flow<List<FeedItem>> =
        feedsDao.getEnabledFeeds()
            .map { feeds ->
                feeds.filter { feed -> feed.tags.any { it in tags } }.map(Feed::id)
            }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList())
                else articlesDao.getFeedItemsByFeedIdsFlow(ids)
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

    fun countUnread(): Flow<Int> = articlesDao.countUnread().flowOn(cc)

    fun countReadSince(since: Long): Flow<Int> = articlesDao.countReadSince(since).flowOn(cc)

    suspend fun bookmarkArticle(
        articleId: String,
        bookmark: Boolean,
    ) = withContext(jcc) {
        articlesDao.getArticleById(articleId)?.let {
            articlesDao.updateFeedArticle(it.copy(bookmarked = bookmark, pinned = bookmark))
        }
    }

    suspend fun unpinArticle(
        articleId: String,
        pin: Boolean = false,
    ) = withContext(jcc) {
        articlesDao.getArticleById(articleId)?.let {
            articlesDao.updateFeedArticle(it.copy(pinned = pin))
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
        .flowOn(cc)
}