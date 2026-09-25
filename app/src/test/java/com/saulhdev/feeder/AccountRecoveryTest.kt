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

import com.saulhdev.feeder.manager.sync.greader.matchFeeds
import com.saulhdev.feeder.manager.sync.greader.titleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The first night against a live server: 29 feeds copied both ways because
 * their addresses differed, three syncs at once, and fifty-megabyte retries.
 */
class AccountRecoveryTest {

    @Test
    fun `the same address is the same feed`() {
        val m = matchFeeds(mapOf("verge/rss" to "The Verge"), mapOf("verge/rss" to "The Verge"), emptyMap())
        assertEquals(mapOf("verge/rss" to "verge/rss"), m.serverAs)
        assertTrue(m.newAliases.isEmpty())
    }

    @Test
    fun `a different address with the same title is the same feed, and is remembered`() {
        val m = matchFeeds(
            local = mapOf("pocket-lint.com/rss.phtml" to "Pocket-lint"),
            server = mapOf("pocket-lint.com/feed" to "Pocket-lint "),
            aliases = emptyMap(),
        )
        assertEquals("pocket-lint.com/rss.phtml", m.serverAs["pocket-lint.com/feed"])
        assertEquals(mapOf("pocket-lint.com/feed" to "pocket-lint.com/rss.phtml"), m.newAliases)
    }

    @Test
    fun `a remembered pairing holds after a rename`() {
        val m = matchFeeds(
            local = mapOf("a/rss" to "My name for it"),
            server = mapOf("a/feed" to "Publisher's name"),
            aliases = mapOf("a/feed" to "a/rss"),
        )
        assertEquals("a/rss", m.serverAs["a/feed"])
    }

    @Test
    fun `a second copy of a matched feed is left alone on either side`() {
        val m = matchFeeds(
            local = mapOf("npr/1" to "NPR", "npr/local-copy" to "NPR"),
            server = mapOf("npr/1" to "NPR", "npr/server-copy" to "NPR", "npr/third" to "NPR"),
            aliases = emptyMap(),
        )
        assertEquals("npr/1", m.serverAs["npr/1"])
        // One server copy pairs with the spare local one, one to one; the rest is left.
        assertEquals(2, m.serverAs.values.filterNotNull().toSet().size)
        assertEquals("left alone, not copied down", 1, m.serverAs.values.count { it == null })
        assertTrue(m.localIgnored.isEmpty())
    }

    @Test
    fun `a local second copy is not sent up`() {
        val m = matchFeeds(
            local = mapOf("bbc/1" to "BBC News", "bbc/2" to "BBC News"),
            server = mapOf("bbc/1" to "BBC News"),
            aliases = emptyMap(),
        )
        assertEquals(setOf("bbc/2"), m.localIgnored)
    }

    @Test
    fun `two sections with one name pair up by address first`() {
        val m = matchFeeds(
            local = mapOf("guardian/world" to "The Guardian", "guardian/europe" to "The Guardian"),
            server = mapOf("guardian/europe" to "The Guardian", "feeds.guardian/world" to "The Guardian"),
            aliases = emptyMap(),
        )
        assertEquals("guardian/europe", m.serverAs["guardian/europe"])
        assertEquals("guardian/world", m.serverAs["feeds.guardian/world"])
        assertTrue(m.localIgnored.isEmpty())
    }

    @Test
    fun `new on the server stays new`() {
        val m = matchFeeds(mapOf("a" to "A"), mapOf("a" to "A", "b" to "B"), emptyMap())
        assertEquals("b", m.serverAs["b"])
    }

    @Test
    fun `titles compare without case, spacing or trailing marks`() {
        assertEquals(titleKey("The  Verge -"), titleKey("the verge"))
        assertEquals("", titleKey("   "))
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()
    private val service = source("manager/sync/service/GoogleReaderService.kt")

    @Test
    fun `one account sync at a time`() {
        assertTrue(service.contains("return accountLock.withLock { syncLocked(auth, forceNetwork) }"))
        assertTrue(service.contains("private val accountLock = Mutex()"))
    }

    @Test
    fun `being stopped is not failing`() {
        val body = service.substring(service.indexOf("private suspend fun syncLocked("))
        val cancel = body.indexOf("} catch (e: CancellationException) {\n            // Android stopping the work")
        assertTrue(cancel >= 0)
        assertTrue("before the catch-all", cancel < body.indexOf("} catch (t: Throwable) {"))
        assertTrue(body.substring(cancel).substringBefore("} catch (t: Throwable)").contains("throw e"))
    }

    @Test
    fun `the first match is small, capped, and kept as it goes`() {
        assertTrue(service.contains("const val MAP_FIRST_WINDOW_MS = 2 * DAY_MS"))
        assertTrue(service.contains("while (pages < MAP_MAX_PAGES) {"))
        val map = service.substring(service.indexOf("private suspend fun mapRemoteIds(")).substringBefore("\n    }\n")
        val loop = map.substring(map.indexOf("while (pages < MAP_MAX_PAGES)"))
        assertTrue("matched per page", loop.substringBefore("if (next == null").contains("articles.attachRemoteId(link, remoteId)"))
        assertTrue("and the next match starts from here", map.contains("GoogleReaderState.setMappedAt(context, startedAt)\n        Log.i"))
    }

    @Test
    fun `feeds are compared in Whisper's keys, with the pairings kept`() {
        assertTrue(service.contains("val plan = planSubscriptions(localKeys, remoteAs.keys, lastLocal, everOnServer)"))
        assertTrue(service.contains("val localKeys = localByKey.keys - match.localIgnored"))
        assertTrue(service.contains("(oldAliases + match.newAliases).filterKeys { it in remoteByKey }"))
    }

    @Test
    fun `extra copies go, the oldest stays, and only for the filter that is sure`() {
        val vm = source("viewmodels/SourceListViewModel.kt")
        assertTrue(vm.contains("_duplicateGroups.value.flatMap { it.ids.sorted().drop(1) }.toSet()"))
        val page = source("ui/pages/SourceListPage.kt")
        assertTrue(page.contains("onRemoveExtra = if (sameSite) null else viewModel::removeDuplicateCopies,"))
    }
}
