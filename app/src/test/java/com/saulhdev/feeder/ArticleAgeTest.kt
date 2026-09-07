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
package com.saulhdev.feeder

import com.saulhdev.feeder.utils.AgeUnit
import com.saulhdev.feeder.utils.articleAge
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The boundaries are the whole point of this function, so they are what is
 * tested — one case either side of every step up a unit.
 */
class ArticleAgeTest {

    private val now = 1_757_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val week = 7 * day

    private fun age(elapsed: Long) = articleAge(now, now - elapsed)

    @Test
    fun `under a minute is now`() {
        assertEquals(AgeUnit.NOW, age(0).unit)
        assertEquals(AgeUnit.NOW, age(59_999).unit)
    }

    @Test
    fun `a future timestamp reads as now rather than a negative age`() {
        // Feeds do publish these, usually via a bad timezone.
        assertEquals(AgeUnit.NOW, articleAge(now, now + day).unit)
    }

    @Test
    fun `minutes up to an hour`() {
        assertEquals(AgeUnit.MINUTES to 1, age(minute).let { it.unit to it.count })
        assertEquals(AgeUnit.MINUTES to 59, age(59 * minute).let { it.unit to it.count })
    }

    @Test
    fun `hours up to a day`() {
        assertEquals(AgeUnit.HOURS to 1, age(hour).let { it.unit to it.count })
        assertEquals(AgeUnit.HOURS to 23, age(23 * hour + 59 * minute).let { it.unit to it.count })
    }

    @Test
    fun `elapsed time floors rather than rounds`() {
        // 31 minutes is not "1h": rounding would claim the article is older
        // than it is, so every step floors.
        assertEquals(AgeUnit.MINUTES to 31, age(31 * minute).let { it.unit to it.count })
        assertEquals(AgeUnit.HOURS to 1, age(hour + 59 * minute).let { it.unit to it.count })
    }

    @Test
    fun `days up to a week`() {
        assertEquals(AgeUnit.DAYS to 1, age(day).let { it.unit to it.count })
        assertEquals(AgeUnit.DAYS to 6, age(6 * day + 23 * hour).let { it.unit to it.count })
    }

    @Test
    fun `weeks up to five, so a month-old article is not rushed into months`() {
        assertEquals(AgeUnit.WEEKS to 1, age(week).let { it.unit to it.count })
        assertEquals(AgeUnit.WEEKS to 4, age(30 * day).let { it.unit to it.count })
    }

    @Test
    fun `months then years`() {
        assertEquals(AgeUnit.MONTHS to 1, age(5 * week).let { it.unit to it.count })
        assertEquals(AgeUnit.MONTHS to 12, age(364 * day).let { it.unit to it.count })
        assertEquals(AgeUnit.YEARS to 1, age(365 * day).let { it.unit to it.count })
        assertEquals(AgeUnit.YEARS to 2, age(800 * day).let { it.unit to it.count })
    }
}
