package com.saulhdev.feeder

import com.saulhdev.feeder.ui.overlay.DayBreak
import com.saulhdev.feeder.ui.overlay.DayName
import com.saulhdev.feeder.ui.overlay.FeedSegment
import com.saulhdev.feeder.ui.overlay.dayBreaks
import com.saulhdev.feeder.ui.overlay.dayName
import com.saulhdev.feeder.ui.overlay.feedListIndex
import com.saulhdev.feeder.ui.overlay.feedSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * "Updated 47m ago" at the top, and nothing a hundred cards down to say
 * whether they were from this morning or last week.
 */
class FeedDaysTest {
    private val sat = LocalDate.of(2026, 9, 26)
    private val fri = sat.minusDays(1)
    private val thu = sat.minusDays(2)

    @Test
    fun `a heading above the first article of each day`() {
        assertEquals(
            listOf(DayBreak(0, sat), DayBreak(2, fri), DayBreak(3, thu)),
            dayBreaks(listOf(sat, sat, fri, thu, thu)),
        )
    }

    @Test
    fun `pinned articles at the top get no heading, and no day is named twice`() {
        // Pinned (null), then a held story from Friday above Saturday's.
        val breaks = dayBreaks(listOf(null, null, fri, sat, sat, fri))
        assertEquals(listOf(DayBreak(2, fri), DayBreak(3, sat)), breaks)
    }

    @Test
    fun `the feed is cut into runs under their headings`() {
        val breaks = listOf(DayBreak(2, sat), DayBreak(5, fri))
        assertEquals(
            listOf(FeedSegment(null, 0, 2), FeedSegment(sat, 2, 5), FeedSegment(fri, 5, 7)),
            feedSegments(7, breaks),
        )
        assertEquals(listOf(FeedSegment(null, 0, 4)), feedSegments(4, emptyList()))
        assertTrue(feedSegments(0, emptyList()).isEmpty())
    }

    @Test
    fun `an article's place in the list counts the headings above it`() {
        val breaks = listOf(DayBreak(0, sat), DayBreak(3, fri))
        assertEquals("header + Today", 2, feedListIndex(0, breaks))
        assertEquals(4, feedListIndex(2, breaks))
        assertEquals("and Yesterday", 6, feedListIndex(3, breaks))
        assertEquals("no headings, as before", 4, feedListIndex(3, emptyList()))
    }

    @Test
    fun `days are named the way people say them`() {
        assertEquals(DayName.Today, dayName(sat, sat))
        assertEquals(DayName.Yesterday, dayName(fri, sat))
        assertEquals(DayName.Weekday, dayName(sat.minusDays(6), sat))
        assertEquals(DayName.Date, dayName(sat.minusDays(7), sat))
        assertEquals("a clock running ahead is still a date", DayName.Date, dayName(sat.plusDays(1), sat))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `headings only in date order, and not while searching`() {
        val page = source("ui/pages/ArticleListPage.kt")
        assertTrue(page.contains("if (sort == SORT_CHRONOLOGICAL && !searching) feedDayBreaks(frame.articles) else emptyList()"))
        assertTrue("both containers", Regex("segments.forEach \\{ segment ->").findAll(page).count() == 2)
        assertTrue("and the return-to-article scroll counts them", page.contains("breaks = dayBreaks,"))
        assertTrue(source("ui/overlay/FocusAnchor.kt").contains("val index = feedListIndex(position, breaks)"))
    }

    @Test
    fun `share is in the menu, not on the card`() {
        val card = source("ui/overlay/ArticleCard.kt")
        assertFalse(card.contains("IconButton(onClick = onShare)"))
        assertTrue(source("ui/overlay/ArticleMenu.kt").contains("MenuEntry(R.string.share, Phosphor.ShareNetwork)"))
    }
}
