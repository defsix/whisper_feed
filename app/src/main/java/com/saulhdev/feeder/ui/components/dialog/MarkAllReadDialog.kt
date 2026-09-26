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
package com.saulhdev.feeder.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.saulhdev.feeder.R
import com.saulhdev.feeder.viewmodels.MarkReadRange

/**
 * "Mark all as read", asking how far back.
 *
 * Each choice shows how many articles it would mark, counted at the moment
 * the dialog opened; [onConfirm] is handed that same moment so the marking
 * matches the count. A choice with nothing in it cannot be picked.
 */
@Composable
fun MarkAllReadDialog(
    counts: suspend (nowMs: Long) -> Map<MarkReadRange, Int>,
    onConfirm: (MarkReadRange, nowMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val counted by produceState<Map<MarkReadRange, Int>?>(null) { value = counts(now) }
    var selected by remember { mutableStateOf(MarkReadRange.Everything) }
    val chosen = counted?.get(selected) ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mark_read_title)) },
        text = {
            Column {
                MarkReadRange.entries.forEach { range ->
                    val count = counted?.get(range)
                    SingleSelectionListItem(
                        text = stringResource(range.label()),
                        isSelected = range == selected,
                        isEnabled = count != null && count > 0,
                        endWidget = count?.let {
                            {
                                Text(
                                    text = pluralStringResource(R.plurals.mark_read_unread, it, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { selected = range },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen > 0,
                onClick = {
                    onConfirm(selected, now)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.mark_read_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private fun MarkReadRange.label(): Int = when (this) {
    MarkReadRange.Everything -> R.string.mark_read_everything
    MarkReadRange.OlderThanHour -> R.string.mark_read_older_hour
    MarkReadRange.OlderThanDay -> R.string.mark_read_older_day
}
