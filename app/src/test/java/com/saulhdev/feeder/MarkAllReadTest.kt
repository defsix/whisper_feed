package com.saulhdev.feeder

import com.saulhdev.feeder.viewmodels.MarkReadRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkAllReadTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `the three choices, in the order they are offered`() {
        assertEquals(
            listOf(MarkReadRange.Everything, MarkReadRange.OlderThanHour, MarkReadRange.OlderThanDay),
            MarkReadRange.entries,
        )
    }

    @Test
    fun `each choice cuts off where it says`() {
        // Everything has no cut-off, so an article a site dated tomorrow is
        // still marked.
        assertEquals(Long.MAX_VALUE, MarkReadRange.Everything.before(now))
        assertEquals(now - 3_600_000L, MarkReadRange.OlderThanHour.before(now))
        assertEquals(now - 86_400_000L, MarkReadRange.OlderThanDay.before(now))
    }

    @Test
    fun `older reaches less far than everything`() {
        assertTrue(MarkReadRange.OlderThanDay.before(now) < MarkReadRange.OlderThanHour.before(now))
        assertTrue(MarkReadRange.OlderThanHour.before(now) < MarkReadRange.Everything.before(now))
    }

    @Test
    fun `the count and the marking ask the same question`() {
        val dao = File("src/main/java/com/saulhdev/feeder/data/db/dao/FeedArticleDao.kt").readText()
        val cut = "AND Article.primarySortTime < :before"
        assertEquals("both queries filter by the article's own date", 2, Regex(Regex.escape(cut)).findAll(dao).count())
        assertTrue(dao.contains("suspend fun unreadIdsBefore(before: Long): List<String>"))
        assertTrue(dao.contains("suspend fun unreadCountBefore(before: Long): Int"))
    }

    @Test
    fun `the settings row asks before it marks`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/PreferencesPage.kt").readText()
        assertTrue(page.contains("FeedPreferences.markEverythingRead = { markingRead = true }"))
        assertTrue(page.contains("articles.markAllRead(range, now) { count ->"))
        // Offered back on the same screen, not left to the feed's delayed offer,
        // which a reader coming back from Settings almost never saw.
        assertTrue(page.contains("snackbarHost = { SnackbarHost(snackbarHostState) }"))
        assertTrue(page.contains("if (result == SnackbarResult.ActionPerformed) articles.undoReads()"))
        assertTrue(page.contains("else articles.forgetUndoableReads()"))
        // The dialog passes on the moment it counted at, so what is marked is
        // what it showed.
        val dialog = File("src/main/java/com/saulhdev/feeder/ui/components/dialog/MarkAllReadDialog.kt").readText()
        assertTrue(dialog.contains("onConfirm(selected, now)"))
        assertTrue(dialog.contains("value = counts(now)"))
    }
}
