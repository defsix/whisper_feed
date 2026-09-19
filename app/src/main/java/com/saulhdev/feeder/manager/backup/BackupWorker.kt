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
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saulhdev.feeder.data.content.FeedPreferences
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

/**
 * The backup nobody has to remember to take.
 *
 * A manual export is not a backup: it is a thing people mean to do. The whole
 * value of this feature is that it happens without being asked, so the
 * scheduled version is the feature and the button is the reassurance.
 *
 * Daily rather than hourly. A subscription list changes a few times a month at
 * most, and writing an identical file to somebody's cloud storage every hour
 * would be rude to their storage and their battery for no gain.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    private val store: BackupStore by inject(BackupStore::class.java)

    override suspend fun doWork(): Result {
        val destination = prefs.backupFolder.getValue().takeIf { it.isNotEmpty() }
            ?: return Result.success()

        return when (val outcome = store.backUp(destination.toUri())) {
            is BackupStore.Result.Written -> {
                prefs.backupLastRun.setValue(outcome.at.toString())
                // Any earlier stoppage is over, and a warning that outlives
                // the problem teaches people to ignore warnings.
                if (prefs.backupStoppedAt.getValue() != 0L) {
                    prefs.backupStoppedAt.setValue(0L)
                }
                Result.success()
            }

            // The folder is gone, or permission was revoked. Retrying on a
            // schedule would not fix it and the reader has to choose again, so
            // this stops rather than failing daily in the background for ever.
            //
            // Recorded on the way out. Stopping quietly was the whole problem:
            // nothing said backups had ended, and the only way to find out was
            // to open the backup screen and read a line about the folder.
            BackupStore.Result.NoDestination -> {
                if (prefs.backupStoppedAt.getValue() == 0L) {
                    prefs.backupStoppedAt.setValue(System.currentTimeMillis())
                }
                Result.success()
            }

            else -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "WhisperBackup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        // Unmetered rather than merely connected: an OPML file
                        // is small, but writing to cloud storage on someone's
                        // mobile data without being asked is not this app's
                        // decision to make.
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
