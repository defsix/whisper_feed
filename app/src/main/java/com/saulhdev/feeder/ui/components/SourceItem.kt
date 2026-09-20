/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
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

package com.saulhdev.feeder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import androidx.compose.material3.IconButton
import com.saulhdev.feeder.ui.icons.phosphor.HeartStraight
import com.saulhdev.feeder.ui.icons.phosphor.HeartStraightFill
import com.saulhdev.feeder.ui.icons.phosphor.WifiHigh
import com.saulhdev.feeder.data.db.models.Feed

@Composable
fun SourceItem(
    source: Feed,
    modifier: Modifier = Modifier,
    onSwitch: (Feed) -> Unit = {},
    onClick: (Feed) -> Unit = {},
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (Feed) -> Unit = {},
    /**
     * Long-press while a selection is already running.
     *
     * Separate from [onLongClick] because the two gestures mean different
     * things once a selection exists: a tap adds or removes this one source,
     * a long-press takes the whole run back to the last one picked. That is
     * the convention every file manager and mail app uses, and it is the
     * difference between filing thirty imported feeds in two gestures and
     * doing it in thirty taps. Defaults to [onLongClick], so a caller that
     * has no range behaviour keeps the plain toggle.
     */
    onExtendSelection: ((Feed) -> Unit)? = null,
    staleSince: Long = 0L,
    /** Whether this source is one of the few kept at the top of the list. */
    favourite: Boolean = false,
    /** Null hides the control entirely, for the screens that have no list to top. */
    onFavourite: ((Feed) -> Unit)? = null,
) {
    val extend = onExtendSelection ?: onLongClick
    val (isEnabled, enable) = remember(source.isEnabled) {
        mutableStateOf(source.isEnabled)
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerLowest, label = "backgroundColor"
    )

    // A source that has not synced for days looks identical to one that is
    // simply quiet, so say which it is. The threshold is passed in rather than
    // computed here: "now" in a composable would be captured at composition and
    // then drift.
    val isStale = staleSince > 0L && source.isEnabled &&
            source.lastSync.toEpochMilliseconds() < staleSince

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = { if (selectionMode) onLongClick(source) else onClick(source) },
                onLongClick = { if (selectionMode) extend(source) else onLongClick(source) },
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else backgroundColor,
        ),
        overlineContent = {
            Text(
                text = source.url.toString(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        headlineContent = {
            Text(text = source.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val tags = source.tags
            if (tags.isNotEmpty() || isStale) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isStale) {
                        Icon(
                            imageVector = Phosphor.WifiHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.source_not_updating),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (tags.isNotEmpty()) {
                        Text(
                            text = tags.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onLongClick(source) })
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onFavourite != null) {
                        IconButton(onClick = { onFavourite(source) }) {
                            Icon(
                                imageVector = if (favourite) Phosphor.HeartStraightFill
                                else Phosphor.HeartStraight,
                                contentDescription = stringResource(
                                    if (favourite) R.string.source_unfavourite
                                    else R.string.source_favourite
                                ),
                                tint = if (favourite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = isEnabled,
                        colors = SwitchDefaults.colors(uncheckedBorderColor = Color.Transparent),
                        onCheckedChange = {
                            enable(!isEnabled)
                            onSwitch(source)
                        }
                    )
                }
            }
        }
    )
}

