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

import android.content.Context
import com.saulhdev.feeder.R

enum class AgeUnit { NOW, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }

/** How old an article is, at the coarsest useful granularity. */
data class ArticleAge(val unit: AgeUnit, val count: Int)

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

/**
 * How old something is, as a number and a unit.
 *
 * Steps up a unit at a time: minutes below an hour, hours below a day, days
 * below a week, weeks below roughly a month, then months, then years. This is
 * the granularity a feed wants — the difference between 09:14 and 09:22 does
 * not matter once an article is a day old, and the previous format spelled out
 * "3 minutes ago" in full, which was long enough to push the source name into
 * an ellipsis on a compact row.
 *
 * Each step **floors** rather than rounds. Rounding would let something 31
 * minutes old read as "1h", which claims the article is older than it is;
 * flooring only ever under-states, which is the safer direction for a
 * timestamp. A future date — feeds do publish them — reads as "now" rather
 * than as a negative age.
 *
 * Kept free of Context so the boundaries can be tested directly; see
 * [format] for the display strings.
 */
fun articleAge(nowMillis: Long, thenMillis: Long): ArticleAge {
    val elapsed = nowMillis - thenMillis
    return when {
        elapsed < MINUTE -> ArticleAge(AgeUnit.NOW, 0)
        elapsed < HOUR   -> ArticleAge(AgeUnit.MINUTES, (elapsed / MINUTE).toInt())
        elapsed < DAY    -> ArticleAge(AgeUnit.HOURS, (elapsed / HOUR).toInt())
        elapsed < WEEK   -> ArticleAge(AgeUnit.DAYS, (elapsed / DAY).toInt())
        // Five weeks rather than four, so a 30-day-old article is "4w" instead
        // of jumping straight to "1mo" while it is still under a month old.
        elapsed < 5 * WEEK -> ArticleAge(AgeUnit.WEEKS, (elapsed / WEEK).toInt())
        elapsed < 365 * DAY -> ArticleAge(AgeUnit.MONTHS, (elapsed / (30 * DAY)).toInt())
        else -> ArticleAge(AgeUnit.YEARS, (elapsed / (365 * DAY)).toInt())
    }
}

fun ArticleAge.format(context: Context): String = when (unit) {
    AgeUnit.NOW     -> context.getString(R.string.age_now)
    AgeUnit.MINUTES -> context.getString(R.string.age_minutes, count)
    AgeUnit.HOURS   -> context.getString(R.string.age_hours, count)
    AgeUnit.DAYS    -> context.getString(R.string.age_days, count)
    AgeUnit.WEEKS   -> context.getString(R.string.age_weeks, count)
    AgeUnit.MONTHS  -> context.getString(R.string.age_months, count)
    AgeUnit.YEARS   -> context.getString(R.string.age_years, count)
}

/** Convenience for the feed: an article's age, ready to draw. */
fun formatArticleAge(
    context: Context,
    thenMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): String = articleAge(nowMillis, thenMillis).format(context)
