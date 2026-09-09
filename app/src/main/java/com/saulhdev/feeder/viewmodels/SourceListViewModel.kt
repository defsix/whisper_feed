package com.saulhdev.feeder.viewmodels

import androidx.annotation.StringRes
import com.saulhdev.feeder.R
import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class SourceListViewModel(
    private val feedsRepo: SourcesRepository,
    articleRepo: ArticleRepository,
    private val prefs: FeedPreferences,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    /** What the user typed into the search field, if anything. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * How the list is ordered, read straight from the stored preference.
     *
     * Derived rather than held: a MutableStateFlow seeded from DataStore would
     * be a second copy of the same fact, and the two would drift the first
     * time anything else wrote to the preference.
     *
     * An unrecognised name falls back to Title. The stored value is the enum's
     * name rather than its ordinal precisely so that removing or reordering an
     * option cannot silently change what somebody's phone is sorted by.
     */
    val sort: StateFlow<SourceSort> = prefs.sourcesSort.get()
        .map(SourceSort::byName)
        .stateIn(ioScope, SharingStarted.Eagerly, SourceSort.Title)

    /** Which way round that order runs. */
    val ascending: StateFlow<Boolean> = prefs.sourcesSortAsc.get()
        .stateIn(ioScope, SharingStarted.Eagerly, true)

    /**
     * Ids picked out for a bulk action.
     *
     * Held here rather than in the screen so that a rotation, or the list being
     * rebuilt by a sync finishing mid-selection, does not silently drop what
     * the user had chosen.
     */
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    val state = combine(
        feedsRepo.getAllSourcesFlow(),
        feedsRepo.getAllTagsFlow(),
        // TODO move the getter eventually to SourcesRepository
        articleRepo.getBookmarkedFeedItems(),
        _query,
        combine(sort, ascending) { sort, ascending -> sort to ascending },
    ) { allSources, allTags, bookmarked, query, order ->
        val (sort, ascending) = order
        val comparator =
            if (ascending) sort.comparator else sort.comparator.reversed()
        val matching = allSources.filter { it.matches(query) }.sortedWith(comparator)
        val (enabledSources, disabledSources) = matching.partition { it.isEnabled }
        SourceListState(
            allSources = allSources,
            enabledSources = enabledSources,
            disabledSources = disabledSources,
            // Every tag mapped to the sources carrying it, plus "" for the
            // untagged ones — OPML export reads this map and a source in no
            // bucket is a source that does not get exported.
            //
            // Matched against the split tag list rather than with
            // `tag.contains`, which made "New" match a source tagged "News",
            // and made the "" bucket match *every* source: an export therefore
            // wrote each feed twice, once under its own category and once
            // under none.
            tagsSourcesMap = allTags.associateWith { tag ->
                allSources.filter { tag in it.tags }
            } + ("" to allSources.filter { it.tags.isEmpty() }),
            bookmarked = bookmarked,
            allTags = allTags,
        )
    }.stateIn(
        ioScope,
        SharingStarted.Eagerly,
        SourceListState()
    )

    fun setQuery(value: String) {
        _query.value = value
    }

    /**
     * Picks an order, or reverses the one already picked.
     *
     * Choosing the row that is already chosen is not a no-op — it is the only
     * way to ask for the other direction, and it is what tapping a sorted
     * column header does everywhere else. Choosing a different one always
     * starts ascending rather than inheriting the last direction, so the same
     * tap always produces the same result.
     */
    fun setSort(value: SourceSort) {
        // On the IO scope because setValue is a blocking DataStore write, and
        // this is called straight from a tap.
        ioScope.launch {
            if (sort.value == value) prefs.sourcesSortAsc.setValue(!ascending.value)
            else {
                prefs.sourcesSort.setValue(value.name)
                prefs.sourcesSortAsc.setValue(true)
            }
        }
    }

    /* Selection */

    fun toggleSelected(id: Long) {
        _selection.value = if (id in _selection.value) _selection.value - id
        else _selection.value + id
    }

    fun selectAll(ids: Collection<Long>) {
        _selection.value = _selection.value + ids
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /* Bulk actions. Each one clears the selection: leaving twelve sources
       highlighted after acting on them invites acting on them twice. */

    fun setSelectedEnabled(enabled: Boolean) {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { feedsRepo.setEnabled(ids, enabled) }
    }

    fun editSelectedTags(
        add: Set<String> = emptySet(),
        remove: Set<String> = emptySet(),
        replaceWith: Set<String>? = null,
    ) {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { feedsRepo.editTags(ids, add, remove, replaceWith) }
    }

    fun deleteSelected() {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { feedsRepo.deleteSources(ids) }
    }

    /* Category management */

    fun renameTag(from: String, to: String) {
        viewModelScope.launch { feedsRepo.renameTag(from, to) }
    }

    fun deleteTag(tag: String) {
        viewModelScope.launch { feedsRepo.deleteTag(tag) }
    }

    val recentlyDeletedMany = feedsRepo.recentlyDeletedMany

    fun undoDeleteSources() = feedsRepo.undoDeleteSources()

    fun forgetDeletedSources() = feedsRepo.forgetDeletedSources()

    fun insertFeed(feed: Feed) {
        viewModelScope.launch {
            feedsRepo.insertSource(feed)
        }
    }

    fun updateFeed(source: Feed, enable: Boolean) {
        viewModelScope.launch {
            feedsRepo.updateSource(source, enable)
        }
    }

    /**
     * Subscribes to one search result.
     *
     * Deliberately one, not a list: this used to take every result the search
     * produced and add all of them when the screen closed.
     */
    fun addFeed(result: SearchResult) {
        if (result.isError) return
        viewModelScope.launch {
            val url = sloppyLinkToStrictURL(result.url)
            // Re-checked here rather than trusted from the UI: results can be
            // minutes old, and Feeds.url is uniquely indexed with an insert
            // strategy of REPLACE — an unnoticed duplicate would delete the
            // original row and cascade to its articles.
            if (feedsRepo.findSourceByUrl(url) != null) return@launch
            feedsRepo.insertSource(
                // feedImage is the source's *mark*, not its address. Writing
                // the feed URL here made every new subscription claim an icon
                // it does not have, which the card would then try to load.
                // Sync fills it in properly on the first fetch.
                Feed(
                    title = result.title,
                    description = result.description,
                    url = url,
                )
            )
        }
    }

    /**
     * Files a source under a category, found by its address.
     *
     * By url rather than by id because the screen that needs this has just
     * added the feed and never saw the id — the insert happens in a coroutine
     * and returns nothing. The url is what the reader was looking at, and it
     * is unique in the table.
     */
    fun setCategory(url: String, tag: String) {
        viewModelScope.launch {
            val feed = feedsRepo.findSourceByUrl(sloppyLinkToStrictURL(url)) ?: return@launch
            feedsRepo.updateSource(feed.copy(tag = tag))
        }
    }

    val recentlyDeleted = feedsRepo.recentlyDeleted

    fun undoDelete() = feedsRepo.undoDeleteSource()

    fun forgetDeleted() = feedsRepo.forgetDeletedSource()
}

data class SourceListState(
    val allSources: List<Feed> = emptyList(),
    val enabledSources: List<Feed> = emptyList(),
    val disabledSources: List<Feed> = emptyList(),
    val tagsSourcesMap: Map<String, List<Feed>> = emptyMap(),
    val bookmarked: List<FeedItem> = emptyList(),
    val allTags: List<String> = emptyList(),
)

/**
 * How the source list is ordered.
 *
 * Three fields, each sorted either way, rather than four entries where two
 * were the same field in two directions under different names — "Least
 * recently updated" and "Recently added" both named a direction in their
 * label, which left no way to ask for the other one and made the menu look
 * like it offered four orders when it offered two and a half.
 *
 * Each comparator here is the *ascending* one. The view model reverses it.
 */
enum class SourceSort(@StringRes val labelId: Int, val comparator: Comparator<Feed>) {
    Title(R.string.sort_by_title, compareBy(String.CASE_INSENSITIVE_ORDER, Feed::title)),
    Category(
        R.string.sort_by_category,
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.tags.firstOrNull().orEmpty() },
    ),

    /**
     * Oldest first ascending, newest first descending.
     *
     * There is no "added" column and none is needed: `Feeds.id` is
     * autoGenerate, so id order is insertion order.
     *
     * Two honest caveats. An OPML import arrives in file order, so a hundred
     * sources imported at once are "added" in whatever order the file listed
     * them rather than all at the same moment. And undoing a removal
     * re-inserts under a fresh id, so a restored source counts as newly added
     * — the old id is gone and nothing else refers to it.
     */
    Added(R.string.sort_by_added, compareBy(Feed::id)),
    ;

    companion object {
        /**
         * The order stored under this name, or the default if there isn't one.
         *
         * Stored by name rather than by ordinal, and resolved rather than
         * indexed, because a preference outlives the code that wrote it. An
         * order removed in a later version — "LastSync" was, in this one —
         * leaves that name sitting in somebody's DataStore, and an ordinal
         * would quietly resolve it to whichever order happens to sit at that
         * position now. A name that no longer exists resolves to nothing, and
         * nothing means Title.
         */
        fun byName(name: String?): SourceSort =
            entries.firstOrNull { it.name == name } ?: Title
    }
}

/**
 * Whether a source matches what was typed in the search field.
 *
 * Title, address and categories all count: people look for a source by the
 * name they gave it, by the site it comes from, or by what they filed it
 * under, and which of the three they reach for is not predictable.
 */
internal fun Feed.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
            url.toString().contains(q, ignoreCase = true) ||
            tags.any { it.contains(q, ignoreCase = true) }
}