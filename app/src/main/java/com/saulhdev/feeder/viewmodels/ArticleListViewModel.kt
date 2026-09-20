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
import com.saulhdev.feeder.data.repository.FEED_WINDOW
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.ui.overlay.ArticleWeight.MAX_CONSECUTIVE_FROM_SOURCE
import com.saulhdev.feeder.utils.READ_HIDE
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
    private val feedsRepo: SourcesRepository,
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
        ) { categories, searching ->
            // The searching flag travels with the categories rather than being
            // read again inside flatMapLatest. distinctUntilChanged below
            // compares whatever comes through here, and with only the
            // categories in it, starting a search while no chip was selected
            // would produce the same empty set twice — so the query would not
            // restart and the wider limit would never take effect.
            (if (searching) emptySet() else categories) to searching
        }
            .distinctUntilChanged()
            .flatMapLatest { (categories, searching) ->
                // A search covers everything that has been downloaded, which is
                // what the feature promises; capping it to the window would
                // make that quietly untrue for older articles.
                val limit = if (searching) Int.MAX_VALUE else FEED_WINDOW
                val source =
                    if (categories.any()) articleRepo.getFeedItemsByTags(categories, limit)
                    else articleRepo.getEnabledFeedItems(limit)
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
        // Debounced so a fast typist does not re-filter and re-sort the whole
        // feed on every keystroke; the list is rebuilt once they pause.
        _searchQuery.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
        prefs.readVisibility.get(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        processArticles(
            articles = values[0] as List<FeedItem>,
            sfm = values[1] as SortFilterModel,
            removeDuplicate = values[2] as Boolean,
            query = values[4] as String,
            hideRead = values[5] as String == READ_HIDE,
        )
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

    /** Holds an article at the top of the feed, or lets it go. */
    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            articleRepo.setPinned(id, pinned)
        }
    }

    /**
     * Articles marked read without the reader pressing anything, and still
     * recallable.
     *
     * Opening an article is its own undo — the article is right there. A batch
     * is not: forty marked while scrolling, or a whole feed marked at once, is
     * gone before there is anything to look at. So those two accumulate here
     * and the feed offers them back.
     */
    private val _undoableReads = MutableStateFlow<List<String>>(emptyList())
    val undoableReads: StateFlow<List<String>> = _undoableReads.asStateFlow()

    /** Records that an article was opened, for the unread and read-today counts. */
    fun markRead(id: String) {
        viewModelScope.launch {
            articleRepo.markRead(id)
        }
    }

    /**
     * Records an article being opened, which also marks it read.
     *
     * The tap handlers used to call [markRead], which recorded the same thing
     * for an article somebody chose to open as for forty that scrolled past
     * — and then the ordering learned from the sum of the two as though they
     * meant the same. They do not, and this is the one that is certain.
     */
    fun markOpened(id: String) {
        viewModelScope.launch {
            articleRepo.markOpened(id)
        }
    }

    /** Adds to an article's time on screen. */
    /**
     * Records time spent reading an article, in milliseconds.
     *
     * Used by the browser-trip timer rather than the in-app clock, which
     * reports through ArticleViewModel on the article's own screen.
     */
    fun addReading(id: String, millis: Long) {
        ioScope.launch { articleRepo.addReading(id, millis) }
    }

    fun addDwell(id: String, millis: Long) {
        viewModelScope.launch {
            articleRepo.addDwell(id, millis)
        }
    }

    /**
     * Records that an article was read by being looked at rather than opened.
     *
     * Kept apart from [markRead] because only this one is worth offering back:
     * a reader who opened an article knows they did.
     */
    fun markReadOnScroll(id: String) {
        viewModelScope.launch {
            articleRepo.markRead(id)
            _undoableReads.value = _undoableReads.value + id
        }
    }

    /** Marks everything currently unread, offering the whole batch back. */
    fun markAllRead() {
        viewModelScope.launch {
            val marked = articleRepo.markAllRead()
            if (marked.isNotEmpty()) _undoableReads.value = _undoableReads.value + marked
        }
    }

    /** Puts back every article marked since the offer was last dismissed. */
    fun undoReads() {
        viewModelScope.launch {
            val ids = _undoableReads.value
            _undoableReads.value = emptyList()
            if (ids.isNotEmpty()) articleRepo.unmarkRead(ids)
        }
    }

    /** The reader let the offer go. What was marked stays marked. */
    fun forgetUndoableReads() {
        _undoableReads.value = emptyList()
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

    /**
     * Hides a source, which now also stops it fetching.
     *
     * These were two states: a feed switched off, and a feed in a hidden set.
     * Both kept the source's articles out of the feed, and the only thing
     * separating them was whether it carried on syncing in the background —
     * a distinction nobody asked for, expressed as two controls that looked
     * identical and lived on different screens.
     *
     * Hiding something should stop it costing data and battery. So there is
     * one state now, and it is the feed's own `isEnabled` column: hiding
     * turns a source off, the switch on the sources list does the same thing,
     * and either can be undone from either place.
     *
     * Safe to collapse only because saved articles no longer depend on it —
     * until the previous commit, switching a source off took everything the
     * reader had bookmarked from it out of Bookmarks, and merging the two
     * would have made hiding do that too.
     */
    fun hideSource(item: FeedItem) {
        ioScope.launch {
            feedsRepo.setEnabled(listOf(item.feed.id), enabled = false)
            _recentlyHidden.value = item
        }
    }

    fun undoHideSource() {
        val item = _recentlyHidden.value ?: return
        _recentlyHidden.value = null
        ioScope.launch { feedsRepo.setEnabled(listOf(item.feed.id), enabled = true) }
    }

    fun forgetHiddenSource() {
        _recentlyHidden.value = null
    }

    fun unhideSource(feedId: Long) {
        ioScope.launch { feedsRepo.setEnabled(listOf(feedId), enabled = true) }
    }

    // HELPERS


    private fun processArticles(
        articles: List<FeedItem>,
        sfm: SortFilterModel,
        removeDuplicate: Boolean,
        query: String = "",
        hideRead: Boolean = false,
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
                    item.sourceId in sfm.sourcesFilter -> false
                    // Any of the source's categories being muted hides it. This
                    // compared the whole comma-separated tag string against the
                    // muted set, so muting a category never hid a feed that had
                    // more than one.
                    sfm.tagsFilter.isNotEmpty() &&
                            item.feedTags.any { it in sfm.tagsFilter } -> false

                    // Saved and pinned survive: those are the two ways a
                    // reader has said "keep this", and a setting about
                    // tidying away what is finished must not throw away
                    // what was deliberately kept.
                    hideRead && item.article.readAt != 0L &&
                            !item.bookmarked && !item.pinned -> false

                    else -> true
                }
            }
            .toList()

        val comparator = when (sfm.sort) {
            SORT_TITLE  -> compareBy(FeedItem::contentTitle)
            SORT_SOURCE -> compareBy(FeedItem::displayTitle)
            else        -> compareBy(FeedItem::timeMillis)
        }
        val ordered = if (sfm.sortAsc) filtered.sortedWith(comparator)
        else filtered.sortedWith(comparator.reversed())

        // Pinned articles sit above everything, whichever sort is active. A
        // pin means "I am following this, keep it in front of me", and a sort
        // that moved it away would be answering a question the reader did not
        // ask. sortedBy is stable, so the order inside each group is the one
        // the sort just produced.
        val sorted = ordered.sortedByDescending { it.pinned }

        // Sorting by source is a request to see one source's articles
        // together, so spreading them would be undoing what was asked.
        return if (sfm.sort == SORT_SOURCE) sorted else spreadSources(sorted)
    }

    /**
     * Breaks up runs from a single source, without losing any of them.
     *
     * A feed sorted by time gives a source that posts six times in an hour six
     * consecutive slots, and a chronological feed of a busy wire service reads
     * as that wire service's feed. The overflow is displaced further down
     * rather than dropped: those are still six articles the reader subscribed
     * to, they just stop being the whole of the screen.
     *
     * One pass, and stable — the order within a source never changes, so an
     * article can only ever move later, never earlier and never past one of
     * its own.
     */
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

