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
import com.saulhdev.feeder.data.db.models.DayCount
import com.saulhdev.feeder.data.db.models.HourCount
import com.saulhdev.feeder.data.db.models.ReadingTime
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlin.math.roundToInt

/** How many days the charts cover. A month is long enough to have a shape. */
const val STATS_WINDOW_DAYS = 30

/**
 * Below this many articles the charts are drawn from an example instead.
 *
 * Twenty is about a single sitting. Under that, every chart is one bar and a
 * lot of white space, which tells a new reader nothing about what the screen
 * is for — see [StatisticsState.isExample].
 */
const val STATS_MIN_REAL = 20

/**
 * Everything the statistics screen draws, and whether any of it is real.
 *
 * @param isExample the charts are made up, and the screen must say so. Not a
 *   loading state and not an empty state: it is a worked example standing in
 *   for data that does not exist yet.
 * @param openingUncounted every article in the window reached the screen and
 *   none was opened. Almost certainly not a reading habit — opening has only
 *   been counted since the release that added it, so a history collected
 *   before then has seen counts and no opened ones. Worth saying, because a
 *   chart with one series flat at zero otherwise reads as a broken feature.
 */
data class StatisticsState(
    val byDay: List<DayCount> = emptyList(),
    val byHour: List<HourCount> = emptyList(),
    val readingTime: ReadingTime = ReadingTime(totalMs = 0, articles = 0),
    val isExample: Boolean = false,
    val loading: Boolean = true,
) {
    val seen: Int get() = byDay.sumOf { it.seen }
    val opened: Int get() = byDay.sumOf { it.opened }

    /** Articles a day, over the days that had any. Zero days would flatten it. */
    val perActiveDay: Int
        get() {
            val active = byDay.count { it.seen > 0 }
            return if (active == 0) 0 else (seen.toFloat() / active).roundToInt()
        }

    /** The hour with the most reading in it, or null if there is no reading. */
    val busiestHour: Int?
        get() = byHour.filter { it.seen > 0 }.maxByOrNull { it.seen }?.hour?.toIntOrNull()

    val openingUncounted: Boolean get() = !isExample && seen > 0 && opened == 0

    /**
     * Time spent inside articles, as hours and minutes.
     *
     * Null when nothing was measured at all, which is a different statement
     * from "no time" and has to be said differently on the screen — an
     * article opened in an external browser records nothing, so a reader who
     * uses that mode would otherwise be told they read for zero minutes.
     */
    val readingHoursMinutes: Pair<Long, Long>?
        get() {
            if (readingTime.totalMs <= 0L) return null
            val minutes = readingTime.totalMs / 60_000L
            return minutes / 60 to minutes % 60
        }

    /** Average time per article that had any recorded, rounded to the minute. */
    val minutesPerArticle: Int
        get() {
            if (readingTime.articles <= 0) return 0
            return (readingTime.totalMs / readingTime.articles / 60_000L).toInt()
        }

    /** The longest run of consecutive days with any reading on them. */
    val streak: Int
        get() {
            var best = 0
            var run = 0
            byDay.forEach { day ->
                run = if (day.seen > 0) run + 1 else 0
                if (run > best) best = run
            }
            return best
        }
}

/**
 * Reading habits over time, from what is already on the device.
 *
 * Both charts come from columns the app has always written for its own
 * purposes — `readAt` since read state existed, `openedAt` since the release
 * that separated opening an article from scrolling past it. Nothing is
 * collected for this screen, nothing leaves the device, and switching the
 * screen off would not make the app collect less.
 *
 * The window is deliberately the same thirty days the weighting uses. Two
 * numbers describing "recently" that disagree about what recently means is a
 * bug report waiting to happen, and somebody comparing this screen against
 * Learned should find the same reading behind both.
 */
class StatisticsViewModel(
    private val articleRepo: ArticleRepository,
) : NeoViewModel() {

    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    private val since: Long
        get() = System.currentTimeMillis() -
                STATS_WINDOW_DAYS.toLong() * 24 * 60 * 60 * 1000

    val state: StateFlow<StatisticsState> = run {
        val from = since
        combine(
            articleRepo.readingByDay(from, STATS_WINDOW_DAYS),
            articleRepo.readingByHour(from),
            articleRepo.readingTime(from),
        ) { days, hours, time ->
            val total = days.sumOf { it.seen }
            if (total < STATS_MIN_REAL) {
                StatisticsState(
                    byDay = exampleDays(STATS_WINDOW_DAYS, days.map { it.day }),
                    byHour = exampleHours(),
                    readingTime = ReadingTime(totalMs = 4 * 3_600_000L + 12 * 60_000L, articles = 63),
                    isExample = true,
                    loading = false,
                )
            } else {
                StatisticsState(
                    byDay = days,
                    byHour = hours,
                    readingTime = time,
                    loading = false,
                )
            }
        }
    }.stateIn(ioScope, SharingStarted.Eagerly, StatisticsState())
}

/*
 * The worked example.
 *
 * Shown only when there is too little real reading to draw, and always behind
 * a label that says so — a chart of invented numbers presented as somebody's
 * own reading would be a lie told in a graph, which is worse than an empty
 * screen rather than better.
 *
 * Fixed rather than random, for two reasons. A screen that shows different
 * figures every time it is opened looks broken, and an example is supposed to
 * demonstrate what a shape means: the weekday-heavy days and the
 * breakfast-and-evening hours below are the two patterns this screen exists to
 * make visible, so the example is the reader's first lesson in reading it.
 */

/** A month of plausible reading, keyed to the same dates the real chart uses. */
internal fun exampleDays(days: Int, keys: List<String>): List<DayCount> {
    // Weekdays busier than weekends, tailing off over a couple of quiet days,
    // because a flat month would demonstrate nothing.
    val seen = listOf(
        18, 24, 21, 26, 30, 12, 9,
        22, 27, 25, 31, 28, 14, 11,
        19, 23, 29, 24, 33, 16, 8,
        0, 0, 26, 30, 27, 35, 19,
        13, 21,
    )
    // Roughly a fifth opened, varied so the stack is not a constant fraction.
    val opened = listOf(
        3, 6, 4, 7, 9, 2, 1,
        5, 8, 6, 11, 7, 3, 2,
        4, 6, 10, 5, 12, 4, 1,
        0, 0, 7, 9, 6, 13, 5,
        2, 6,
    )
    return (0 until days).map { i ->
        DayCount(
            day = keys.getOrElse(i) { "" },
            seen = seen.getOrElse(i % seen.size) { 0 },
            opened = opened.getOrElse(i % opened.size) { 0 },
        )
    }
}

/** A day shaped like most people's: breakfast, lunch, and a long evening. */
internal fun exampleHours(): List<HourCount> {
    val seen = listOf(
        1, 0, 0, 0, 0, 2, 9, 34, 41, 22, 14, 18,
        26, 19, 12, 15, 21, 38, 44, 36, 29, 23, 12, 4,
    )
    val opened = listOf(
        0, 0, 0, 0, 0, 0, 2, 7, 9, 4, 3, 4,
        6, 4, 2, 3, 5, 9, 12, 10, 7, 5, 2, 1,
    )
    return (0..23).map { h ->
        HourCount(hour = "%02d".format(h), seen = seen[h], opened = opened[h])
    }
}
