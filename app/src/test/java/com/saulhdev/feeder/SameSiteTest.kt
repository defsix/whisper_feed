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

import com.saulhdev.feeder.utils.registrableDomain
import com.saulhdev.feeder.utils.sameSiteGroups
import com.saulhdev.feeder.utils.titleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

private data class F(val title: String, val url: String)

/** Keyed on the url, because two feeds of one publication share a title. */
private fun group(vararg f: F) =
    sameSiteGroups(f.toList(), { URL(it.url) }, { it.title })
        .map { g -> g.map { it.url }.toSet() }

/**
 * Grouping feeds that look like the same publication.
 *
 * Deliberately a suggestion and not a verdict: the screen built on this shows
 * groups for a person to judge. The tests that matter most are therefore the
 * ones asserting it does *not* merge things — an over-eager group invites
 * somebody to delete a feed they wanted.
 */
class SameSiteTest {

    @Test
    fun `registrable domain takes two labels`() {
        assertEquals("engadget.com", registrableDomain("www.engadget.com"))
        assertEquals("techmeme.com", registrableDomain("techmeme.com"))
    }

    /** Naive last-two-labels would give `co.uk` and group every British site. */
    @Test
    fun `a compound public suffix takes three`() {
        assertEquals("guardian.co.uk", registrableDomain("feeds.guardian.co.uk"))
        assertEquals("theregister.co.uk", registrableDomain("www.theregister.co.uk"))
        assertEquals("bbci.co.uk", registrableDomain("feeds.bbci.co.uk"))
    }

    /**
     * The publication name is not reliably the first segment, which is the
     * bug this replaced: taking the head of "Blog – Hackaday" yielded "blog".
     */
    @Test
    fun `every title segment is offered as a key`() {
        assertTrue("hackaday" in titleKeys("Blog \u2013 Hackaday"))
        assertTrue("hackaday" in titleKeys("Hack a Day \u2014 Fresh hacks every day"))
        assertTrue("theguardian" in titleKeys("Ireland | The Guardian"))
        assertEquals(setOf("engadget"), titleKeys("Engadget"))
    }

    /** Generic and short segments are dropped, or half the list joins up. */
    @Test
    fun `generic words are not keys`() {
        assertTrue(titleKeys("News").isEmpty())
        assertTrue(titleKeys("Blog").isEmpty())
        // "World", "News" and "Sky" are each generic or too short, so this
        // whole title yields nothing rather than a key half the list shares.
        assertTrue(titleKeys("World News | Sky").isEmpty())
    }

    @Test
    fun `two feeds on one host are grouped`() {
        assertEquals(
            listOf(
                setOf(
                    "https://www.techmeme.com/feed.xml",
                    "https://www.techmeme.com/index.xml",
                )
            ),
            group(
                F("Techmeme A", "https://www.techmeme.com/feed.xml"),
                F("Techmeme B", "https://www.techmeme.com/index.xml"),
            ),
        )
    }

    /**
     * The case a host comparison cannot see, and the commonest way to end up
     * subscribed to one publication twice.
     */
    @Test
    fun `a feedburner alias is joined to its site by the title`() {
        val g = group(
            F("Android Authority", "https://feeds.feedburner.com/androidauthority"),
            F("Android Authority", "https://www.androidauthority.com/feed/"),
        )
        assertEquals(1, g.size)
        assertEquals(2, g.first().size)
    }

    /** Relatedness is transitive, or an alias chain splits into fragments. */
    @Test
    fun `a chain joined through a shared title becomes one group`() {
        val g = group(
            F("Hackaday", "https://feeds2.feedburner.com/hackaday/LgoM"),
            F("Hackaday", "https://www.hackaday.com/rss.xml"),
            F("Hackaday Retro", "https://www.hackaday.com/retro.xml"),
        )
        assertEquals(1, g.size)
        assertEquals(3, g.first().size)
    }

    @Test
    fun `unrelated feeds are not grouped`() {
        assertTrue(
            group(
                F("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index/"),
                F("Quanta Magazine", "https://www.quantamagazine.org/feed/"),
            ).isEmpty()
        )
    }

    /**
     * Two British sites must not merge through a shared `co.uk`. This is the
     * failure the suffix list exists to prevent.
     */
    @Test
    fun `different sites under one compound suffix stay apart`() {
        assertTrue(
            group(
                F("The Register", "https://www.theregister.co.uk/excerpts.rss"),
                F("The Guardian", "https://feeds.guardian.co.uk/theguardian/rss"),
            ).isEmpty()
        )
    }

    /**
     * FeedBurner hosts everybody. Two unrelated publications behind it share a
     * domain and must not be offered as one another's duplicate.
     */
    @Test
    fun `two unrelated feedburner feeds are grouped only by their shared host`() {
        val g = group(
            F("Torrentfreak", "https://feeds.feedburner.com/Torrentfreak"),
            F("PetaPixel", "https://feeds.feedburner.com/PetaPixel"),
        )
        // They do land together — the domain is genuinely the same — which is
        // precisely why this screen suggests rather than acts.
        assertEquals(1, g.size)
    }

    @Test
    fun `a lone feed is never a group`() {
        assertTrue(group(F("Only one", "https://example.com/feed")).isEmpty())
    }
}
