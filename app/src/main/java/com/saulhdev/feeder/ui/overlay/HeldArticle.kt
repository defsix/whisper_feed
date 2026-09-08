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
package com.saulhdev.feeder.ui.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

/**
 * The one article allowed to hold the top of the feed.
 *
 * Pinning, breaking news and the weighting all want to put something at the
 * top, and three mechanisms each promoting their own candidate would fight.
 * There is one rule instead, and it is deliberately dull: **whatever the
 * ordering already put first, if it is being held on purpose.** Pinning sorts
 * an article first; the breaking-news term weights a cluster lead so heavily
 * it lands first. Both arrive at the top through the machinery that was
 * already there, and this only asks whether the article now sitting at the top
 * got there for a reason worth holding on to.
 *
 * That keeps one thing on screen at a time. A pinned article and a breaking
 * story cannot both be held, because only one of them can be first — and the
 * reader's own pin wins, since it sorts above everything.
 */
object Held {

    /**
     * How many articles must pass before the held one lets go.
     *
     * "Sticky" must not mean "permanent". The point is that a story does not
     * vanish the moment the reader starts scrolling, not that they have to
     * live with it for the rest of the feed.
     */
    const val RELEASE_AFTER = 5
}

/**
 * The article to hold at the top, or null.
 *
 * @param clusters the current story clusters, so a cluster lead counts as held
 *   in the same way a pin does.
 */
@Composable
fun rememberHeldArticle(
    articles: List<FeedItem>,
    clusters: Map<String, StoryCluster>,
): FeedItem? {
    val prefs: FeedPreferences = koinInject()
    val enabled by prefs.stickyTop.get().collectAsState(initial = prefs.stickyTop.getValue())
    return remember(articles, clusters, enabled) {
        if (!enabled) return@remember null
        val first = articles.firstOrNull() ?: return@remember null
        val isLead = clusters[first.id]?.leadId == first.id
        if (first.pinned || isLead) first else null
    }
}

/**
 * Lays out the feed with the held article stuck to the top, then let go.
 *
 * `stickyHeader` on its own gives the wrong behaviour here. A header stays
 * pinned for as long as its section is on screen, and with one header and
 * every article inside it that means for ever — "sticky" would become
 * "permanent", and the reader would be unable to get rid of a story by
 * scrolling, which is the one thing they will try.
 *
 * So there are two sections. The held article heads the first, which holds
 * only the next few articles; a second, empty header then takes the sticky
 * slot and pushes the first one off the top. The empty header has no height
 * and nothing in it, so what the reader sees is the held card sliding away
 * after a few articles have gone by, which is exactly the described
 * behaviour with no animation to write.
 *
 * When nothing is held this is an ordinary list, with no extra items in it.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.heldFeed(
    articles: List<FeedItem>,
    held: FeedItem?,
    article: @Composable (index: Int, item: FeedItem) -> Unit,
) {
    if (held == null || articles.firstOrNull()?.id != held.id) {
        itemsIndexed(articles, key = { _, item -> item.id }) { index, item ->
            article(index, item)
        }
        return
    }

    stickyHeader(key = "held-${held.id}") { article(0, held) }

    val rest = articles.drop(1)
    val holding = rest.take(Held.RELEASE_AFTER)
    itemsIndexed(holding, key = { _, item -> item.id }) { index, item ->
        article(index + 1, item)
    }

    // The release. Taking the sticky slot is the whole job, so it has nothing
    // in it and takes no room.
    stickyHeader(key = "held-release") { Spacer(Modifier.height(0.dp)) }

    val remainder = rest.drop(Held.RELEASE_AFTER)
    itemsIndexed(remainder, key = { _, item -> item.id }) { index, item ->
        article(index + 1 + holding.size, item)
    }
}
