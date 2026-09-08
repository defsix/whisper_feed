package com.saulhdev.feeder

import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.StarterSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The starter list, checked for the properties it exists to have.
 *
 * The addresses themselves were verified against the live endpoints before
 * they shipped and cannot be verified here — a unit test that reaches the
 * network is a test that fails on a train.
 */
class StarterSourcesTest {

    @Test
    fun `enough world sources for clustering to ever fire`() {
        // Breaking-news clustering needs three sources carrying the same story
        // before it groups anything. A shorter world list would leave the
        // feature switched on and never firing, which is worse than not
        // having it: it looks like it does not work.
        val world = StarterSources.ALL.count { it.categoryId == R.string.starter_category_world }
        assertTrue("only $world world sources; clustering needs three", world >= 3)
    }

    @Test
    fun `no feed is listed twice`() {
        val urls = StarterSources.ALL.map { it.url }
        assertEquals(urls.size, urls.distinct().size)
    }

    @Test
    fun `every feed is tagged`() {
        // The tags become the feed's first category chips, which the tour
        // points at. Untagged, that step gets skipped and the chip row is
        // empty on a phone that has just subscribed to nine feeds.
        assertTrue(StarterSources.ALL.all { it.categoryId != 0 })
        assertTrue(StarterSources.ALL.all { it.title.isNotBlank() })
    }

    @Test
    fun `a regional feed is not handed to somebody in another country`() {
        val berlin = StarterSources.defaultSelection(Locale("de", "DE"))
        val australian = StarterSources.ALL.filter { it.region == "AU" }
        assertTrue("nothing to test", australian.isNotEmpty())
        australian.forEach {
            assertFalse("${it.title} was pre-selected in Germany", it.url in berlin)
        }
    }

    @Test
    fun `and is handed to somebody who is there`() {
        val sydney = StarterSources.defaultSelection(Locale("en", "AU"))
        StarterSources.ALL.filter { it.region == "AU" }.forEach {
            assertTrue("${it.title} was not offered in Australia", it.url in sydney)
        }
    }

    @Test
    fun `everything without a region is offered everywhere`() {
        val anywhere = StarterSources.defaultSelection(Locale("ja", "JP"))
        StarterSources.ALL.filter { it.region == null }.forEach {
            assertTrue("${it.title} was not offered", it.url in anywhere)
        }
    }

    @Test
    fun `the list stays short enough to read`() {
        // It is a starting point, not a directory. Past a screenful nobody
        // reads the names, and the whole argument for showing them is that
        // they get read.
        assertTrue("the starter list has grown past ten", StarterSources.ALL.size <= 10)
    }
}
