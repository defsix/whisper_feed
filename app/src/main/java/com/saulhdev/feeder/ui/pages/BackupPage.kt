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
package com.saulhdev.feeder.ui.pages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.backup.BackupStore
import com.saulhdev.feeder.manager.backup.BackupWorker
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowUp
import com.saulhdev.feeder.ui.icons.phosphor.Nut
import com.saulhdev.feeder.ui.icons.phosphor.PaintRoller
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

/**
 * Choosing somewhere to keep a copy of the subscription list.
 *
 * The screen leads with why rather than how, because "back up your feeds" only
 * lands once somebody has noticed that the list exists in one place and took
 * years to build. Everything else here is one tap.
 */
@Composable
fun BackupPage(
    prefs: FeedPreferences = koinInject(),
    store: BackupStore = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val folder by prefs.backupFolder.get().collectAsState(initial = "")
    val lastRun by prefs.backupLastRun.get().collectAsState(initial = "")
    var message by remember { mutableStateOf<String?>(null) }

    val writable = remember(folder) {
        folder.isNotEmpty() && store.canWrite(folder.toUri())
    }

    val chooseFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        store.remember(uri)
        scope.launch {
            prefs.backupFolder.setValue(uri.toString())
            BackupWorker.schedule(context)
            // Written immediately rather than waiting for the first scheduled
            // run: somebody who has just chosen a folder wants to see a file
            // appear in it, and a day of nothing happening reads as a setting
            // that did not take.
            report(store.backUp(uri), context) { message = it }
            prefs.backupLastRun.setValue(System.currentTimeMillis().toString())
        }
    }

    val chooseSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { report(store.restoreSettings(uri), context) { message = it } }
    }

    val chooseBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { report(store.restore(uri), context) { message = it } }
    }

    ViewWithActionBar(
        title = stringResource(R.string.pref_backup),
        largeTitle = true,
        showBackButton = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.backup_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                Text(
                    text = when {
                        folder.isEmpty() -> stringResource(R.string.backup_none)
                        !writable -> stringResource(R.string.backup_lost)
                        lastRun.isEmpty() -> stringResource(R.string.backup_never)
                        else -> stringResource(
                            R.string.backup_last,
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(lastRun.toLongOrNull() ?: 0L)),
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (folder.isNotEmpty() && !writable) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            item {
                ActionButton(
                    text = stringResource(
                        if (folder.isEmpty()) R.string.backup_choose else R.string.backup_change
                    ),
                    icon = Phosphor.PaintRoller,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    onClick = { chooseFolder.launch(null) },
                )
            }

            if (folder.isNotEmpty() && writable) {
                item {
                    OutlinedActionButton(
                        text = stringResource(R.string.backup_now),
                        icon = Phosphor.CloudArrowUp,
                        modifier = Modifier.fillMaxWidth(),
                        positive = true,
                        onClick = {
                            scope.launch {
                                report(store.backUp(folder.toUri()), context) { message = it }
                                prefs.backupLastRun.setValue(
                                    System.currentTimeMillis().toString()
                                )
                            }
                        },
                    )
                }
            }

            item {
                OutlinedActionButton(
                    text = stringResource(R.string.backup_restore_settings),
                    icon = Phosphor.Nut,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    // Chosen separately from the feeds on purpose: adding
                    // somebody's subscriptions to a new phone is additive and
                    // safe, and overwriting every setting on a phone already
                    // arranged the way they like it is not.
                    onClick = { chooseSettings.launch(arrayOf("application/json", "*/*")) },
                )
            }

            item {
                OutlinedActionButton(
                    text = stringResource(R.string.backup_restore),
                    icon = Phosphor.CloudArrowDown,
                    modifier = Modifier.fillMaxWidth(),
                    positive = true,
                    // Any OPML, not only one Whisper wrote: somebody arriving
                    // from another reader has an export of their own, and this
                    // is the same file.
                    onClick = { chooseBackup.launch(arrayOf("*/*")) },
                )
            }

            message?.let {
                item {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.backup_what),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/** Turns an outcome into the one sentence the screen shows. */
private fun report(
    result: BackupStore.Result,
    context: android.content.Context,
    show: (String) -> Unit,
) {
    show(
        when (result) {
            is BackupStore.Result.Written ->
                context.getString(R.string.backup_written, result.name)

            is BackupStore.Result.SettingsRestored ->
                context.getString(R.string.backup_settings_restored, result.count)

            is BackupStore.Result.Restored ->
                if (result.feeds > 0) {
                    context.getString(R.string.backup_restored, result.feeds)
                } else {
                    context.getString(R.string.backup_restored_none)
                }

            is BackupStore.Result.FolderRestored -> context.getString(
                R.string.backup_folder_restored, result.feeds, result.settings
            )

            BackupStore.Result.NothingFound -> context.getString(R.string.backup_nothing_found)
            BackupStore.Result.NoDestination -> context.getString(R.string.backup_lost)
            is BackupStore.Result.Failed -> context.getString(R.string.backup_failed)
        }
    )
}
