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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import org.koin.compose.koinInject

/**
 * How much of the screen an article has earned.
 *
 * The Mosaic sizes were positional — item 0 and every eleventh got the big
 * tile, whatever they happened to be — which meant a two-line aggregator stub
 * could take both columns while the story of the day sat in a thumbnail. The
 * weight below is a property of the *article*, so it does not change when a
 * sync reorders the feed: the same article gets the same size wherever it
 * lands, which is the thing the positional rule was protecting against and
 * gets for free here.
 *
 * Every signal is something already known locally. Nothing is inferred from a
 * server, and nothing here needs an account.
 *
 * The numbers are deliberately blunt and all in one place. They are the part
 * that wants tuning against a real feed rather than reasoning.
 */
object ArticleWeight {

    /** No picture, so the big shapes are out however good the article is. */
    const val NO_IMAGE = 0f

    /** Every article with an image starts here. */
    const val BASE = 1f

    /** At or above this an article can take both columns. */
    const val LARGE_AT = 2.2f

    /** At or above this it keeps its own column at full height. */
    const val MEDIUM_AT = 1.6f

    /**
     * How many tiles must pass between two large ones.
     *
     * Without it a burst of fresh articles from a favourite source — exactly
     * what a morning sync produces — turns the whole first screen into full
     * width tiles, which is a list, not a mosaic.
     */
    const val LARGE_GAP = 6

    /**
     * How far down the feed to look for an opening anchor.
     *
     * A feed of nothing but yesterday's articles scores nothing above
     * [LARGE_AT], and opening on a flat grid of small tiles reads as a bug. The
     * best of the first few gets promoted instead — best of what is actually
     * there, rather than whatever sits at index 0.
     */
    const val ANCHOR_WITHIN = 6
}

/**
 * The weight for one article.
 *
 * @param affinity "more like this" scores by source id, as recorded by the
 *   article menu. This is the first thing that reads them back.
 * @param nowMs passed in rather than read, so a list is scored against one
 *   instant and an article near a boundary cannot be scored twice at two
 *   different ages within the same pass.
 */
fun articleWeight(
    item: FeedItem,
    affinity: Map<String, Int>,
    nowMs: Long,
): Float {
    if (item.article.imageUrl.isNullOrBlank()) return ArticleWeight.NO_IMAGE

    var weight = ArticleWeight.BASE

    // Freshness. A news surface that gives its biggest slot to something from
    // Tuesday is not a news surface.
    val ageHours = ((nowMs - item.timeMillis).coerceAtLeast(0L)) / 3_600_000f
    weight += when {
        ageHours < 2f  -> 1.2f
        ageHours < 6f  -> 0.8f
        ageHours < 24f -> 0.4f
        ageHours < 72f -> 0f
        else           -> -0.4f
    }

    // What the reader has said about the source, clamped so a dozen taps on
    // one source cannot make every one of its articles large for ever.
    weight += (affinity[item.sourceId] ?: 0).coerceIn(-3, 3) * 0.35f

    // Headline length. This is about the shape on screen, not the writing: a
    // twenty-word headline set at titleMedium fills the large tile and pushes
    // the summary off it, which wastes the width it was given.
    val words = item.contentTitle.trim().split(Regex("\\s+")).size
    weight += when {
        words < 3   -> -0.2f
        words <= 12 -> 0.4f
        words <= 18 -> 0f
        else        -> -0.25f
    }

    // The large tile shows a line of summary. Without one it is a picture and a
    // headline in a lot of empty space.
    weight += if (item.article.description.isNotBlank()) 0.3f else -0.3f

    // Already read: it has had its turn.
    if (item.article.readAt != 0L) weight -= 1.5f

    // Saved, and pinned, are the reader saying this one matters.
    if (item.bookmarked) weight += 0.4f
    if (item.pinned) weight += 1.5f

    return weight
}

/**
 * Sizes for a whole list, in order.
 *
 * A list rather than a per-item function because two of the rules are about
 * neighbours — large tiles have to be spaced out, and the top of the feed needs
 * an anchor — and neither can be answered by looking at one article.
 */
fun feedEmphasisFor(
    items: List<FeedItem>,
    affinity: Map<String, Int>,
    nowMs: Long,
): List<FeedEmphasis> {
    val weights = items.map { articleWeight(it, affinity, nowMs) }
    val sizes = MutableList(items.size) { FeedEmphasis.Small }

    var lastLarge = -ArticleWeight.LARGE_GAP - 1
    weights.forEachIndexed { index, weight ->
        sizes[index] = when {
            weight >= ArticleWeight.LARGE_AT &&
                    index - lastLarge > ArticleWeight.LARGE_GAP -> {
                lastLarge = index
                FeedEmphasis.Large
            }

            // A large-weight article that lands inside the gap still deserves
            // more than the smallest tile.
            weight >= ArticleWeight.MEDIUM_AT                   -> FeedEmphasis.Medium
            else                                                -> FeedEmphasis.Small
        }
    }

    // The opening anchor, if the scores did not produce one.
    val head = minOf(ArticleWeight.ANCHOR_WITHIN, items.size)
    if (head > 0 && sizes.take(head).none { it == FeedEmphasis.Large }) {
        val best = (0 until head)
            .filter { weights[it] > ArticleWeight.NO_IMAGE }
            .maxByOrNull { weights[it] }
        if (best != null) sizes[best] = FeedEmphasis.Large
    }

    return sizes
}

/**
 * The affinity preference, as a map.
 *
 * Stored as `sourceId:score` strings because DataStore has no map type; parsed
 * from the last colon so a source id containing one still splits correctly.
 */
fun parseAffinity(raw: Set<String>): Map<String, Int> = raw.mapNotNull { entry ->
    val at = entry.lastIndexOf(':')
    if (at <= 0) null
    else entry.substring(0, at) to (entry.substring(at + 1).toIntOrNull() ?: 0)
}.toMap()

/**
 * The sizes for the articles on screen, recomputed only when they change.
 *
 * Both surfaces need the same answer and neither should be threading an
 * affinity map through its own parameter list to get it, so the preference is
 * read here.
 */
@Composable
fun rememberFeedEmphasis(articles: List<FeedItem>): List<FeedEmphasis> {
    val prefs: FeedPreferences = koinInject()
    val raw by prefs.sourceAffinity.get().collectAsState(initial = emptySet())
    val affinity = remember(raw) { parseAffinity(raw) }
    // The clock is sampled per list rather than per frame: an article does not
    // need to shrink while it is being looked at.
    return remember(articles, affinity) {
        feedEmphasisFor(articles, affinity, System.currentTimeMillis())
    }
}
