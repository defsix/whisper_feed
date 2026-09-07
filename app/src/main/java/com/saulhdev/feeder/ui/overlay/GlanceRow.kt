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
package com.saulhdev.feeder.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.glance.GlanceState
import com.saulhdev.feeder.manager.glance.weatherCodeLabel
import kotlin.math.roundToInt

/**
 * The status strip above the category filters.
 *
 * Deliberately separate from [CategoryChipRow] even though both are rows of
 * rounded things: those chips are controls that change what the feed shows,
 * these are read-only status. Mixing them would make the row's behaviour
 * unguessable — half of it filters, half of it does nothing when tapped.
 *
 * Chips are only drawn when they have something to say, so the row is never
 * padded out with placeholders: no location means no weather or sunset chip,
 * and nothing read yet today means no count.
 */
@Composable
fun GlanceRow(
    state: GlanceState,
    onSetLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.enabled) return

    val weather = state.weather
    val hasAnything = weather != null || state.unread > 0 || state.readToday > 0
    if (!hasAnything && state.placeName.isNotBlank()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        if (weather != null) {
            val (emoji, condition) = weatherCodeLabel(weather.weatherCode)
            item {
                GlanceChip(
                    label = weather.place.substringBefore(","),
                    value = "${weather.temperatureC.roundToInt()}°",
                    trailing = emoji,
                    caption = condition,
                )
            }
            weather.sunsetLocal?.let { sunset ->
                item {
                    GlanceChip(
                        label = stringResource(R.string.glance_sunset),
                        value = sunset,
                        trailing = "🌇",
                    )
                }
            }
        } else if (state.placeName.isBlank()) {
            // The row is on but unusable without a place, so it says so and the
            // chip is the way to fix it rather than a dead end.
            item {
                GlanceChip(
                    label = stringResource(R.string.pref_glance_place),
                    value = stringResource(R.string.glance_set_location),
                    trailing = "📍",
                    onClick = onSetLocation,
                )
            }
        }

        if (state.unread > 0) {
            item {
                GlanceChip(
                    label = stringResource(R.string.glance_unread),
                    value = state.unread.toString(),
                    trailing = "📰",
                )
            }
        }
        if (state.readToday > 0) {
            item {
                GlanceChip(
                    label = stringResource(R.string.glance_read_today),
                    value = state.readToday.toString(),
                    trailing = "✅",
                )
            }
        }
    }
}

@Composable
private fun GlanceChip(
    label: String,
    value: String,
    trailing: String,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val shape = RoundedCornerShape(18.dp)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.widthIn(max = 160.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.padding(horizontal = 5.dp))
            Text(text = trailing, style = MaterialTheme.typography.titleLarge, color = Color.Unspecified)
        }
    }

    if (onClick != null) {
        Card(onClick = onClick, shape = shape, colors = colors) { content() }
    } else {
        Card(shape = shape, colors = colors) { content() }
    }
}
