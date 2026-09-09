package com.saulhdev.feeder.viewmodels

import androidx.annotation.StringRes
import com.saulhdev.feeder.R
import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class SourceListViewModel(
    private val feedsRepo: SourcesRepository,
    articleRepo: ArticleRepository,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    /** What the user typed into the search field, if anything. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(SourceSort.Title)
    val sort: StateFlow<SourceSort> = _sort.asStateFlow()

    /**
     * Which way round the chosen order runs.
     *
     * Every sort starts ascending and the same row reverses it, which is the
     * pattern Material uses and the one a table header has used for thirty
     * years. The alternative was a separate list of directions, or the four
     * options this menu used to carry where two of them were one field in two
     * directions with different names.
     */
    private val _ascending = MutableStateFlow(true)
    val ascending: StateFlow<Boolean> = _ascending.asStateFlow()

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
        combine(_sort, _ascending) { sort, ascending -> sort to ascending },
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
        if (_sort.value == value) _ascending.value = !_ascending.value
        else {
            _sort.value = value
            _ascending.value = true
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