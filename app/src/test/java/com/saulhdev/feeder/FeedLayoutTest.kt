package com.saulhdev.feeder

import com.saulhdev.feeder.ui.overlay.FeedCardShape
import com.saulhdev.feeder.ui.overlay.feedCardShape
import com.saulhdev.feeder.ui.overlay.feedLayoutIsGrid
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.LAYOUT_LIST
import com.saulhdev.feeder.utils.LAYOUT_MAGAZINE
import com.saulhdev.feeder.utils.LAYOUT_MOSAIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedLayoutTest {

    @Test
    fun `cards opens on a hero and repeats one every eighth item`() {
        assertEquals(FeedCardShape.Hero, feedCardShape(0, hasImage = true, layout = LAYOUT_CARDS))
        assertEquals(FeedCardShape.Hero, feedCardShape(8, hasImage = true, layout = LAYOUT_CARDS))
        assertEquals(FeedCardShape.Hero, feedCardShape(16, hasImage = true, layout = LAYOUT_CARDS))
    }

    @Test
    fun `cards is mostly compact, which is what makes a hero read as an accent`() {
        val shapes = (0 until 32).map { feedCardShape(it, hasImage = true, layout = LAYOUT_CARDS) }
        val compact = shapes.count { it == FeedCardShape.Compact }
        assertTrue("expected compact to dominate, got $shapes", compact > shapes.size / 2)
    }

    @Test
    fun `an imageless article never gets an image-led shape`() {
        for (layout in listOf(LAYOUT_CARDS, LAYOUT_MAGAZINE)) {
            for (i in 0 until 16) {
                val shape = feedCardShape(i, hasImage = false, layout = layout)
                assertTrue(
                    "$layout gave $shape at $i",
                    shape == FeedCardShape.Compact || shape == FeedCardShape.Text,
                )
            }
        }
    }

    @Test
    fun `list is text at every position, with or without an image`() {
        for (i in 0 until 16) {
            assertEquals(FeedCardShape.Text, feedCardShape(i, true, LAYOUT_LIST))
            assertEquals(FeedCardShape.Text, feedCardShape(i, false, LAYOUT_LIST))
        }
    }

    @Test
    fun `mosaic is tiles throughout, so no full-width row lands in a grid cell`() {
        for (i in 0 until 16) {
            assertEquals(FeedCardShape.Tile, feedCardShape(i, true, LAYOUT_MOSAIC))
            assertEquals(FeedCardShape.Tile, feedCardShape(i, false, LAYOUT_MOSAIC))
        }
    }

    @Test
    fun `magazine never goes compact when there is an image to show`() {
        for (i in 0 until 16) {
            assertEquals(FeedCardShape.Card, feedCardShape(i, true, LAYOUT_MAGAZINE))
        }
    }

    @Test
    fun `only mosaic asks for a grid`() {
        assertTrue(feedLayoutIsGrid(LAYOUT_MOSAIC))
        assertFalse(feedLayoutIsGrid(LAYOUT_CARDS))
        assertFalse(feedLayoutIsGrid(LAYOUT_MAGAZINE))
        assertFalse(feedLayoutIsGrid(LAYOUT_LIST))
    }

    @Test
    fun `an unknown layout falls back to cards rather than crashing`() {
        assertEquals(FeedCardShape.Hero, feedCardShape(0, true, "nonsense"))
        assertFalse(feedLayoutIsGrid("nonsense"))
    }
}
