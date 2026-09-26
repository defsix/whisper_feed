package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.accountSummary
import com.saulhdev.feeder.manager.sync.service.refusalCode
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Did it all get there?" After moving 114 feeds to a server, the only answer
 * was a shell on the server and a command to count them.
 */
class AccountTallyTest {

    @Test
    fun `a quiet sync still says what the server has`() {
        assertEquals(
            "110 feeds on the server; 1247 articles matched",
            accountSummary(AccountTally(serverFeeds = 110, matched = 1247)),
        )
    }

    @Test
    fun `everything that happened is named`() {
        val t = AccountTally(
            serverFeeds = 114, feedsSent = 4, feedsRefused = 1, feedsRemoved = 2, feedsAdded = 3, matched = 50,
            readSent = 5, savedSent = 1, changesKept = 2, readHere = 7, unreadHere = 1, savedHere = 2,
        )
        assertEquals(
            "114 feeds on the server, 4 sent, 1 refused, 2 removed, 3 added here; 50 articles matched; " +
                "sent 5 read, 1 saved; 2 changes kept; received 7 read, 1 unread, 2 saved",
            accountSummary(t),
        )
    }

    @Test
    fun `the history line carries it after the feed counts, and not on a failure`() {
        val ok = SyncResult(due = 114, unchanged = 20, account = AccountTally(serverFeeds = 110, matched = 9))
        assertEquals("ok (114 feeds, 20 unchanged); 110 feeds on the server; 9 articles matched", syncOutcome(ok))
        assertFalse(syncOutcome(ok.copy(error = "IOException")).contains("on the server"))
    }

    @Test
    fun `a refusal says what the server answered`() {
        assertEquals(" (400)", refusalCode(400))
        assertEquals(" (no answer)", refusalCode(0))
        assertEquals("", refusalCode(null))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the sync counts what it did and keeps it for the account screen`() {
        val service = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(service.contains("feedsRefused = plan.subscribe.size - subscribed.size,"))
        assertTrue(service.contains("serverFeeds = (remoteAs.keys + subscribed - unsubscribed).size,"))
        assertTrue(service.contains("if (done) accepted[i] += chunk.size else ok = false"))
        assertTrue(service.contains("AccountTallyStore.write(context, tally, account.lastSync)"))
        val page = source("ui/pages/AccountPage.kt")
        assertTrue(page.contains("state.tally?.let { tally ->"))
        val account = source("data/content/SyncAccount.kt")
        assertEquals("cleared on sign in and sign out", 2, Regex("AccountTallyStore.clear\\(appContext\\)").findAll(account).count())
    }

    @Test
    fun `Data sources always has a way back`() {
        val page = source("ui/pages/SourceListPage.kt")
        assertTrue(page.contains("showBackButton = true,"))
        assertFalse(page.contains("showBackButton = selecting"))
        assertTrue(page.contains("onBackAction = if (selecting) viewModel::clearSelection else null,"))
    }
}
