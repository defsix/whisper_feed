package com.saulhdev.feeder

import android.os.BatteryManager
import com.saulhdev.feeder.utils.PowerState
import com.saulhdev.feeder.utils.SyncEntry
import com.saulhdev.feeder.utils.SyncHistory
import com.saulhdev.feeder.utils.SyncLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enough recorded to tell every combination apart: network and whether it
 * is metered, plugged in or not and whether charging or held, battery level,
 * Battery Saver and Doze - at the start of every sync and again at its end.
 */
class SyncConditionsRecordTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the power reading names charging, held and on battery apart`() {
        fun p(plugged: Boolean, status: Int, pct: Int) = PowerState(plugged, "USB", status, pct).short()
        assertEquals("USB charging 64%", p(true, BatteryManager.BATTERY_STATUS_CHARGING, 64))
        assertEquals("a Pixel holding at 80% is still plugged in", "USB held 80%", p(true, BatteryManager.BATTERY_STATUS_NOT_CHARGING, 80))
        assertEquals("USB full 100%", p(true, BatteryManager.BATTERY_STATUS_FULL, 100))
        assertEquals("battery 41%", p(false, BatteryManager.BATTERY_STATUS_DISCHARGING, 41))
    }

    @Test
    fun `a run keeps the phone as it ended as well as began`() {
        val started = SyncHistory.start(emptyList(), SyncEntry(1, 0, "scheduled", "running", "Wi-Fi (unmetered), USB charging 70%"))
        val ended = SyncHistory.finish(started, 1, 30, "stopped: came off the charger", "mobile (metered), battery 69%")
        assertEquals("mobile (metered), battery 69%", ended.single().phoneAtEnd)
        assertEquals("Wi-Fi (unmetered), USB charging 70%", ended.single().phone)
        assertEquals(ended, SyncHistory.decode(SyncHistory.encode(ended)))
    }

    @Test
    fun `a line written before the end reading existed still reads`() {
        val old = "1\t9\tscheduled\tok\tplugged in, Wi-Fi"
        val read = SyncHistory.decode(old).single()
        assertEquals("ok", read.outcome)
        assertEquals("", read.phoneAtEnd)
    }

    @Test
    fun `the snapshot carries every condition`() {
        val device = source("utils/DeviceState.kt")
        val snapshot = device.substringAfter("internal fun deviceSnapshot(")
        assertTrue("network and metered", snapshot.contains("networkState(context).short()"))
        assertTrue("power and level", snapshot.contains("power.short()"))
        assertTrue("Battery Saver", snapshot.contains("isPowerSaveMode(context)"))
        assertTrue("Doze", snapshot.contains("isDeviceIdle(context)"))
        val log = source("utils/SyncLog.kt")
        assertEquals("read at the start and at the end", 2, Regex("""deviceSnapshot\(context\)""").findAll(log).count())
    }

    /**
     * Battery Saver pauses the schedule and the panel, and nothing the reader
     * asked for.
     */
    @Test
    fun `Battery Saver pauses automatic syncs only`() {
        assertEquals(setOf(SyncLog.ORIGIN_SCHEDULED, SyncLog.ORIGIN_PANEL), SyncLog.AUTOMATIC_ORIGINS)
        assertFalse(SyncLog.ORIGIN_PULL in SyncLog.AUTOMATIC_ORIGINS)
        val worker = source("manager/sync/FeedSyncer.kt").substringAfter("override suspend fun doWork()")
        val check = worker.indexOf("origin in SyncLog.AUTOMATIC_ORIGINS && isPowerSaveMode(applicationContext)")
        assertTrue("checked", check > 0)
        assertTrue("and before anything is fetched", check < worker.indexOf("syncFeeds("))
        assertTrue("recorded as skipped", worker.contains("\"skipped: Battery Saver\""))
    }
}
