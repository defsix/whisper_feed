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

package com.saulhdev.feeder.viewmodels

import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.entity.SORT_CHRONOLOGICAL
import com.saulhdev.feeder.data.entity.SORT_SOURCE
import com.saulhdev.feeder.data.entity.SORT_TITLE
import com.saulhdev.feeder.data.entity.SortFilterModel
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class ArticleListViewModel(
    private val articleRepo: ArticleRepository,
    feedsRepo: SourcesRepository,
    val prefs: FeedPreferences,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    private val sortFilterState = combine(
        prefs.sortingFilter.get(),
        prefs.sortingAsc.get(),
        prefs.sourcesFilter.get(),
        prefs.tagsFilter.get(),
    ) { sort, sortAsc, sources, tags ->
        SortFilterModel(sort, sortAsc, sources, tags)
    }
        .stateIn(
            ioScope,
            SharingStarted.Eagerly,
            SortFilterModel()
        )

    /**
     * Articles for the current category selection, with sync-time invalidation
     * bursts collapsed.
     *
     * A sync writes `currentlySyncing` to Feeds twice per source and inserts
     * that source's articles, and every one of those writes invalidates this
     * query — which joins Feeds. Syncing fifty imported sources therefore
     * re-ran the whole query, its relation join and the processing below well
     * over a hundred times, each time handing Compose a fresh list to diff.
     *
     * The first emission after a category change is passed straight through, so
     * tapping a chip stays immediate; only the invalidations that follow are
     * held back and coalesced.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val categoryArticles: Flow<List<FeedItem>> =
        // Categories narrow the feed (include); the filter sheet's tagsFilter
        // mutes (exclude) and is applied in processArticles. These were the same
        // preference read both ways, so any selection cancelled itself out.
        prefs.categoryFilter.get()
            .distinctUntilChanged()
            .flatMapLatest { categories ->
                val source =
                    if (categories.any()) articleRepo.getFeedItemsByTags(categories)
                    else articleRepo.getEnabledFeedItems()
                var isFirst = true
                source.debounce {
                    if (isFirst) {
                        isFirst = false
                        0L
                    } else {
                        INVALIDATION_DEBOUNCE_MS
                    }
                }
            }
            .conflate()

    /**
     * The expensive part — deduplication, muting and sorting — kept off the flow
     * that carries sync status. `isSyncing` toggles independently of the
     * content, and combining the two meant every toggle re-sorted the whole
     * list for a result that was identical to the one before it.
     */
    private val processedArticles: Flow<List<FeedItem>> = combine(
        categoryArticles,
        sortFilterState,
        prefs.removeDuplicates.get(),
    ) { articles, sfm, removeDuplicate ->
        processArticles(articles, sfm, removeDuplicate)
    }.flowOn(Dispatchers.Default)

    val articleListState: StateFlow<ArticleListState> = combine(
        processedArticles,
        sortFilterState,
        feedsRepo.isSyncing
    ) { articles, sfm, isSyncing ->
        ArticleListState(
            articles = articles,
            isFilterModified = sfm != SortFilterModel(),
            isSyncing = isSyncing
        )
    }.stateIn(
        ioScope,
        SharingStarted.Eagerly,
        ArticleListState()
    )

    @OptIn(FlowPreview::class)
    private val processedBookmarks: Flow<List<FeedItem>> = combine(
        articleRepo.getBookmarkedFeedItems().debounce(INVALIDATION_DEBOUNCE_MS).conflate(),
        sortFilterState,
        prefs.removeDuplicates.get(),
    ) { articles, sfm, removeDuplicate ->
        processArticles(articles, sfm, removeDuplicate)
    }.flowOn(Dispatchers.Default)

    val bookmarksState: StateFlow<BookmarksState> = combine(
        processedBookmarks,
        feedsRepo.isSyncing
    ) { articles, isSyncing ->
        BookmarksState(
            bookmarkedArticles = articles,
            isSyncing = isSyncing
        )
    }.stateIn(
        ioScope,
        SharingStarted.Eagerly,
        BookmarksState()
    )

    fun unpinArticle(id: String) {
        viewModelScope.launch {
            articleRepo.unpinArticle(id)
        }
    }

    /** Records that an article was opened, for the unread and read-today counts. */
    fun markRead(id: String) {
        viewModelScope.launch {
            articleRepo.markRead(id)
        }
    }

    fun bookmarkArticle(id: String, boolean: Boolean) {
        viewModelScope.launch {
            articleRepo.bookmarkArticle(id, boolean)
        }
    }

    // HELPERS
    private fun processArticles(
        articles: List<FeedItem>,
        sfm: SortFilterModel,
        removeDuplicate: Boolean
    ): List<FeedItem> {
        return articles
            .let { if (removeDuplicate) it.distinctBy { item -> item.link } else it }
            .let { list ->
                if (sfm.sourcesFilter.isEmpty()) list
                else list.filterNot { it.sourceId in sfm.sourcesFilter }
            }
            .let { list ->
                if (sfm.tagsFilter.isEmpty()) list
                else list.filterNot { it.feedTag in sfm.tagsFilter }
            }
            .let { list ->
                when (sfm.sort) {
                    SORT_CHRONOLOGICAL if !sfm.sortAsc ->
                        list.sortedByDescending(FeedItem::timeMillis)

                    SORT_CHRONOLOGICAL if sfm.sortAsc  ->
                        list.sortedBy(FeedItem::timeMillis)

                    SORT_TITLE if sfm.sortAsc          ->
                        list.sortedBy(FeedItem::contentTitle)

                    SORT_TITLE if !sfm.sortAsc         ->
                        list.sortedByDescending(FeedItem::contentTitle)

                    SORT_SOURCE if sfm.sortAsc         ->
                        list.sortedBy(FeedItem::displayTitle)

                    SORT_SOURCE if !sfm.sortAsc        ->
                        list.sortedByDescending(FeedItem::displayTitle)

                    else                               -> list.sortedByDescending(
                        FeedItem::timeMillis
                    )
                }
            }
    }
}

/**
 * How long to wait for a burst of database invalidations to settle before
 * rebuilding the list. Long enough to swallow a whole feed's inserts, short
 * enough that finished sources appear while a sync is still running.
 */
private const val INVALIDATION_DEBOUNCE_MS = 300L

data class ArticleListState(
    val articles: List<FeedItem> = emptyList(),
    val isFilterModified: Boolean = false,
    val isSyncing: Boolean = false,
)

data class BookmarksState(
    val bookmarkedArticles: List<FeedItem> = emptyList(),
    val isSyncing: Boolean = false,
)