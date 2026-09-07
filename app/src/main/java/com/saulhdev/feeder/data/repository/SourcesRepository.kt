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

package com.saulhdev.feeder.data.repository

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.saulhdev.feeder.data.db.ID_ALL
import com.saulhdev.feeder.data.db.ID_UNSET
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.manager.models.scheduleFullTextParse
import com.saulhdev.feeder.manager.sync.FeedSyncer
import com.saulhdev.feeder.manager.sync.requestFeedSync
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import com.saulhdev.feeder.utils.isSameFeedUrl
import org.koin.java.KoinJavaComponent.inject
import java.net.URL

class SourcesRepository(db: NeoFeedDb) {
    private val cc = Dispatchers.IO
    private val jcc = Dispatchers.IO + SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO) + CoroutineName("FeedSourceRepository")
    private val feedsDao = db.feedSourceDao()
    private val workManager: WorkManager by inject(WorkManager::class.java)

    suspend fun insertSource(feed: Feed) = withContext(jcc) {
        feedsDao.insert(feed)
    }

    /**
     * The existing subscription this address would collide with, if any.
     *
     * Matched on [normalizeFeedUrl] rather than on the string, so the same feed
     * typed with or without `www.`, with or without a trailing slash, or over
     * the other scheme is recognised. `Feeds.url` is uniquely indexed and the
     * insert strategy is REPLACE, so an unnoticed duplicate does not sit beside
     * the original — it replaces the row and takes that feed's articles down
     * with it.
     */
    suspend fun findSourceByUrl(url: URL): Feed? = withContext(jcc) {
        feedsDao.getFeedByURL(url)
            ?: feedsDao.loadAllFeeds().firstOrNull { isSameFeedUrl(it.url, url) }
    }

    suspend fun updateSource(feed: Feed, resync: Boolean = false) {
        withContext(jcc) {
            if (feedsDao.existsById(feed.id)) {
                feedsDao.update(feed)
                if (resync) requestFeedSync(feed.id)
                if (feed.fullTextByDefault) scheduleFullTextParse()
            }
        }
    }

    fun getAllSourcesFlow(): Flow<List<Feed>> = feedsDao.getAllFeeds()
        .flowOn(cc)

    suspend fun getAllSources(): List<Feed> = feedsDao.loadFeeds()

    fun getEnabledSources(): Flow<List<Feed>> = feedsDao.getEnabledFeeds()
        .flowOn(cc)

    fun getSourceById(id: Long): Flow<Feed?> = feedsDao.getFeedById(id)
        .flowOn(cc)

    suspend fun loadFeedsByTag(tag: String): List<Feed> = withContext(jcc) {
        feedsDao.loadFeedsByTag(tag)
    }

    suspend fun loadFeedById(feedId: Long): Feed? = withContext(jcc) {
        feedsDao.loadFeedById(feedId)
    }

    suspend fun loadFeedIfStale(feedId: Long, staleTime: Long): List<Feed> = withContext(jcc) {
        if (feedId == ID_ALL)
            feedsDao.loadFeedIfStale(staleTime)
        else
            feedsDao.loadFeedIfStale(feedId, staleTime)?.let { listOf(it) } ?: emptyList()
    }

    suspend fun getAllTags(): List<String> {
        return feedsDao.getAllTags()
    }

    fun getAllTagsFlow(): Flow<List<String>> = feedsDao.getAllTagsFlow()
        .map { rawTags ->
            rawTags.flatMap { tagString ->
                tagString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }.distinct()
        }
        .flowOn(cc)

    suspend fun loadFeedIds(): List<Long> = withContext(jcc) {
        feedsDao.loadFeedIds()
    }

    @OptIn(FlowPreview::class)
    val isSyncing: StateFlow<Boolean> =
        workManager.getWorkInfosByTagFlow(FeedSyncer::class.qualifiedName!!)
            .map {
                workManager.pruneWork()
                it.any { work ->
                    work.state == WorkInfo.State.RUNNING || work.state == WorkInfo.State.BLOCKED
                }
            }
            .debounce(1000L)
            .stateIn(
                scope,
                SharingStarted.Lazily,
                false
            )

    fun setCurrentlySyncingOn(feedId: Long, syncing: Boolean) {
        scope.launch {
            feedsDao.setCurrentlySyncingOn(feedId, syncing)
        }
    }

    fun setCurrentlySyncingOn(feedId: Long, syncing: Boolean, lastSync: kotlin.time.Instant) {
        scope.launch {
            feedsDao.setCurrentlySyncingOn(feedId, syncing, lastSync)
        }
    }

    /**
     * The source removed most recently, for as long as it can still be undone.
     *
     * Removal is confirmed by a dialog and then the screen closes, so by the
     * time a user realises they picked the wrong feed there is nothing left on
     * screen to undo it from. Holding the row here lets the list they land back
     * on offer it. Cleared once [undoDeleteSource] runs or [forgetDeletedSource]
     * is called by the snackbar going away.
     */
    private val _recentlyDeleted = MutableStateFlow<Feed?>(null)
    val recentlyDeleted: StateFlow<Feed?> = _recentlyDeleted.asStateFlow()

    fun deleteFeed(feedId: Long) {
        scope.launch {
            _recentlyDeleted.value = feedsDao.loadFeedById(feedId)
            feedsDao.deleteFeedById(feedId)
        }
    }

    /**
     * Puts a removed source back, and resyncs it.
     *
     * Its articles are not restored: deleting a feed cascades to them, and
     * keeping a tombstone of every article of every removed feed to make this
     * exact is not worth the storage. The feed refetches instead, so what comes
     * back is the current contents rather than the old ones — read state and
     * bookmarks within it are genuinely lost.
     */
    fun undoDeleteSource() {
        scope.launch {
            val feed = _recentlyDeleted.value ?: return@launch
            _recentlyDeleted.value = null
            // Insert under a fresh id: the old one may have been handed out
            // again, and nothing outside the row refers to it any more.
            val id = feedsDao.insert(feed.copy(id = ID_UNSET))
            requestFeedSync(id)
        }
    }

    fun forgetDeletedSource() {
        _recentlyDeleted.value = null
    }
}