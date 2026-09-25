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
package com.saulhdev.feeder.utils

private const val HOUR_MS = 60 * 60_000L

/** Never call a sync late sooner than this, whatever the interval. */
const val MIN_OVERDUE_MS = 24 * HOUR_MS

/** What the line above the feed says about how current it is. */
sealed interface SyncFreshness {
    /** A sync is running now. */
    data object Updating : SyncFreshness

    /** There are sources, and not one of them has ever been fetched. */
    data object NeverUpdated : SyncFreshness

    /**
     * The newest successful fetch, and whether that is later than the
     * schedule can explain.
     */
    data class Updated(val age: ArticleAge, val overdue: Boolean) : SyncFreshness
}

/**
 * How long without a successful sync before it counts as stuck.
 *
 * Three missed intervals, and never under a day. A schedule that waits for a
 * charger or Wi-Fi can go a working day without either and be doing exactly
 * what it was told; three intervals alone would call a 30-minute schedule
 * stuck after an hour and a half on the bus.
 *
 * [frequencyHours] is the stored setting ("0.5", "1", "6"); anything
 * unreadable falls back to the hourly default rather than to "never late".
 */
fun syncOverdueAfterMs(frequencyHours: String): Long {
    val hours = frequencyHours.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
    return maxOf(MIN_OVERDUE_MS, (3 * hours * HOUR_MS).toLong())
}

/**
 * The freshness line for the feed, or null when there is nothing to say.
 *
 * [lastSyncMs] is the newest `lastSync` among enabled sources: null when
 * there are none (nothing to be fresh or stale about, so no line), zero or
 * less when none has ever been fetched. It moves only on a successful fetch,
 * so a sync that ran and failed everywhere leaves the old age showing, which
 * is the truth about what is on screen.
 */
fun syncFreshness(
    nowMs: Long,
    lastSyncMs: Long?,
    syncing: Boolean,
    overdueAfterMs: Long,
): SyncFreshness? = when {
    lastSyncMs == null -> null
    syncing -> SyncFreshness.Updating
    lastSyncMs <= 0L -> SyncFreshness.NeverUpdated
    else -> SyncFreshness.Updated(
        age = articleAge(nowMs, lastSyncMs),
        overdue = nowMs - lastSyncMs > overdueAfterMs,
    )
}

/** Milliseconds until the minute rolls over, so the line changes on time. */
/**
 * Whether opening the app should sync.
 *
 * When the feed is older than one scheduled interval, and never more often
 * than every quarter of an hour. With background data off, the schedule
 * never gets to run on mobile data, and an afternoon went by with the app
 * opened several times and nothing fetched since lunchtime. Opening the app
 * is the moment Android allows it.
 *
 * Not when syncing is set to manual ("0"), and not with no sources to sync
 * ([newestSyncMs] null).
 */
fun openedSyncDue(nowMs: Long, newestSyncMs: Long?, frequencyHours: String): Boolean {
    val hours = frequencyHours.toDoubleOrNull()?.takeIf { it > 0 } ?: return false
    val newest = newestSyncMs ?: return false
    val interval = maxOf(OPENED_SYNC_MIN_MS, (hours * HOUR_MS).toLong())
    return nowMs - newest >= interval
}

const val OPENED_SYNC_MIN_MS = 15 * 60_000L

/**
 * Whether an automatic sync should give way to blocked background data.
 *
 * Only when Whisper is not on screen. The block is Android's, and it is
 * lifted while Whisper is in the foreground; skipping then as well is how
 * six syncs in one afternoon were skipped while Whisper was open.
 */
fun skipForBlockedData(automatic: Boolean, blocked: Boolean, onScreen: Boolean): Boolean =
    automatic && blocked && !onScreen

fun msUntilNextMinute(nowMs: Long): Long = 60_000L - Math.floorMod(nowMs, 60_000L)
