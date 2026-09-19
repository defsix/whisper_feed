/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.saulhdev.feeder.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.db.models.SourceReadCount
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FeedArticleDao {
    @Upsert
    suspend fun upsert(vararg item: Article)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeedArticle(item: Article): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeedArticle(items: List<Article>): List<Long>

    @Update
    suspend fun updateFeedArticle(item: Article): Int

    @Update
    suspend fun updateFeedArticle(items: List<Article>): Int

    @Delete
    suspend fun deleteFeedArticle(item: Article): Int

    @Query(
        """
        DELETE FROM Article WHERE uuid IN (:ids)
        """
    )
    suspend fun deleteArticles(ids: List<String>): Int

    @Query(
        """
        DELETE FROM Article WHERE uuid IN (:ids)
        """
    )
    suspend fun deleteFeedArticle(ids: List<String>): Int

    @Query(
        """
        DELETE FROM Article WHERE feedId = :feedId
        """
    )
    suspend fun deleteFeedArticle(feedId: Long?): Int

    /**
     * Empties several feeds without emptying the reader.
     *
     * Bookmarked and pinned articles are kept. Clearing a source is a tidying
     * action — "this feed is sitting on nine hundred items I will never read"
     * — and someone who saved an article did the opposite of asking for it to
     * go. The next sync refills the feed from the server anyway, so the only
     * thing this can destroy for good is the one thing it excludes.
     */
    @Query(
        """
        DELETE FROM Article
        WHERE feedId IN (:feedIds) AND bookmarked = 0 AND pinned = 0
        """
    )
    suspend fun clearArticlesForFeeds(feedIds: List<Long>): Int

    @Query("SELECT * FROM Article WHERE guid IS :guid AND feedId IS :feedId")
    suspend fun loadArticle(guid: String, feedId: Long?): Article?

    @Query("SELECT * FROM Article WHERE uuid IS :id")
    suspend fun getArticleById(id: String): Article?

    @Query("SELECT * FROM Article WHERE uuid IS :id")
    fun loadArticleById(id: String): Flow<Article?>

    @Query(
        """
        SELECT Article.* FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1
    """
    )
    suspend fun loadAllEnabledArticles(): List<Article>

    @Query(
        """
        SELECT uuid FROM Article
        WHERE feedId = :feedId AND pinned = 0 AND bookmarked = 0
        AND pubDateV2 < :minKeptPubDate
        """
    )
    suspend fun getItemsToBeCleanedFromFeed(
        feedId: Long,
        minKeptPubDate: Long,
    ): List<String>

    @Query(
        """
        UPDATE Article SET readAt = :readAt WHERE uuid = :id AND readAt = 0
        """
    )
    suspend fun markRead(id: String, readAt: Long): Int

    /**
     * The unread articles currently in the feed, so a bulk mark can be undone.
     *
     * Collected before the write rather than derived after it: once everything
     * is marked with the same timestamp there is no way to tell what this
     * action changed from what was already read.
     */
    @Query(
        """
        SELECT Article.uuid FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1 AND Article.readAt = 0
        """
    )
    suspend fun unreadIds(): List<String>

    @Query("UPDATE Article SET readAt = :readAt WHERE uuid IN (:ids)")
    suspend fun markReadBatch(ids: List<String>, readAt: Long)

    /**
     * Records that an article was opened, the first time it is.
     *
     * `openedAt = 0` in the WHERE clause so a reopened article keeps the time
     * of the first opening rather than being rewritten on every visit: this
     * answers "was this wanted", which is a thing that happened once, and a
     * moving timestamp would quietly make a re-read look like a new read.
     *
     * Marks it read in the same statement, because opening an article is the
     * least ambiguous way of reading one and leaving it unread would be a lie
     * the rest of the app then has to work around.
     */
    @Query(
        """
        UPDATE Article SET openedAt = :at, readAt = CASE WHEN readAt = 0 THEN :at ELSE readAt END
        WHERE uuid = :id AND openedAt = 0
        """
    )
    suspend fun markOpened(id: String, at: Long): Int

    /**
     * Adds to an article's time on screen.
     *
     * Capped in SQL rather than in Kotlin so a read-modify-write race between
     * the feed and the overlay cannot lose an increment or exceed the ceiling
     * — both surfaces run this against the same row, and the arithmetic is the
     * thing that has to be atomic.
     */
    @Query(
        """
        UPDATE Article SET dwellMs = MIN(dwellMs + :addMs, :capMs)
        WHERE uuid = :id
        """
    )
    suspend fun addDwell(id: String, addMs: Long, capMs: Long)

    @Query("UPDATE Article SET readAt = 0 WHERE uuid IN (:ids)")
    suspend fun unmarkRead(ids: List<String>)

    /**
     * How many articles from each source have been read since [since].
     *
     * The raw material for the reading-habit term in the weighting: a source
     * someone keeps opening is one they want more of. Grouped in SQL because
     * the alternative is loading every article to count them in Kotlin.
     */
    @Query(
        """
        SELECT feedId AS feedId, COUNT(*) AS reads FROM Article
        WHERE readAt >= :since
        GROUP BY feedId
        """
    )
    fun readsPerSource(since: Long): Flow<List<SourceReadCount>>

    /**
     * Every row in the table, with no join and no condition.
     *
     * Deliberately unfiltered: this exists to tell "the database is empty"
     * apart from "the database is full and something is hiding it", and a
     * count that shared the feed query's conditions could not do that.
     */
    @Query("SELECT COUNT(*) FROM Article")
    suspend fun countAll(): Int

    @Query(
        """
        SELECT COUNT(*) FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1 AND Article.readAt = 0
        """
    )
    fun countUnread(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM Article
        JOIN Feeds ON Article.feedId = Feeds.id
        WHERE Feeds.isEnabled = 1 AND Article.readAt >= :since
        """
    )
    fun countReadSince(since: Long): Flow<Int>

    /**
     * Articles whose full text should be prefetched.
     *
     * The ArticleIdWithLink view answers this for the per-source switch, but a
     * view takes no parameters and the global switch is a parameter, so the
     * same rule is spelled out here with [allFeeds] widening it.
     */
    @Query(
        """
        SELECT Article.uuid, Article.link FROM Article
        JOIN Feeds f ON Article.feedId = f.id
        WHERE :allFeeds OR f.fullTextByDefault = 1 OR Article.bookmarked = 1
        """
    )
    fun getArticleIdLinks(allFeeds: Boolean): Flow<List<ArticleIdWithLink>>

    // Embedded FeedItem
    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Feeds.isEnabled = 1
    ORDER BY Article.primarySortTime DESC
    LIMIT :limit
    """
    )
    /**
     * The feed, newest first, capped.
     *
     * It had no cap at all: every article of every enabled source, whole rows
     * including the full article text, rebuilt on every emission. Forty-five
     * sources is a few thousand of those held in memory to show the twenty on
     * screen. The cap is a window rather than a page — see FEED_WINDOW — and
     * search passes a larger one, because a search that only covered the
     * newest few hundred would quietly stop being true.
     */
    fun getAllEnabledFeedItems(limit: Int): Flow<List<FeedItem>>

    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Article.bookmarked = 1 AND Feeds.isEnabled = 1
    ORDER BY Article.pinned DESC, Article.pubDateV2 DESC
    """
    )
    fun getAllBookmarkedFeedItems(): Flow<List<FeedItem>>

    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Article.feedId IN (:feedIds) AND Feeds.isEnabled = 1
    ORDER BY Article.primarySortTime DESC
    LIMIT :limit
    """
    )
    fun getFeedItemsByFeedIdsFlow(feedIds: List<Long>, limit: Int): Flow<List<FeedItem>>



    /**
     * Writes a feed's articles in one transaction and returns them paired with
     * their body text, uuids assigned, for the caller to persist to disk.
     *
     * The blob writes used to happen inside this transaction, one file at a
     * time, via a `withContext(Dispatchers.IO)` callback. That held the write
     * lock for the whole of a feed's disk I/O and switched dispatchers inside a
     * Room transaction, which Room confines to its own. Inserts were also issued
     * one row at a time despite a list overload existing.
     */
    @Transaction
    suspend fun insertOrUpdate(
        itemsWithText: List<Pair<Article, String>>
    ): List<Pair<Article, String>> {
        val (toUpdateItems, toInsertItems) = itemsWithText.partition { (item, _) ->
            item.uuid.isNotEmpty()
        }

        updateFeedArticle(toUpdateItems.map { (item, _) -> item })

        val inserted = toInsertItems.map { (item, text) ->
            item.copy(uuid = UUID.randomUUID().toString()) to text
        }
        insertFeedArticle(inserted.map { (article, _) -> article })

        return toUpdateItems + inserted
    }

    /**
     * The most recent article in each feed, as epoch milliseconds.
     *
     * Deliberately not `Feeds.lastSync`, which records when Whisper last
     * *fetched* a feed and says nothing about whether anything was there. A
     * feed checked faithfully every hour for a year that has published nothing
     * since March has a very recent lastSync and is the exact feed somebody
     * sorting this way is looking for.
     *
     * primarySortTime rather than pubDateV2: an item with no publication date
     * of its own falls back to when it arrived, so this is never zero for a
     * feed that has articles.
     */
    @Query(
        """
    SELECT feedId AS feedId, MAX(primarySortTime) AS latest
    FROM Article GROUP BY feedId
    """
    )
    fun latestArticlePerFeed(): Flow<List<FeedLatestArticle>>

    /**
     * Attaches a server's id to the article at a given link.
     *
     * Matched on link because that is the only thing both sides know. The
     * article was fetched from the feed by this app, so it has a local uuid the
     * server has never seen; the server names the same article with an id this
     * app has never seen. The canonical URL is what they agree on.
     */
    @Query("UPDATE Article SET remoteId = :remoteId WHERE link = :link AND remoteId IS NULL")
    suspend fun attachRemoteId(link: String, remoteId: String): Int

    /** The server's id for one article, for pushing a change back. */
    @Query("SELECT remoteId FROM Article WHERE uuid = :uuid")
    suspend fun remoteIdFor(uuid: String): String?

    /**
     * Marks read everything the server did not list as unread.
     *
     * Scoped to articles that have a remoteId, which is the whole safety
     * property: an article the server has never claimed is left alone rather
     * than assumed read because it was absent from a list it was never in.
     */
    @Query(
        """
    UPDATE Article SET readAt = :now
    WHERE remoteId IS NOT NULL AND readAt = 0 AND remoteId NOT IN (:unreadRemoteIds)
    """
    )
    suspend fun markReadExcept(unreadRemoteIds: List<String>, now: Long): Int

    /** And back the other way, for something read here and unread there. */
    @Query(
        """
    UPDATE Article SET readAt = 0
    WHERE remoteId IS NOT NULL AND readAt != 0 AND remoteId IN (:unreadRemoteIds)
    """
    )
    suspend fun markUnread(unreadRemoteIds: List<String>): Int

    /**
     * Articles the reader actually read, most recent first.
     *
     * `readAt` rather than delivery: what was put in front of somebody says
     * nothing, and the whole of the discovery pass rests on the difference.
     */
    @Query(
        """
    SELECT * FROM Article
    WHERE readAt >= :since AND readAt != 0
    ORDER BY readAt DESC LIMIT :limit
    """
    )
    suspend fun readArticlesSince(since: Long, limit: Int): List<Article>

    /** How many articles a server has claimed, for the sync log. */
    @Query("SELECT COUNT(*) FROM Article WHERE remoteId IS NOT NULL")
    suspend fun countWithRemoteId(): Int
}

/** One row of [FeedArticleDao.latestArticlePerFeed]. */
data class FeedLatestArticle(
    val feedId: Long,
    val latest: Long,
)
