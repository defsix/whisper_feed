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

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo

/**
 * What the phone is doing, as far as a sync cares: power and network.
 *
 * Shared by the diagnostics report, which describes the phone now, and the
 * sync record, which describes it at the moment each sync started. One reading
 * for both, so the two can never disagree about what "plugged in" means.
 */

/**
 * Plugged in or not, charging or held, and the level.
 *
 * Read from the sticky battery broadcast, which needs no permission and no
 * receiver - it hands back the last state the system announced.
 */
internal class PowerState(
    val pluggedIn: Boolean,
    val source: String,
    val status: Int,
    val percent: Int,
) {
    /** Roughly the system's own "battery low": under 15% and on battery. */
    val low: Boolean get() = !pluggedIn && percent in 0 until LOW_BATTERY_PERCENT

    fun describe(): String {
        val level = if (percent >= 0) "battery $percent%" else "battery unknown"
        if (!pluggedIn) return "on battery, $level"
        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            // Plugged in and not taking charge: adaptive charging or a charge
            // limit holding it. Named, because it reads as "unplugged" to
            // anything that only asks whether it is charging.
            else -> "plugged in but held, not charging"
        }
        return "plugged in ($source), $state, $level"
    }

    /** The two words the sync record keeps. */
    fun short(): String = if (pluggedIn) "plugged in" else "on battery"
}

/** Where Android calls the battery low, near enough; the exact line is the device's. */
private const val LOW_BATTERY_PERCENT = 15

internal fun powerState(context: Context): PowerState = runCatching {
    val battery = ContextCompat.registerReceiver(
        context,
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    PowerState(
        pluggedIn = plugged != 0,
        source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "mains"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "other"
        },
        status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1,
        percent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
    )
}.getOrDefault(PowerState(pluggedIn = false, source = "unknown", status = -1, percent = -1))

internal fun isUnmetered(context: Context): Boolean = runCatching {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
}.getOrDefault(false)

/** "Wi-Fi", "mobile data" or "offline", as the sync record puts it. */
internal fun networkKind(context: Context): String = runCatching {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?: return@runCatching "offline"
    when {
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "Wi-Fi"
        else -> "mobile data"
    }
}.getOrDefault("unknown")

/** WorkManager's stop reason, in words. */
internal fun stopReasonName(reason: Int): String = when (reason) {
    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "cancelled by the app"
    WorkInfo.STOP_REASON_PREEMPT -> "pre-empted by other work"
    WorkInfo.STOP_REASON_TIMEOUT -> "ran out of time"
    WorkInfo.STOP_REASON_DEVICE_STATE -> "device state changed"
    WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "battery went low"
    WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "came off the charger"
    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "network changed or dropped"
    WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "device stopped being idle"
    WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "storage ran low"
    WorkInfo.STOP_REASON_QUOTA -> "out of background quota"
    WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "background use restricted"
    WorkInfo.STOP_REASON_APP_STANDBY -> "app put in standby"
    WorkInfo.STOP_REASON_USER -> "stopped by the user"
    WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "system busy"
    WorkInfo.STOP_REASON_UNKNOWN -> "no reason given"
    else -> "reason code $reason"
}
