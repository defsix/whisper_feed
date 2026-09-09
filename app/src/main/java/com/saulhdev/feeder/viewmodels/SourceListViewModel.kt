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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class SourceListViewModel(
    private val feedsRepo: SourcesRepository,
    private val articleRepo: ArticleRepository,
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
        _query,
        combine(sort, ascending) { sort, ascending -> sort to ascending },
        articleRepo.latestArticlePerFeed(),
    ) { allSources, allTags, query, order, latestPosts ->
        val (sort, ascending) = order
        val base = sort.comparator(latestPosts)
        val comparator = if (ascending) base else base.reversed()
        val matching = allSources.filter { it.matches(query) }.sortedWith(comparator)
        val (enabledSources, disabledSources) = matching.partition { it.isEnabled }
        SourceListState(
            allSources = allSources,
            enabledSources = enabledSources,
            disabledSources = disabledSources,
            tagsSourcesMap = groupByTag(allSources, allTags),
            allTags = allTags,
        )
    }
        // A sync writes to every feed row twice — once to mark it syncing and
        // once to record when it finished — and each of those invalidates the
        // query behind this. Without conflation the list is rebuilt, sorted
        // and regrouped for every one of those writes, which is why adding a
        // source felt slow: the add triggers a sync, and the sync then talks
        // over the update the reader was waiting for.
        .conflate()
        .flowOn(Dispatchers.Default)
        .stateIn(
            ioScope,
            SharingStarted.Eagerly,
            SourceListState()
        )

    /**
     * The saved articles, fetched when something actually needs them.
     *
     * This used to be a fifth flow in the combine above, which meant a join
     * across Article and Feeds re-ran on every article a sync inserted — to
     * keep a list ready for a button pressed once in a blue moon, if ever.
     */
    suspend fun bookmarksForExport(): List<FeedItem> =
        articleRepo.getBookmarkedFeedItems().first()

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
    val allTags: List<String> = emptyList(),
)

/**
 * How the source list is ordered.
 *
 * Each comparator here is the *ascending* one; the view model reverses it when
 * the reader taps the order they are already on.
 *
 * The comparator is a function rather than a value because one of these needs
 * something that is not on a Feed: when its newest article arrived. That lives
 * in the Article table, so it is handed in.
 */
enum class SourceSort(@StringRes val labelId: Int) {
    Title(R.string.sort_by_title) {
        override fun comparator(latestPosts: Map<Long, Long>) =
            compareBy(String.CASE_INSENSITIVE_ORDER, Feed::title)
    },

    Category(R.string.sort_by_category) {
        override fun comparator(latestPosts: Map<Long, Long>) =
            compareBy(String.CASE_INSENSITIVE_ORDER) { feed: Feed ->
                feed.tags.firstOrNull().orEmpty()
            }
    },

    /**
     * Oldest first ascending, newest first descending.
     *
     * There is no "added" column and none is needed: `Feeds.id` is
     * autoGenerate, so id order is insertion order.
     *
     * Two honest caveats. An OPML import arrives in file order, so a hundred
     * sources imported at once are "added" in whatever order the file listed
     * them rather than all at the same moment. And undoing a removal
     * re-inserts under a fresh id, so a restored source counts as newly added.
     */
    Added(R.string.sort_by_added) {
        override fun comparator(latestPosts: Map<Long, Long>) = compareBy(Feed::id)
    },

    /**
     * When the feed itself last published, which is the question people
     * actually mean by "least recently updated".
     *
     * This replaces a sort on `Feeds.lastSync`, and the difference matters:
     * lastSync is when *Whisper* last fetched the feed, so a feed checked
     * faithfully every hour that has published nothing since March had the
     * most recent lastSync in the list. It answered "which feeds am I failing
     * to reach" when what was wanted was "which feeds have gone quiet".
     *
     * Ascending puts the quietest first, which is the useful end.
     *
     * A feed with no articles at all sorts as zero — before everything —
     * which is right: it is the quietest of the lot, and usually broken.
     */
    LatestPost(R.string.sort_by_latest_post) {
        override fun comparator(latestPosts: Map<Long, Long>) =
            compareBy { feed: Feed -> latestPosts[feed.id] ?: 0L }
    },
    ;

    abstract fun comparator(latestPosts: Map<Long, Long>): Comparator<Feed>

    companion object {
        /**
         * The order stored under this name, or the default if there isn't one.
         *
         * Stored by name rather than by ordinal, and resolved rather than
         * indexed, because a preference outlives the code that wrote it. An
         * order removed in a later version leaves that name sitting in
         * somebody's DataStore, and an ordinal would quietly resolve it to
         * whichever order happens to sit at that position now. A name that no
         * longer exists resolves to nothing, and nothing means Title.
         */
        fun byName(name: String?): SourceSort =
            entries.firstOrNull { it.name == name } ?: Title
    }
}

/**
 * Every tag mapped to the sources carrying it, plus "" for the untagged.
 *
 * One pass over the sources rather than one pass per tag. `Feed.tags`
 * splits a comma-separated string every time it is read, so the old shape
 * — filtering the whole list once for each tag — split every source's tags
 * once per tag that exists. Ten categories and a hundred sources meant a
 * thousand string splits, on every one of those sync writes.
 *
 * OPML export reads this map, and a source in no bucket is a source that
 * does not get exported.
 */
internal fun groupByTag(sources: List<Feed>, tags: List<String>): Map<String, List<Feed>> {
    val byTag = tags.associateWithTo(HashMap()) { mutableListOf<Feed>() }
    val untagged = mutableListOf<Feed>()
    sources.forEach { source ->
        // Matched against the split list rather than with `tag.contains`,
        // which made "New" match a source tagged "News" — and made the ""
        // bucket match every source, so an export wrote each feed twice.
        val own = source.tags
        if (own.isEmpty()) untagged += source
        else own.forEach { byTag[it]?.add(source) }
    }
    return byTag + ("" to untagged)
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