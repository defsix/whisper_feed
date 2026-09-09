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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import com.saulhdev.feeder.data.content.asState

/**
 * How often the tracker looks. Dwell is measured in whole seconds, so this is
 * fine-grained enough to be accurate and coarse enough that four wake-ups a
 * second are not spent watching a list nobody is scrolling.
 */
private const val TICK_MS = 500L

/**
 * How much of a card has to be on screen before its clock starts.
 *
 * A card a fifth visible at the bottom edge has not been read, and without
 * this the two items straddling the edges of the viewport accumulate time as
 * fast as the one in the middle.
 */
private const val VISIBLE_ENOUGH = 0.6f

/**
 * Marks an article read once it has been on screen long enough.
 *
 * Time on screen rather than scroll speed. A fling past forty headlines is
 * plainly not reading, but velocity is the wrong way to detect it: it swings
 * inside a single fling, so the same gesture would mark some cards and not
 * others depending on where in the deceleration curve they landed. How long a
 * card was actually visible asks the question directly.
 *
 * The threshold is the reader's to set, because the honest answer is that
 * there is no right number — it depends on how fast someone reads and how they
 * scroll. Off by default: this is a surface people scroll idly and come back
 * to, not an inbox.
 *
 * **Time on screen is the qualifier, not the trigger.** An earlier version
 * marked an article the moment its clock ran out, which meant it faded out
 * while it was being read — the reader watching a card grey out under their
 * eyes, mid-sentence, because they had been looking at it too long. That is
 * the exact opposite of what the setting is for.
 *
 * So the mark happens when the article has been *passed*: it earned its dwell,
 * and then scrolled off the top of the screen. Leaving upwards is the part
 * that matters and is why this counts indices rather than pixels — an article
 * that leaves through the bottom edge was scrolled back away from, not read
 * past, and keeps the dwell it earned for when the reader comes back down.
 *
 * The consequence is deliberate: an article read carefully and left on screen
 * is never marked, and the last article in the list cannot be marked at all,
 * because neither has been passed. Both are correct. Nothing dims while it is
 * being looked at.
 */
@Composable
fun MarkReadWhileScrolling(
    articles: List<FeedItem>,
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    onRead: (FeedItem) -> Unit,
) {
    val prefs: FeedPreferences = koinInject()
    val setting by prefs.markReadOnScroll.asState()
    // Below a quarter of a second means off: the slider's own bottom stop,
    // and anything shorter would fire during a fling anyway.
    val thresholdMs = remember(setting) { if (setting < 0.25f) 0L else (setting * 1000).toLong() }

    // Survives list changes on purpose: a sync rebuilds the list, and without
    // this every visible article would start its clock again from zero.
    val marked = remember { mutableSetOf<String>() }

    if (thresholdMs <= 0L) return

    LaunchedEffect(thresholdMs, isGrid, articles) {
        // Looked up by the key the list was given rather than by layout
        // position. The two are not the same and were being treated as such:
        // a sticky held article occupies a slot of its own, and so does the
        // empty one that releases it, so from the release onwards every layout
        // index pointed at the article before the one actually on screen — and
        // any header added above the feed would have shifted the lot.
        val position = articles.withIndex().associate { (i, item) -> item.id to i }
        val byId = articles.associateBy { it.id }

        val dwell = mutableMapOf<String, Long>()
        // Articles that have earned their dwell and are now only waiting to be
        // scrolled past, held with their position in the feed: deciding whether
        // one left through the top or the bottom is the whole point, and once
        // it is gone from the layout there is nothing left to ask.
        val ready = mutableMapOf<String, Int>()

        while (true) {
            delay(TICK_MS)
            val visible = visibleEnoughKeys(isGrid, listState, gridState)
            val onScreen = visibleKeys(isGrid, listState, gridState)
            val ids = HashSet<String>(visible.size)

            visible.forEach { id ->
                val item = byId[id] ?: return@forEach
                ids += id
                if (id in marked || id in ready || item.article.readAt != 0L) return@forEach

                val soFar = (dwell[id] ?: 0L) + TICK_MS
                dwell[id] = soFar
                if (soFar >= thresholdMs) {
                    // Not marked yet. It has been looked at long enough to
                    // count, and now has to be left behind before it counts.
                    dwell -= id
                    ready[id] = position[id] ?: return@forEach
                }
            }

            // Anything above everything still on screen has been passed.
            // Non-article keys — a sticky header, the glance row, the chips —
            // simply have no position and drop out of the comparison.
            val topmost = onScreen.mapNotNull { position[it] }.minOrNull()
            passedIds(ready, topmost).forEach { id ->
                ready -= id
                marked += id
                byId[id]?.let(onRead)
            }

            // An article scrolled away before the threshold starts again next
            // time it comes past. A glance is a glance, however many of them.
            // Anything in `ready` keeps what it earned: leaving through the
            // bottom means the reader scrolled back up, not that they passed
            // it, and making them dwell on it twice would be a strange thing
            // to ask.
            dwell.keys.retainAll(ids)
        }
    }
}

/**
 * The keys of everything showing at least [VISIBLE_ENOUGH] of itself.
 *
 * The two containers report their layout through different types with the same
 * shape, and neither shares an interface, so the arithmetic is written twice
 * rather than hidden behind a wrapper that would be longer than both.
 */
private fun visibleEnoughKeys(
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
): List<String> = if (isGrid) {
    val info = gridState.layoutInfo
    info.visibleItemsInfo.filter { item ->
        val height = item.size.height
        height > 0 && visibleFraction(
            start = item.offset.y,
            size = height,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
        ) >= VISIBLE_ENOUGH
    }.mapNotNull { it.key as? String }
} else {
    val info = listState.layoutInfo
    info.visibleItemsInfo.filter { item ->
        item.size > 0 && visibleFraction(
            start = item.offset,
            size = item.size,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
        ) >= VISIBLE_ENOUGH
    }.mapNotNull { it.key as? String }
}

/**
 * The keys of everything with any part of itself on screen.
 *
 * Separate from [visibleEnoughKeys], which answers "has this been looked at".
 * This answers "is this still there at all", and a card sliding out with a
 * sliver showing has not been passed yet.
 */
private fun visibleKeys(
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
): List<String> = if (isGrid) {
    gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
} else {
    listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
}

private fun visibleFraction(
    start: Int,
    size: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Float {
    val top = maxOf(start, viewportStart)
    val bottom = minOf(start + size, viewportEnd)
    return ((bottom - top).coerceAtLeast(0)).toFloat() / size
}

/**
 * Which of the waiting articles have been scrolled past.
 *
 * An article is passed when it sits above everything still on screen. Pulled
 * out of the loop because it is the rule the whole feature turns on, and
 * because the bug it replaced — articles dimming while they were being read —
 * came from having no name for this question at all.
 *
 * Nothing is passed when nothing is visible: an empty layout means the list is
 * still being measured, and treating that as "everything scrolled past" would
 * mark the whole feed read on a rotation.
 */
internal fun passedIds(ready: Map<String, Int>, topmostVisible: Int?): List<String> {
    if (topmostVisible == null) return emptyList()
    return ready.filterValues { it < topmostVisible }.keys.toList()
}
