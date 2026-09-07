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

    @Query("SELECT * FROM ArticleIdWithLink")
    fun getArticleIdLinks(): Flow<List<ArticleIdWithLink>>

    // Embedded FeedItem
    @Transaction
    @Query(
        """
    SELECT Article.* FROM Article
    JOIN Feeds ON Article.feedId = Feeds.id
    WHERE Feeds.isEnabled = 1
    ORDER BY Article.primarySortTime DESC
    """
    )
    fun getAllEnabledFeedItems(): Flow<List<FeedItem>>

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
    """
    )
    fun getFeedItemsByFeedIdsFlow(feedIds: List<Long>): Flow<List<FeedItem>>



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
}
