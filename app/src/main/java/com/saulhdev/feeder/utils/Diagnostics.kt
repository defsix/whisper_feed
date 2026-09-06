/*
 * This file is part of 076 Feed
 * Copyright (c) 2026   076 Feed contributors
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

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.SourcesRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Self-service diagnostics, so a problem can be reported from the device alone.
 *
 * This app is developed against a phone with no desktop attached, and adb's
 * wireless route needs a network that is not always there. An app can read its
 * own logcat without any permission, though, so it can collect what would
 * otherwise need a host machine.
 *
 * The report goes to the shared Downloads collection rather than the app's own
 * storage, because app-private files and `Android/data` are both unreadable by
 * other apps on modern Android — Downloads is somewhere a terminal or file
 * manager can actually reach it.
 */
object Diagnostics : KoinComponent {

    private const val LOG_LINE_LIMIT = 2000

    suspend fun collect(context: Context): String = buildString {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        appendLine("076 Feed diagnostics")
        appendLine("Generated: $now")
        appendLine()

        appendLine("== Build ==")
        appendLine("Package:     ${BuildConfig.APPLICATION_ID}")
        appendLine("Version:     ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Build type:  ${BuildConfig.BUILD_TYPE}")
        appendLine()

        appendLine("== Device ==")
        appendLine("Model:       ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android:     ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine()

        appendLine("== State ==")
        // The overlay needs this to launch articles; see UPSTREAM_NOTES.md 3b.
        appendLine("Can draw overlays: ${Settings.canDrawOverlays(context)}")
        runCatching {
            val prefs = get<FeedPreferences>()
            appendLine("Article open mode: ${prefs.articleOpenMode.getValue()}")
            appendLine("Overlay theme:     ${prefs.overlayTheme.getValue()}")
            // Two different things, easily confused: categoryFilter is what the
            // chips select (include), tagsFilter is what the filter sheet mutes
            // (exclude). Reporting only one of them is how a misleading report
            // gets written.
            appendLine("Selected categories: ${prefs.categoryFilter.getValue()}")
            appendLine("Muted tags:          ${prefs.tagsFilter.getValue()}")
            appendLine("Muted sources:       ${prefs.sourcesFilter.getValue()}")
        }.onFailure { appendLine("Preferences unavailable: $it") }

        // Worth reporting explicitly: an empty tag list is the ordinary reason the
        // category chip row renders nothing, and it is indistinguishable from a
        // failure without looking.
        runCatching {
            val tags = get<SourcesRepository>().getAllTagsFlow().first()
            appendLine("Source tags:       ${tags.filter { it.isNotBlank() }}")
            appendLine("Untagged sources:  ${tags.any { it.isBlank() }}")
        }.onFailure { appendLine("Sources unavailable: $it") }
        appendLine()

        appendLine("== Log (last $LOG_LINE_LIMIT lines, this app only) ==")
        appendLine(readOwnLogcat())
    }

    /**
     * Reads this app's logcat. Since Android 4.1 a process only sees its own
     * entries, which is exactly the scope wanted and needs no permission.
     */
    private fun readOwnLogcat(): String = runCatching {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "time", "-t", LOG_LINE_LIMIT.toString())
        )
        process.inputStream.bufferedReader().use { it.readText() }
            .ifBlank { "(no log entries)" }
    }.getOrElse { "Could not read logcat: $it" }

    /**
     * Writes the report to Downloads and returns a human-readable location, or
     * null if it could not be written.
     */
    suspend fun export(context: Context): String? {
        val report = collect(context)
        val name = "076feed-diagnostics-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, name, report)
            } else {
                writeToLegacyDownloads(name, report)
            }
        }.getOrNull()
    }

    private fun writeViaMediaStore(context: Context, name: String, report: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore rejected the insert")

        resolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
            ?: error("Could not open $uri for writing")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return "Download/$name"
    }

    @Suppress("DEPRECATION") // The permission is declared with maxSdkVersion 28.
    private fun writeToLegacyDownloads(name: String, report: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, name)
        file.writeText(report)
        return file.absolutePath
    }
}
