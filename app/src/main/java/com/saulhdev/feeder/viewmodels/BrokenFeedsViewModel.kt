/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
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
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.bookmarks.FeedDiscovery
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.isSameFeedUrl
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** What a look for a replacement turned up. */
sealed interface Recovery {
    data object Looking : Recovery

    /** A different address that works — the useful case. */
    data class Found(val feedUrl: String, val title: String, val declared: Boolean) : Recovery

    /** The site still advertises the address already subscribed to. */
    data object Unchanged : Recovery

    /**
     * The address the site advertises belongs to a different subscription.
     *
     * Common rather than exotic: a feed goes quiet because the site moved it,
     * and the address it moved to is one the reader already has under another
     * entry. `Feeds.url` is unique, so this cannot be applied — and until it
     * was reported, attempting it threw out of the tap handler and killed the
     * app. The way out is to delete this one.
     */
    data object AlreadySubscribed : Recovery

    /** Nothing there. The site may be gone, and only the reader can judge that. */
    data object NothingFound : Recovery
}

class BrokenFeedsViewModel(
    private val sources: SourcesRepository,
    private val discovery: FeedDiscovery,
) : NeoViewModel() {

    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    val broken: StateFlow<List<Feed>> =
        sources.getFailingFeeds().stateIn(ioScope, SharingStarted.Eagerly, emptyList())

    private val _recovery = MutableStateFlow<Map<Long, Recovery>>(emptyMap())
    val recovery: StateFlow<Map<Long, Recovery>> = _recovery.asStateFlow()

    /**
     * Asks the site whether its feed has moved.
     *
     * This is the whole feature: a feed address changes — a site moves to a
     * new CMS, drops `/rss` for `/feed`, switches host — and every reader in
     * the world treats that as the feed having died. The site is usually still
     * there, still publishing, still advertising the new address in its own
     * head. Nobody looks, so nobody finds it.
     */
    fun look(feed: Feed) {
        val host = runCatching { feed.url.host }.getOrNull() ?: return
        ioScope.launch {
            set(feed.id, Recovery.Looking)
            val found = discovery.discover(host.removePrefix("www."))
            set(
                feed.id,
                when {
                    found == null -> Recovery.NothingFound
                    // The same address it already has: whatever is wrong is not
                    // that the feed moved, so offering to "fix" it by writing
                    // the identical URL would be theatre.
                    isSameFeedUrl(
                        runCatching { sloppyLinkToStrictURL(found.feedUrl) }.getOrNull()
                            ?: return@launch,
                        feed.url,
                    ) -> Recovery.Unchanged

                    else -> Recovery.Found(found.feedUrl, found.title, found.declared)
                },
            )
        }
    }

    /**
     * Points the existing feed at the new address.
     *
     * Updated in place rather than added and removed, which keeps every
     * article, the read state, the categories and whatever the weighting has
     * learned about this source. A subscription somebody has had for years is
     * not worth losing to a URL change.
     */
    suspend fun useNewAddress(feed: Feed, newUrl: String) {
        val url = runCatching { sloppyLinkToStrictURL(newUrl) }.getOrNull() ?: return
        if (!sources.updateSource(feed.copy(url = url), resync = true)) {
            set(feed.id, Recovery.AlreadySubscribed)
            return
        }
        sources.clearFailures(feed.id)
        set(feed.id, Recovery.Unchanged)
    }

    /** Stops asking about this one without unsubscribing from it. */
    suspend fun forget(feed: Feed) = sources.clearFailures(feed.id)

    /**
     * Unsubscribes, with its articles.
     *
     * The screen offered to find a feed a new home and to stop asking about
     * it, and nothing else — so a feed that is simply gone, or one whose
     * replacement is already subscribed to, could only be dealt with by
     * leaving here and hunting for it in the sources list.
     */
    suspend fun delete(feed: Feed) {
        sources.deleteFeed(feed.id)
    }

    private fun set(id: Long, value: Recovery) {
        _recovery.value = _recovery.value + (id to value)
    }
}
