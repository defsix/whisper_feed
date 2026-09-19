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
import androidx.compose.foundation.layout.Box
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
import com.saulhdev.feeder.data.content.asState

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
     * How many articles must pass before a *breaking* story lets go.
     *
     * "Sticky" must not mean "permanent" for something the app chose by
     * itself. A cluster lead is inferred — nobody asked for it, and there is
     * no control that says "stop showing me this" — so a reader who wants rid
     * of it has only the scroll, and the scroll has to work.
     *
     * A pin is the opposite case and is not governed by this at all. See
     * [heldFeed].
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
    val enabled by prefs.stickyTop.asState()
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
 * **A pin holds for as long as it is pinned. A breaking story lets go.** The
 * two used to be treated identically, releasing after a few articles, on the
 * reasoning that the reader must always be able to scroll a story away. That
 * reasoning is right about one of them and wrong about the other.
 *
 * A cluster lead is the app's own guess. Nobody asked for it, no control turns
 * off that particular one, and so the scroll has to be the way out — otherwise
 * an article the reader never chose sits on their screen and cannot be
 * removed.
 *
 * A pin is a deliberate act with an obvious undo sitting on the card itself.
 * Releasing it after five articles makes it useless for the thing pins are
 * for: following a story as it develops, checking back through the day,
 * wanting it there every time the feed is opened. "Until you unpin it" is what
 * the reader asked for by pinning, and second-guessing that is not caution but
 * a refusal to do as told.
 *
 * Mechanically: `stickyHeader` holds for as long as its section is on screen,
 * so a pin gets one section containing the whole feed and simply stays. A
 * breaking lead gets two — it heads the first, which holds only the next few
 * articles, and then a second, empty header takes the sticky slot and pushes
 * it off the top. That header has no height and nothing in it, so what the
 * reader sees is the card sliding away after a few articles, with no animation
 * to write.
 *
 * When nothing is held this is an ordinary list, with no extra items in it.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.heldFeed(
    articles: List<FeedItem>,
    held: FeedItem?,
    /**
     * Whether cards may move rather than appear.
     *
     * Passed rather than read here because this is a LazyListScope extension
     * and the setting is a composable read — and passed at all because it was
     * being honoured on one of the four feed paths and ignored on the other
     * three. Somebody who has turned animations off has said what they want,
     * and the answer should not depend on which layout they chose or which
     * surface they are looking at.
     */
    animate: Boolean = true,
    article: @Composable (index: Int, item: FeedItem) -> Unit,
) {

    if (held == null || articles.firstOrNull()?.id != held.id) {
        itemsIndexed(articles, key = { _, item -> item.id }) { index, item ->
            // Keyed, so a sync moves cards rather than replacing the list.
            Box(modifier = if (animate) Modifier.animateItem() else Modifier) {
                article(index, item)
            }
        }
        return
    }

    stickyHeader(key = "held-${held.id}") { article(0, held) }

    val rest = articles.drop(1)

    // A pin stays. One section over the whole feed, no release header, so the
    // card is still there at the bottom of the list and on the next open —
    // until the reader unpins it, which is the only thing that should end it.
    if (held.pinned) {
        itemsIndexed(rest, key = { _, item -> item.id }) { index, item ->
            Box(modifier = if (animate) Modifier.animateItem() else Modifier) {
                article(index + 1, item)
            }
        }
        return
    }

    val holding = rest.take(Held.RELEASE_AFTER)
    itemsIndexed(holding, key = { _, item -> item.id }) { index, item ->
        Box(modifier = if (animate) Modifier.animateItem() else Modifier) {
            article(index + 1, item)
        }
    }

    // The release. Taking the sticky slot is the whole job, so it has nothing
    // in it and takes no room.
    stickyHeader(key = "held-release") { Spacer(Modifier.height(0.dp)) }

    val remainder = rest.drop(Held.RELEASE_AFTER)
    itemsIndexed(remainder, key = { _, item -> item.id }) { index, item ->
        Box(modifier = if (animate) Modifier.animateItem() else Modifier) {
            article(index + 1 + holding.size, item)
        }
    }
}