/**
 * Keeps one source from filling the screen, without dropping anything.
 *
 * Top level and internal rather than a private method, so the guarantee that
 * a pin survives it can be tested directly. It is a pure function over a list
 * and never needed the view model's state.
 */
internal fun spreadSources(articles: List<FeedItem>): List<FeedItem> {
    if (articles.size < MAX_CONSECUTIVE_FROM_SOURCE + 1) return articles

    val result = ArrayList<FeedItem>(articles.size)
    val deferred = ArrayDeque<FeedItem>()
    var lastSource: String? = null
    var run = 0

    fun take(item: FeedItem) {
        if (item.sourceId == lastSource) run++ else { lastSource = item.sourceId; run = 1 }
        result += item
    }

    articles.forEach { item ->
        // A pin is never deferred. This rule exists to stop one source
        // filling the screen, and it has no business overruling an
        // article the reader put at the top on purpose — pin four things
        // from one source and the last of them was being posted to the
        // bottom of the feed, which looks exactly like the pin not
        // working.
        if (item.pinned) {
            take(item)
            return@forEach
        }
        // Anything held back that would break the current run goes first:
        // the whole point is to fill the gap rather than to leave one.
        val released = deferred.firstOrNull { it.sourceId != lastSource || run < MAX_CONSECUTIVE_FROM_SOURCE }
        if (released != null && item.sourceId == lastSource && run >= MAX_CONSECUTIVE_FROM_SOURCE) {
            deferred.remove(released)
            take(released)
        }
        if (item.sourceId == lastSource && run >= MAX_CONSECUTIVE_FROM_SOURCE) deferred += item
        else take(item)
    }
    // Whatever is still held back goes on the end, in the order it arrived.
    deferred.forEach(result::add)
    return result
}
