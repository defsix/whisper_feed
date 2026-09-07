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

import com.saulhdev.feeder.data.db.models.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comma-separated tag string is what broke category filtering, so the
 * splitting and the matching that depends on it are pinned here.
 */
class FeedTagsTest {

    private fun feed(tag: String) = Feed(tag = tag)

    @Test
    fun `a single tag`() {
        assertEquals(listOf("Tech"), feed("Tech").tags)
    }

    @Test
    fun `several tags, however they were typed`() {
        assertEquals(listOf("Tech", "News"), feed("Tech,News").tags)
        assertEquals(listOf("Tech", "News"), feed("Tech, News").tags)
        assertEquals(listOf("Tech", "News"), feed(" Tech ,  News ").tags)
    }

    @Test
    fun `empty and ragged strings do not produce blank tags`() {
        assertEquals(emptyList<String>(), feed("").tags)
        assertEquals(emptyList<String>(), feed("  ").tags)
        assertEquals(listOf("Tech"), feed("Tech,").tags)
        assertEquals(listOf("Tech", "News"), feed("Tech,,News").tags)
    }

    @Test
    fun `a multi-tag feed matches either of its categories`() {
        // The whole bug: "Tech,News" matched neither chip, because the match
        // compared the entire string against one tag at a time.
        val f = feed("Tech,News")
        assertTrue(f.tags.any { it in setOf("Tech") })
        assertTrue(f.tags.any { it in setOf("News") })
        assertTrue(f.tags.any { it in setOf("News", "Science") })
        assertFalse(f.tags.any { it in setOf("Science") })
    }

    @Test
    fun `an untagged feed matches no category`() {
        assertFalse(feed("").tags.any { it in setOf("Tech") })
    }
}
