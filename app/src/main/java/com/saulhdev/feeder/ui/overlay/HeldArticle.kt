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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
     * A pin is not governed by this at all — it is not held here. See
     * [heldFeed].
     */
    const val RELEASE_AFTER = 5
}

/**
 * Whether the card being drawn is the one held to the top of the feed.
 *
 * A held card is a `stickyHeader`: it is drawn over the list rather than in
 * it, and the articles it is holding scroll underneath. That makes its opacity
 * load-bearing in a way no other card's is — at anything less than solid it
 * stops being a card and becomes a window onto whatever is passing beneath.
 *
 * Which is exactly what happened. Holding an article to the viewport means it
 * never leaves the screen, the read tracker counts what does not leave the
 * screen as read, and a read card is drawn at 55% opacity. So the app held a
 * breaking story up to be noticed, decided from its own holding of it that the
 * reader had seen it, and faded it into a translucent sheet lying across the
 * next five articles.
 *
 * [com.saulhdev.feeder.ui.overlay.FeedArticleItem] already carries this exact
 * argument for pins — "held at the top… which makes it permanently visible and
 * therefore permanently read" — and a held cluster lead is the same case by
 * the same reasoning. It was missed because the two are held by different
 * machinery: a pin by the sort order, this by a sticky header.
 *
 * A composition local rather than another parameter, because the card is
 * reached through a lambda the two feed surfaces each supply, and threading a
 * flag through both of them would put the question in four places that do not
 * otherwise care about it.
 */
val LocalHeldAtTop = compositionLocalOf { false }

/**
 * The breaking story to hold at the top, or null.
 *
 * @param clusters the current story clusters; a lead is the only thing held.
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
        // A pin is deliberately not held here any more. Sorting already puts
        // it at the top of the list, under the glance row and the chips, and
        // that is where it should stay — first in the feed, scrolling away
        // like anything else, still first on the next refresh because the
        // order comes from the stored flag rather than from anything on
        // screen. Sticking it to the viewport as well made it follow the
        // reader down the feed, which is a different thing and not the thing
        // that was wanted.
        val isLead = clusters[first.id]?.leadId == first.id
        if (isLead) first else null
    }
}

/**
 * Lays out the feed with the held article stuck to the top, then let go.
 *
 * **Only a breaking story is held here.** A pin is handled entirely by the
 * ordering: it sorts to the top of the list, under the glance row and the
 * chips, and scrolls away like any other card — still first on the next
 * refresh, because that position comes from the stored flag rather than from
 * anything on screen.
 *
 * Holding a pin to the viewport as well was a misreading. "Keep it at the top"
 * means the top of the list, not a card that follows the reader down the feed
 * and covers what they are trying to read. The pin's job is to be findable,
 * not to be unavoidable.
 *
 * A cluster lead is the opposite case and does belong here. It is the app's
 * own guess, nobody asked for it, and no control turns off that particular
 * one — so it is held briefly, to be noticed, and then let go, because the
 * scroll has to be the way out of something the reader never chose.
 *
 * Mechanically: `stickyHeader` holds for as long as its section is on screen,
 * the held article heads a section holding only the next few articles, and
 * then a second, empty header takes the sticky slot and pushes it off the top.
 * That header has no height and nothing in it, so what the reader sees is the
 * card sliding away after a few articles, with no animation to write.
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

    stickyHeader(key = "held-${held.id}") {
        // Marked as held for the card itself, which has no other way to know.
        // A sticky header is drawn *over* the list, so anything less than
        // fully opaque turns it into a window onto the articles sliding past
        // underneath — see [LocalHeldAtTop].
        CompositionLocalProvider(LocalHeldAtTop provides true) {
            article(0, held)
        }
    }

    val rest = articles.drop(1)


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
