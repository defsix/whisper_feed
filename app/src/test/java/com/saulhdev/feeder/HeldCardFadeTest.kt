package com.saulhdev.feeder

import com.saulhdev.feeder.ui.overlay.isCardFaded
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nothing the app holds on screen is dimmed for having been seen.
 *
 * A held breaking story is a `stickyHeader`, drawn over the list while the
 * articles it holds scroll underneath. Because it never leaves the viewport
 * the read tracker marked it read, and a read card is drawn at 55% opacity —
 * so the app held a story up to be noticed, concluded from its own holding of
 * it that the reader had seen it, and faded it into a translucent sheet lying
 * across the next five articles.
 *
 * The pin exemption already carries this argument in full. A cluster lead
 * reaches the same place by different machinery — a pin through the sort
 * order, a lead through a sticky header — which is why one was exempt and the
 * other was not.
 */
class HeldCardFadeTest {

    @Test
    fun `an ordinary read article still fades`() {
        // The feature itself is not being turned off.
        assertTrue(isCardFaded(faded = true, pinned = false, heldAtTop = false))
    }

    @Test
    fun `an unread article is never faded`() {
        assertFalse(isCardFaded(faded = false, pinned = false, heldAtTop = false))
    }

    @Test
    fun `a held breaking story does not fade`() {
        // The reported bug: faded, and obscuring the posts behind it.
        assertFalse(isCardFaded(faded = true, pinned = false, heldAtTop = true))
    }

    @Test
    fun `a pinned article does not fade`() {
        assertFalse(isCardFaded(faded = true, pinned = true, heldAtTop = false))
    }

    @Test
    fun `an article that is both pinned and held does not fade`() {
        assertFalse(isCardFaded(faded = true, pinned = true, heldAtTop = true))
    }

    @Test
    fun `being held does not fade an article that had no reason to fade`() {
        assertFalse(isCardFaded(faded = false, pinned = false, heldAtTop = true))
    }
}
