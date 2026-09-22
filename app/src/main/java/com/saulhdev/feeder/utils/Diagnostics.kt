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
import android.widget.Toast
import androidx.core.content.FileProvider
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

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

        appendLine()
        appendLine("== Images ==")
        // Since this launch, not since the last five-second window, which is
        // all the log below can show. A picture that failed to appear an hour
        // into a session is still counted here. See FeedTrace.imageTally.
        appendLine(FeedTrace.imageTally())

        // Worth reporting explicitly: an empty tag list is the ordinary reason the
        // category chip row renders nothing, and it is indistinguishable from a
        // failure without looking.
        runCatching { withTimeout(SECTION_TIMEOUT_MS) {
            val tags = get<SourcesRepository>().getAllTagsFlow().first()
            appendLine("Source tags:       ${tags.filter { it.isNotBlank() }}")
            appendLine("Untagged sources:  ${tags.any { it.isBlank() }}")
        } }.onFailure { appendLine("Sources unavailable: $it") }
        appendLine()

        // The counts, which are what an empty feed actually turns on. Without
        // them a report of "the feed is empty" cannot distinguish an empty
        // database from a full one being filtered to nothing, and those have
        // nothing in common but the symptom.
        appendLine("== Counts ==")
        runCatching { withTimeout(SECTION_TIMEOUT_MS) {
            val sources = get<SourcesRepository>().getAllSources()
            appendLine("Sources:           ${sources.size}")
            appendLine("Enabled:           ${sources.count { it.isEnabled }}")

            // Counted over all of them, not just the ones printed below. A
            // sample of twenty is how the last round of this was noticed at
            // all, and only because six rows happened to share a timestamp;
            // the totals say it outright.
            val never = sources.count { it.isEnabled && it.lastSync.toEpochMilliseconds() <= 0L }
            val failing = sources.count { it.isEnabled && it.consecutiveFailures > 0 }
            appendLine("Never synced:      $never")
            appendLine("Currently failing: $failing")

            // Worst first: a report is read from the top, and the feeds that
            // are not working are the reason anybody is reading it.
            val ordered = sources.sortedWith(
                compareByDescending<com.saulhdev.feeder.data.db.models.Feed> {
                    it.isEnabled && it.lastSync.toEpochMilliseconds() <= 0L
                }.thenByDescending { it.consecutiveFailures }
            )
            ordered.take(MAX_SOURCES_LISTED).forEach {
                val last = if (it.lastSync.toEpochMilliseconds() <= 0L) "never" else "${it.lastSync}"
                val fails = if (it.consecutiveFailures > 0) {
                    " fails ${it.consecutiveFailures} since ${it.failingSince}"
                } else ""
                appendLine(
                    "  ${if (it.isEnabled) "on " else "off"} " +
                            "${it.title.take(28).padEnd(28)} last sync $last$fails"
                )
            }
            if (sources.size > MAX_SOURCES_LISTED) {
                appendLine("  … and ${sources.size - MAX_SOURCES_LISTED} more")
            }
        } }.onFailure { appendLine("Source counts unavailable: $it") }

        runCatching { withTimeout(SECTION_TIMEOUT_MS) {
            val repo = get<ArticleRepository>()
            appendLine("Articles stored:   ${repo.countAll()}")
            appendLine("Unread:            ${repo.countUnread().first()}")
            appendLine("Bookmarked:        ${repo.getBookmarkedFeedItems().first().size}")
        } }.onFailure { appendLine("Article counts unavailable: $it") }
        appendLine()

        appendLine("== Log (last $LOG_LINE_LIMIT lines, this app only) ==")
        appendLine(readOwnLogcat())
    }

    /**
     * Reads this app's logcat. Since Android 4.1 a process only sees its own
     * entries, which is exactly the scope wanted and needs no permission.
     */
    /**
     * How long any one database section may take before the report goes on
     * without it. A section that times out says so and the rest still arrives:
     * a report that is missing its counts is still a report, and one that
     * never finishes is nothing at all.
     */
    private const val SECTION_TIMEOUT_MS = 4_000L

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
     * Whether a report is being put together right now.
     *
     * Both buttons said nothing until the report was finished, and finishing
     * waits on the database - which, during a sync, is the busiest thing in
     * the app. So a tap looked like nothing at all, a second tap started a
     * second report behind the first, and the whole thing read as a button
     * that did not work. The one moment a diagnostics report is most wanted,
     * the app misbehaving, is the moment it was slowest to arrive.
     */
    private val collecting = AtomicBoolean(false)

    /**
     * Says the report has started, or refuses a second one. False when one is
     * already on its way, so the caller does nothing rather than queue another.
     */
    fun begin(context: Context): Boolean {
        if (!collecting.compareAndSet(false, true)) return false
        Toast.makeText(context, R.string.diagnostics_collecting, Toast.LENGTH_SHORT).show()
        return true
    }

    fun end() {
        collecting.set(false)
    }

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

    /**
     * Writes the stack trace to Downloads when the app dies unexpectedly.
     *
     * A crash on launch is the one failure this app had no way to report. The
     * diagnostics above are collected from a running app by somebody pressing
     * a button, and an app that dies before its first frame offers neither —
     * so the only evidence was a host machine running adb, which is exactly
     * what this project does not assume anybody has. Three release cycles were
     * spent guessing at a crash for want of one stack trace.
     *
     * Deliberately small. It runs inside a process that is already dying, so
     * it collects what is already in memory — the exception, the build, the
     * device — and writes it. No database, no preferences, no logcat: each of
     * those can block or throw, and a crash reporter that crashes reports
     * nothing.
     *
     * The previous handler is always called afterwards. Swallowing it would
     * leave the process hung instead of dying, and would take away the system
     * dialog that is the only sign to the reader that anything happened.
     */
    fun installCrashLog(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val report = buildString {
            appendLine("Whisper crash")
            appendLine("When:        ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Version:     ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type:  ${BuildConfig.BUILD_TYPE}")
            appendLine("Device:      ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android:     ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread:      ${thread.name}")
            appendLine()
            appendLine("== Stack trace ==")
            appendLine(error.stackTraceToString())

            // Printed separately as well as inside the trace above, because a
            // cause chain is where the answer usually is and `Caused by` is
            // easy to lose in a hundred lines of framework frames.
            var cause = error.cause
            var depth = 0
            while (cause != null && depth < 8) {
                appendLine()
                appendLine("== Cause ${depth + 1} ==")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
                depth++
            }
        }

        val name = "whisper-crash-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, name, report)
        } else {
            writeToLegacyDownloads(name, report)
        }
    }
}
