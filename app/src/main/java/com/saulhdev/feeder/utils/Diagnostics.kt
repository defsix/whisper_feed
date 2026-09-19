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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.ArticleRepository
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

    /** Enough to see the shape of a feed list without printing forty lines. */
    private const val MAX_SOURCES_LISTED = 20

    suspend fun collect(context: Context): String = buildString {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        appendLine("Whisper diagnostics")
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

        // The counts, which are what an empty feed actually turns on. Without
        // them a report of "the feed is empty" cannot distinguish an empty
        // database from a full one being filtered to nothing, and those have
        // nothing in common but the symptom.
        appendLine("== Counts ==")
        runCatching {
            val sources = get<SourcesRepository>().getAllSources()
            appendLine("Sources:           ${sources.size}")
            appendLine("Enabled:           ${sources.count { it.isEnabled }}")
            sources.take(MAX_SOURCES_LISTED).forEach {
                appendLine(
                    "  ${if (it.isEnabled) "on " else "off"} " +
                            "${it.title.take(28).padEnd(28)} last sync ${it.lastSync}"
                )
            }
            if (sources.size > MAX_SOURCES_LISTED) {
                appendLine("  … and ${sources.size - MAX_SOURCES_LISTED} more")
            }
        }.onFailure { appendLine("Source counts unavailable: $it") }

        runCatching {
            val repo = get<ArticleRepository>()
            appendLine("Articles stored:   ${repo.countAll()}")
            appendLine("Unread:            ${repo.countUnread().first()}")
            appendLine("Bookmarked:        ${repo.getBookmarkedFeedItems().first().size}")
        }.onFailure { appendLine("Article counts unavailable: $it") }
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
            // "*:V" explicitly, rather than relying on the default filter:
            // the gate trace and anything else written at DEBUG is the point
            // of reading this at all, and a default of *:I would drop it.
            arrayOf("logcat", "-d", "-v", "time", "-t", LOG_LINE_LIMIT.toString(), "*:V")
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
        val name = "whisper-diagnostics-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, name, report)
            } else {
                writeToLegacyDownloads(name, report)
            }
        }.getOrNull()
    }

    // Guarded at the call site, and said again here so the compiler and lint
    // can both see it rather than taking the branch on trust.
    @RequiresApi(Build.VERSION_CODES.Q)
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

    /**
     * Bundles the report and hands it to the share sheet.
     *
     * The export above writes to Downloads, which is right for the developer's
     * own phone and useless to a tester: it leaves them to find a file manager,
     * locate the file and attach it themselves, and most people stop before
     * the end of that sentence. This puts the same report into whichever app
     * they already use to talk to us.
     *
     * @param note what the tester typed. It goes at the top of the report
     *   rather than in the message body, because a message body is easy to
     *   lose track of and the file is the thing that gets kept.
     */
    suspend fun share(context: Context, note: String) {
        val report = buildString {
            if (note.isNotBlank()) {
                appendLine("== What happened ==")
                appendLine(note.trim())
                appendLine()
            }
            append(collect(context))
        }

        val name = "whisper-report-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        // The provider is scoped to exactly this directory; see file_paths.xml.
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        // Yesterday's reports are of no use to anyone and would otherwise
        // accumulate for the life of the install.
        dir.listFiles()?.forEach { it.delete() }

        val file = File(dir, name)
        file.writeText(report)

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Whisper ${BuildConfig.VERSION_NAME} — ${Build.MANUFACTURER} ${Build.MODEL}",
            )
            if (note.isNotBlank()) putExtra(Intent.EXTRA_TEXT, note.trim())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(send, context.getString(R.string.report_problem))
        // Settings is an activity, but this is called from a preference's
        // onClick with the application context in scope for other call sites.
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    @Suppress("DEPRECATION") // The permission is declared with maxSdkVersion 28.
    private fun writeToLegacyDownloads(name: String, report: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, name)
        file.writeText(report)
        return file.absolutePath
    }
}
