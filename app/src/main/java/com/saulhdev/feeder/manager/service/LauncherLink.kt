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
package com.saulhdev.feeder.manager.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a launcher is using Whisper as its feed, and which one could.
 *
 * There is no way to ask a launcher what it has selected — the preference
 * lives in its process and its data directory — so the only honest signal
 * available is whether one has ever bound to [OverlayService] while this
 * process has been alive. That is a proof of success and not a proof of
 * failure: after a restart the flag reads false until the launcher next asks
 * for the page, which is why the screen says "connected" when it is true and
 * says nothing at all when it is not.
 */
object LauncherLink {

    private val _bound = MutableStateFlow(false)

    /** True from the moment a launcher binds until it lets go. */
    val bound: StateFlow<Boolean> = _bound

    /** True if a launcher has bound at any point since the app started. */
    var everBound: Boolean = false
        private set

    internal fun onBound() {
        _bound.value = true
        everBound = true
    }

    internal fun onUnbound() {
        _bound.value = false
    }

    /**
     * Launchers known to offer a choice of feed provider.
     *
     * Lawnchair is the one that has been tested on a device. The forks
     * inherit the same `FeedBridge`, so they very probably work, and listing
     * them costs nothing — the screen only uses this to decide whether the
     * setup instructions are worth showing at all.
     */
    private val LAUNCHERS = listOf(
        "app.lawnchair",
        "ch.deletescape.lawnchair",
        "ch.deletescape.lawnchair.plah",
        "com.saggitt.omega",
        "app.lawnchair.debug",
    )

    /** The package name of an installed launcher that could use this, if any. */
    fun installedLauncher(context: Context): String? =
        LAUNCHERS.firstOrNull { context.isInstalled(it) }

    /**
     * That launcher's own settings, if it can be reached.
     *
     * Launchers do not publish a deep link to the feed-provider preference, so
     * this opens the launcher's settings and the instructions take it from
     * there. Better than nothing, and honest about being a starting point
     * rather than a shortcut.
     */
    fun settingsIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.takeIf { context.isInstalled(packageName) }

    private fun Context.isInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
