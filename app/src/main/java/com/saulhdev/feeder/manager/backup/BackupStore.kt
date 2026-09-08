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
package com.saulhdev.feeder.manager.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.documentfile.provider.DocumentFile
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.models.OPMLParser
import com.saulhdev.feeder.manager.models.SourceToRoom
import com.saulhdev.feeder.manager.models.writeOutputStream
import com.saulhdev.feeder.manager.sync.requestFeedSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeping a copy of the subscription list somewhere that is not this phone.
 *
 * **Through the Storage Access Framework, not the Google Drive API**, and the
 * difference is worth the paragraph. Talking to Drive directly means an OAuth
 * client registered against this app's package and signing certificate, which
 * is a step the project cannot take for its users and which would have to be
 * repeated for the debug build, the release build, and anyone forking this.
 * Picking a folder needs none of that: Drive ships a `DocumentsProvider`, the
 * system's own picker offers it alongside everything else, and a persistable
 * permission means the app can keep writing there afterwards without asking
 * again.
 *
 * It is also simply better. Dropbox, OneDrive, Nextcloud and an SD card all
 * appear in the same picker, so this is not a Google feature that happens to
 * be spelled generically — it works with wherever the reader already keeps
 * things. And there is no Google dependency to declare, which matters for a
 * build that goes to F-Droid.
 *
 * What it writes is exactly what the existing export writes: the same OPML,
 * from the same code. This is a destination, not a second format.
 */
class BackupStore(
    private val context: Context,
    private val sources: SourcesRepository,
    private val dataStore: DataStore<Preferences>,
) {

    /** What happened, in terms the settings screen can say out loud. */
    sealed interface Result {
        data class Written(val name: String, val at: Long) : Result
        data class Restored(val feeds: Int) : Result
        data class SettingsRestored(val count: Int) : Result
        data object NoDestination : Result
        data class Failed(val cause: Throwable?) : Result
    }

    /**
     * Writes the current subscription list to the chosen folder.
     *
     * One file, overwritten, rather than a dated series. A backup that
     * accumulates is a folder the reader has to tidy, and the second-newest
     * copy of a subscription list has never once been the one somebody wanted.
     */
    suspend fun backUp(treeUri: Uri?): Result = withContext(Dispatchers.IO) {
        if (treeUri == null) return@withContext Result.NoDestination
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.NoDestination
            if (!tree.canWrite()) return@withContext Result.NoDestination

            // Replaced rather than appended to: DocumentFile has no truncate,
            // so an existing shorter file would otherwise keep the tail of the
            // longer one it replaced and produce invalid XML.
            tree.findFile(FILE_NAME)?.delete()
            val file = tree.createFile(MIME_TYPE, FILE_NAME)
                ?: return@withContext Result.Failed(null)

            val byTag = sources.getAllSources()
                .groupBy { it.tags.firstOrNull().orEmpty() }

            context.contentResolver.openOutputStream(file.uri)?.use { out ->
                writeOutputStream(out, byTag)
            } ?: return@withContext Result.Failed(null)

            // Beside it, not inside it: the OPML is an interchange format and
            // has to stay one. See SettingsBackup.
            tree.findFile(SettingsBackup.FILE_NAME)?.delete()
            tree.createFile("application/json", SettingsBackup.FILE_NAME)?.let { settingsFile ->
                context.contentResolver.openOutputStream(settingsFile.uri)?.use { out ->
                    out.write(SettingsBackup.export(dataStore).toByteArray())
                }
            }

            Log.i(TAG, "Wrote backup to ${file.uri}")
            Result.Written(FILE_NAME, System.currentTimeMillis())
        } catch (t: Throwable) {
            Log.e(TAG, "Backup failed", t)
            Result.Failed(t)
        }
    }

    /**
     * Reads a backup back in.
     *
     * Additive, like the OPML import it reuses: a feed already subscribed is
     * left alone rather than duplicated, and nothing local is removed. A
     * restore that deleted whatever the file did not mention would be a
     * different and much more dangerous operation than the word suggests.
     */
    suspend fun restore(fileUri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val before = sources.getAllSources().size
            val parser = OPMLParser(SourceToRoom())
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                parser.parseInputStream(stream)
            } ?: return@withContext Result.Failed(null)

            val after = sources.getAllSources().size
            requestFeedSync()
            Result.Restored(after - before)
        } catch (t: Throwable) {
            Log.e(TAG, "Restore failed", t)
            Result.Failed(t)
        }
    }

    /**
     * Puts the settings back from a settings file.
     *
     * Separate from restoring the feeds, and separately chosen, because the two
     * are different in kind. Adding somebody's subscriptions to a new phone is
     * additive and safe; overwriting every preference on a phone already set up
     * the way they like it is not, and one button doing both would eventually
     * do the second to somebody who wanted the first.
     */
    suspend fun restoreSettings(fileUri: Uri): Result = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(fileUri)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.Failed(null)
            Result.SettingsRestored(SettingsBackup.import(dataStore, json))
        } catch (t: Throwable) {
            Log.e(TAG, "Settings restore failed", t)
            Result.Failed(t)
        }
    }

    /** Keeps the folder writable across restarts. */
    fun remember(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    /** Whether the folder chosen earlier can still be written to. */
    fun canWrite(treeUri: Uri?): Boolean {
        if (treeUri == null) return false
        return runCatching {
            DocumentFile.fromTreeUri(context, treeUri)?.canWrite() == true
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "Backup"
        const val FILE_NAME = "whisper-subscriptions.opml"
        const val MIME_TYPE = "text/x-opml"
    }
}
