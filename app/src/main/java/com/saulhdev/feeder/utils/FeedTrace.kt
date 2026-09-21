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
package com.saulhdev.feeder.utils

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val FEED_TAG = "FeedTrace"

/**
 * Counts what the feed pipeline does, so a scroll can be measured rather than
 * reasoned about.
 *
 * A diagnostics report showed the collector freeing 100–200MB per cycle on a
 * 256MB heap, every six to eight seconds, throughout a scroll. Reading the
 * source produced a plausible account — the feed query returns five hundred
 * whole article rows including their full text, any write to the Article table
 * re-runs it, and the dwell tracker writes a row every time an article leaves
 * the screen — but plausible is where the last three days' worth of wrong
 * guesses also started. The crash in this app's history was found by a stack
 * trace, and the card padding by measuring a screenshot; neither was found by
 * reading.
 *
 * So the pipeline counts itself. Emissions, the rows behind them, how long the
 * processing took, and how many dwell writes each flush collapsed — enough to
 * say whether the account above is right and, afterwards, whether the change
 * made from it actually worked.
 *
 * **Counts and durations only, never content.** These lines go into a file the
 * reader sends to a stranger. What was on screen, what they searched for and
 * what they subscribe to are all absent by construction: there is nowhere in
 * this file to put them.
 *
 * Summarised on a cadence rather than logged per event. The report carries the
 * last 2000 lines of logcat, and a line per emission during a fast scroll
 * would push everything else — the GC lines this exists to explain — out of
 * the window before it was read.
 *
 * Nothing is written unless the reader has turned Debugging on; every counter
 * here is an atomic add on a path that already allocates, and the reporter
 * only runs while tracing.
 */
object FeedTrace {

    /** Feed query emissions: how often Room handed the pipeline a new list. */
    private val emissions = AtomicInteger()

    /** Rows across those emissions, for the cost each one carries. */
    private val rows = AtomicLong()

    /** Emissions that survived the debounce and were actually processed. */
    private val processed = AtomicInteger()

    /** Wall time inside processArticles, in microseconds. */
    private val processMicros = AtomicLong()

    /** Dwell flushes, and the writes each one stood in for. */
    private val flushes = AtomicInteger()
    private val flushedRows = AtomicInteger()

    /** Dwell increments asked for, whether or not they have been written yet. */
    private val dwellAsks = AtomicInteger()

    fun emission(count: Int) {
        emissions.incrementAndGet()
        rows.addAndGet(count.toLong())
    }

    fun processed(micros: Long) {
        processed.incrementAndGet()
        processMicros.addAndGet(micros)
    }

    fun dwellAsked() {
        dwellAsks.incrementAndGet()
    }

    fun dwellFlushed(rowCount: Int) {
        flushes.incrementAndGet()
        flushedRows.addAndGet(rowCount)
    }

    /**
     * Empties the counters into one line, or returns null if nothing happened.
     *
     * Read-and-reset rather than read: each line is the window since the last
     * one, so two lines can be compared without subtracting them, and a quiet
     * window produces no line at all rather than a repeat of the last figures.
     */
    private fun drain(windowMs: Long): String? {
        val e = emissions.getAndSet(0)
        val r = rows.getAndSet(0)
        val p = processed.getAndSet(0)
        val micros = processMicros.getAndSet(0)
        val f = flushes.getAndSet(0)
        val fr = flushedRows.getAndSet(0)
        val asks = dwellAsks.getAndSet(0)
        if (e == 0 && p == 0 && f == 0 && asks == 0) return null

        val seconds = windowMs / 1000.0
        val perSecond = if (seconds > 0) e / seconds else 0.0
        // Rows per emission rather than total: the total is the product and
        // says less. Five hundred rows an emission is the whole point — it is
        // what makes an emission expensive.
        val perEmission = if (e > 0) r / e else 0L
        val avgMs = if (p > 0) micros / p / 1000.0 else 0.0

        return buildString {
            append("emissions %d (%.1f/s, %d rows each)".format(e, perSecond, perEmission))
            append(", processed %d avg %.1fms".format(p, avgMs))
            append(", dwell %d asks -> %d writes".format(asks, f))
            if (f > 0) append(" (%d rows)".format(fr))
        }
    }

    /**
     * One summary line per window, for as long as tracing is on.
     *
     * `Log.println` rather than `Log.d`, for the reason
     * [com.saulhdev.feeder.ui.overlay.gateLog] gives: proguard-rules.pro strips
     * `Log.d`/`v`/`i`, so a trace written with those exists only in the debug
     * build — which is the one build where the question this answers cannot
     * come up.
     */
    fun report(windowMs: Long) {
        val line = drain(windowMs) ?: return
        Log.println(Log.DEBUG, FEED_TAG, line)
    }

    /** Throws away anything counted so far, so a window starts clean. */
    fun reset() {
        drain(1L)
    }
}

/** How often [FeedTrace] writes a line while Debugging is on. */
const val FEED_TRACE_WINDOW_MS = 5_000L
