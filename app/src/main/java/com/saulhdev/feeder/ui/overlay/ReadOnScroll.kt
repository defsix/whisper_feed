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
 * Dwell accumulates while the list is still, too, which is deliberate: an
 * article held on screen while it is being read has been read, and requiring
 * movement would mean the one card you stopped on was the one that never
 * counted.
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
    val setting by prefs.markReadOnScroll.get()
        .collectAsState(initial = prefs.markReadOnScroll.getValue())
    // Below a quarter of a second means off: the slider's own bottom stop,
    // and anything shorter would fire during a fling anyway.
    val thresholdMs = remember(setting) { if (setting < 0.25f) 0L else (setting * 1000).toLong() }

    // Survives list changes on purpose: a sync rebuilds the list, and without
    // this every visible article would start its clock again from zero.
    val marked = remember { mutableSetOf<String>() }

    if (thresholdMs <= 0L) return

    LaunchedEffect(thresholdMs, isGrid, articles) {
        val dwell = mutableMapOf<String, Long>()
        while (true) {
            delay(TICK_MS)
            val visible = visibleEnoughIndices(isGrid, listState, gridState)
            val ids = HashSet<String>(visible.size)

            visible.forEach { index ->
                val item = articles.getOrNull(index) ?: return@forEach
                val id = item.id
                ids += id
                if (id in marked || item.article.readAt != 0L) return@forEach

                val soFar = (dwell[id] ?: 0L) + TICK_MS
                dwell[id] = soFar
                if (soFar >= thresholdMs) {
                    marked += id
                    dwell -= id
                    onRead(item)
                }
            }

            // An article scrolled away before the threshold starts again next
            // time it comes past. A glance is a glance, however many of them.
            dwell.keys.retainAll(ids)
        }
    }
}

/**
 * The indices currently showing at least [VISIBLE_ENOUGH] of themselves.
 *
 * The two containers report their layout through different types with the same
 * shape, and neither shares an interface, so the arithmetic is written twice
 * rather than hidden behind a wrapper that would be longer than both.
 */
private fun visibleEnoughIndices(
    isGrid: Boolean,
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
): List<Int> = if (isGrid) {
    val info = gridState.layoutInfo
    info.visibleItemsInfo.filter { item ->
        val height = item.size.height
        height > 0 && visibleFraction(
            start = item.offset.y,
            size = height,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
        ) >= VISIBLE_ENOUGH
    }.map { it.index }
} else {
    val info = listState.layoutInfo
    info.visibleItemsInfo.filter { item ->
        item.size > 0 && visibleFraction(
            start = item.offset,
            size = item.size,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
        ) >= VISIBLE_ENOUGH
    }.map { it.index }
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
