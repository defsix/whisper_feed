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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.glance.GlanceState
import com.saulhdev.feeder.manager.glance.weatherLook
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Every chip is this tall, whatever it contains. */
private val CHIP_HEIGHT = 76.dp

/** The supplied artwork is rendered at this size; see docs/brand/08_icons. */
private val ICON_SIZE = 32.dp

private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The status strip above the category filters.
 *
 * Deliberately separate from [CategoryChipRow] even though both are rows of
 * rounded things: those chips are controls that change what the feed shows,
 * these are read-only status. Mixing them would make the row's behaviour
 * unguessable — half of it filters, half of it does nothing when tapped.
 *
 * Laid out as three equal columns rather than a scrolling row. A scrolling row
 * sized each chip to its own content, so a long place name made the weather
 * chip half again as wide as the others and its extra line of text made it
 * taller too — the row read as three unrelated boxes. Fixed thirds and one
 * height means the strip reads as a single unit, and the cost is that long
 * place names truncate, which is the right trade for a glanceable row.
 */
@Composable
fun GlanceRow(
    state: GlanceState,
    onSetLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.enabled) return

    val weather = state.weather

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (weather != null) {
            val look = weatherLook(weather.weatherCode, weather.isDay)
            // Condition on top and the place underneath, not the other way
            // round: a chip is a third of the screen wide, and place names run
            // long ("Armação de Pêra" is 90dp against a 71dp column). Putting
            // the place on the line that matters least means the truncation
            // lands where it costs least.
            GlanceChip(
                label = stringResource(look.labelRes),
                value = "${weather.temperatureC.roundToInt()}°",
                caption = weather.place.substringBefore(","),
                iconRes = look.iconRes,
            )
            // After dark, today's sunset is behind us and repeating it is
            // stale — the next thing that happens is sunrise, so the chip
            // becomes that, artwork and label together.
            val sunUp = weather.isDay
            val next = if (sunUp) weather.nextSunset else weather.nextSunrise
            GlanceChip(
                label = stringResource(
                    if (sunUp) R.string.glance_sunset else R.string.glance_sunrise
                ),
                value = next?.format(HOUR_MINUTE) ?: "—",
                iconRes = if (sunUp) R.drawable.ic_glance_sunset
                else R.drawable.ic_glance_sunrise,
            )
        } else {
            // The row is on but has nothing to show without a place, so the chip
            // is the way to fix that rather than a dead end. It spans the two
            // slots the weather and sunset chips would occupy.
            GlanceChip(
                label = stringResource(R.string.pref_glance_place),
                value = stringResource(R.string.glance_set_location),
                iconRes = R.drawable.ic_glance_location,
                onClick = onSetLocation,
                weight = 2f,
            )
        }

        GlanceChip(
            label = stringResource(R.string.glance_read_today),
            value = state.readToday.toString(),
            iconRes = R.drawable.ic_glance_articles_read,
        )
    }
}

/**
 * One chip. Label above, value below, icon in a tinted circle on the right.
 *
 * [caption] is optional and the chip is the same height without it, so a chip
 * that has a third line does not push its neighbours around.
 */
@Composable
private fun RowScope.GlanceChip(
    label: String,
    value: String,
    @DrawableRes iconRes: Int,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    weight: Float = 1f,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val shape = RoundedCornerShape(20.dp)
    val chipModifier = Modifier
        .weight(weight)
        .height(CHIP_HEIGHT)

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

            Spacer(Modifier.size(4.dp))

            // Drawn, not tinted: these are full-colour illustrations, so the
            // tinted circular badge that suited a monochrome symbol is gone and
            // the artwork sits directly on the chip.
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
    }

    if (onClick != null) {
        Card(onClick = onClick, modifier = chipModifier, shape = shape, colors = colors) { content() }
    } else {
        Card(modifier = chipModifier, shape = shape, colors = colors) { content() }
    }
}
