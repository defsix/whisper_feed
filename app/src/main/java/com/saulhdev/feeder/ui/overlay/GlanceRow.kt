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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.glance.GlanceState
import com.saulhdev.feeder.manager.glance.weatherLook
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Every chip is this tall, whatever it contains. */
private val CHIP_HEIGHT = 84.dp

/**
 * Chip width as a fraction of the screen.
 *
 * Just under a half, so two sit side by side and the third is visibly cut off —
 * which is what tells you the row scrolls. Three equal thirds fitted exactly and
 * therefore looked complete, but left each chip a 71dp text column: everything
 * fitted and nothing had any room.
 */
private const val CHIP_WIDTH_FRACTION = 0.455f

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
 * A scrolling row of equally sized chips.
 *
 * It has been both other things. Sizing each chip to its own content made a
 * long place name half again as wide as its neighbours, with an extra line of
 * text that made it taller too, so the strip read as three unrelated boxes.
 * Fixing that with three equal thirds went too far the other way: everything
 * fitted exactly, which left each chip a 71dp text column and no room at all.
 *
 * One fixed width, just under half the screen, gets both — the chips are
 * identical, and the third being visibly cut off is what tells you the row
 * scrolls.
 */
@Composable
fun GlanceRow(
    state: GlanceState,
    onSetLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.enabled) return

    val weather = state.weather

    val chipWidth = (LocalConfiguration.current.screenWidthDp * CHIP_WIDTH_FRACTION).dp

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (weather != null) {
            val look = weatherLook(weather.weatherCode, weather.isDay)
            // Condition on top and the place underneath. Place names run long,
            // and putting the one that can still overflow on the line that
            // matters least means the truncation lands where it costs least.
            item {
                GlanceChip(
                    width = chipWidth,
                    label = stringResource(look.labelRes),
                    value = "${weather.temperatureC.roundToInt()}°",
                    caption = weather.place.substringBefore(","),
                    iconRes = look.iconRes,
                )
            }
            // After dark, today's sunset is behind us and repeating it is
            // stale — the next thing that happens is sunrise, so the chip
            // becomes that, artwork and label together.
            val sunUp = weather.isDay
            val next = if (sunUp) weather.nextSunset else weather.nextSunrise
            item {
                GlanceChip(
                    width = chipWidth,
                    label = stringResource(
                        if (sunUp) R.string.glance_sunset else R.string.glance_sunrise
                    ),
                    value = next?.format(HOUR_MINUTE) ?: "—",
                    iconRes = if (sunUp) R.drawable.ic_glance_sunset
                    else R.drawable.ic_glance_sunrise,
                )
            }
        } else {
            // The row is on but has nothing to show without a place, so the chip
            // is the way to fix that rather than a dead end.
            item {
                GlanceChip(
                    width = chipWidth,
                    label = stringResource(R.string.pref_glance_place),
                    value = stringResource(R.string.glance_set_location),
                    iconRes = R.drawable.ic_glance_location,
                    onClick = onSetLocation,
                )
            }
        }

        item {
            GlanceChip(
                width = chipWidth,
                label = stringResource(R.string.glance_read_today),
                value = state.readToday.toString(),
                iconRes = R.drawable.ic_glance_articles_read,
            )
        }
    }
}

/**
 * One chip. Label above, value below, icon in a tinted circle on the right.
 *
 * [caption] is optional and the chip is the same height without it, so a chip
 * that has a third line does not push its neighbours around.
 */
@Composable
private fun GlanceChip(
    width: Dp,
    label: String,
    value: String,
    @DrawableRes iconRes: Int,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val shape = RoundedCornerShape(20.dp)
    val chipModifier = Modifier
        .width(width)
        .height(CHIP_HEIGHT)

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.size(10.dp))

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
