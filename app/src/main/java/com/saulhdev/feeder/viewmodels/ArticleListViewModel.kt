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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class ArticleListViewModel(
    private val articleRepo: ArticleRepository,
    feedsRepo: SourcesRepository,
    val prefs: FeedPreferences,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    /**
     * What the user is searching for, empty when they are not.
     *
     * Local to what has already been downloaded: every article in the feed is
     * already in the database, so this needs no network, no account and no
     * index — and it works on a train.
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

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
        //
        // A search deliberately ignores the selected category. "I know I read
        // something about X" does not come with a memory of which category it
        // was filed under, and a search that silently only covered the chip you
        // happen to have selected would look like the article was gone.
        combine(
            prefs.categoryFilter.get(),
            _searchQuery.map { it.isNotBlank() },
        ) { categories, searching -> if (searching) emptySet() else categories }
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
        prefs.hiddenSources.get(),
        // Debounced so a fast typist does not re-filter and re-sort the whole
        // feed on every keystroke; the list is rebuilt once they pause.
        _searchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
    ) { articles, sfm, removeDuplicate, hidden, query ->
        processArticles(articles, sfm, removeDuplicate, hidden, query)
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

    /**
     * "More like this" / "Less like this", as a running score per source.
     *
     * Nothing ranks on this yet; see FeedPreferences.sourceAffinity for why it
     * is recorded from the day the menu appears rather than when the ranking
     * that reads it arrives.
     */
    fun recordAffinity(sourceId: String, delta: Int) {
        ioScope.launch {
            val scores = prefs.sourceAffinity.get().first().mapNotNull { entry ->
                val at = entry.lastIndexOf(':')
                if (at <= 0) null
                else entry.substring(0, at) to (entry.substring(at + 1).toIntOrNull() ?: 0)
            }.toMap()
            val updated = scores + (sourceId to ((scores[sourceId] ?: 0) + delta))
            prefs.sourceAffinity.setValue(updated.map { "${it.key}:${it.value}" }.toSet())
        }
    }

    /**
     * The source hidden most recently, for as long as it can still be undone.
     *
     * Hiding is one tap from a menu reached mid-scroll, with no confirmation
     * step by design — so the way back has to be offered straight away, and
     * the feed the article vanished from is where to offer it.
     */
    private val _recentlyHidden = MutableStateFlow<FeedItem?>(null)
    val recentlyHidden: StateFlow<FeedItem?> = _recentlyHidden.asStateFlow()

    fun hideSource(item: FeedItem) {
        ioScope.launch {
            prefs.hiddenSources.setValue(prefs.hiddenSources.get().first() + item.sourceId)
            _recentlyHidden.value = item
        }
    }

    fun undoHideSource() {
        val item = _recentlyHidden.value ?: return
        _recentlyHidden.value = null
        unhideSource(item.sourceId)
    }

    fun forgetHiddenSource() {
        _recentlyHidden.value = null
    }

    fun unhideSource(sourceId: String) {
        ioScope.launch {
            prefs.hiddenSources.setValue(prefs.hiddenSources.get().first() - sourceId)
        }
    }

    // HELPERS


    private fun processArticles(
        articles: List<FeedItem>,
        sfm: SortFilterModel,
        removeDuplicate: Boolean,
        hiddenSources: Set<String> = emptySet(),
        query: String = "",
    ): List<FeedItem> {
        val terms = query.trim().takeIf(String::isNotEmpty)
        // One pass rather than four. Each `let` here used to allocate a whole
        // new list, so a feed of a few thousand articles built three throwaway
        // copies before the sort even started — on every emission.
        val seenLinks = if (removeDuplicate) HashSet<String>() else null
        val filtered = articles.asSequence()
            .filter { item ->
                when {
                    terms != null && !item.matchesSearch(terms) -> false
                    seenLinks != null && !seenLinks.add(item.link) -> false
                    // "Hide source" is permanent and survives a filter reset;
                    // sourcesFilter below is the filter sheet's scratchpad.
                    item.sourceId in hiddenSources -> false
                    item.sourceId in sfm.sourcesFilter -> false
                    // Any of the source's categories being muted hides it. This
                    // compared the whole comma-separated tag string against the
                    // muted set, so muting a category never hid a feed that had
                    // more than one.
                    sfm.tagsFilter.isNotEmpty() &&
                            item.feedTags.any { it in sfm.tagsFilter } -> false

                    else -> true
                }
            }
            .toList()

        val comparator = when (sfm.sort) {
            SORT_TITLE  -> compareBy(FeedItem::contentTitle)
            SORT_SOURCE -> compareBy(FeedItem::displayTitle)
            else        -> compareBy(FeedItem::timeMillis)
        }
        return if (sfm.sortAsc) filtered.sortedWith(comparator)
        else filtered.sortedWith(comparator.reversed())
    }
}

/**
 * How long to wait for a burst of database invalidations to settle before
 * rebuilding the list. Long enough to swallow a whole feed's inserts, short
 * enough that finished sources appear while a sync is still running.
 */
/**
 * Whether an article answers a search.
 *
 * Headline, source and summary, in that order of likelihood. The full article
 * body is deliberately not searched: it lives in a file per article rather
 * than in the database, so covering it would mean reading every one of them
 * off disk on every keystroke, or building an index — neither of which is
 * worth it before the shorter fields prove too thin.
 */
internal fun FeedItem.matchesSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return contentTitle.contains(q, ignoreCase = true) ||
            feedTitle.contains(q, ignoreCase = true) ||
            article.description.contains(q, ignoreCase = true)
}

private const val INVALIDATION_DEBOUNCE_MS = 300L

/** How long to let typing settle before rebuilding the list for a search. */
private const val SEARCH_DEBOUNCE_MS = 250L

data class ArticleListState(
    val articles: List<FeedItem> = emptyList(),
    val isFilterModified: Boolean = false,
    val isSyncing: Boolean = false,
)

data class BookmarksState(
    val bookmarkedArticles: List<FeedItem> = emptyList(),
    val isSyncing: Boolean = false,
)