package com.saulhdev.feeder

import com.saulhdev.feeder.ui.overlay.passedIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When an article counts as having been read past.
 *
 * This exists because of a bug worth not repeating: articles were marked the
 * moment they had been on screen long enough, so one being read carefully
 * faded out under the reader mid-sentence. Time on screen is the qualifier;
 * being scrolled past is the trigger.
 */
class ReadOnScrollTest {

    @Test
    fun `an article above everything on screen has been passed`() {
        val ready = mapOf("a" to 2, "b" to 3)
        assertEquals(listOf("a"), passedIds(ready, topmostVisible = 3))
    }

    @Test
    fun `an article still on screen has not been passed`() {
        // The bug in one line: this is the article being read, and it must not
        // be marked while the reader is still looking at it.
        val ready = mapOf("reading-it" to 5)
        assertTrue(passedIds(ready, topmostVisible = 5).isEmpty())
    }

    @Test
    fun `an article below the viewport has not been passed`() {
        // Scrolled back up, not read past. It keeps the time it earned and is
        // marked when the reader eventually goes by it.
        val ready = mapOf("below" to 9)
        assertTrue(passedIds(ready, topmostVisible = 4).isEmpty())
    }

    @Test
    fun `nothing is passed when nothing is on screen`() {
        // An empty layout means the list is still being measured. Treating it
        // as "everything scrolled past" would mark the whole feed read on a
        // rotation.
        val ready = mapOf("a" to 0, "b" to 1, "c" to 2)
        assertTrue(passedIds(ready, topmostVisible = null).isEmpty())
    }

    @Test
    fun `a run of articles passed together are all marked`() {
        val ready = mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 7)
        assertEquals(setOf("a", "b", "c"), passedIds(ready, topmostVisible = 6).toSet())
    }
}
