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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * The source's own mark, beside its name.
 *
 * It is what makes a publisher recognisable at a glance in a mixed feed — the
 * name alone is a word among other words, and on a compact row it is a
 * truncated word at that.
 *
 * The monogram is the default case, not the exception. Plenty of feeds declare
 * no icon and plenty of small sites serve none, and a row that collapses to
 * nothing whenever that happens reads worse than one that always has something
 * in the same place. The tint is derived from the name so a given source keeps
 * the same colour everywhere it appears, without storing anything.
 *
 * @param onImage true over a photograph, where the theme's containers cannot
 *   be relied on and the mark needs its own opaque ground.
 */
@Composable
fun SourceMark(
    iconUrl: String?,
    sourceName: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    onImage: Boolean = false,
) {
    // Coil reports a failure asynchronously, so the monogram cannot simply be
    // an else-branch on a null URL — a 404 favicon would otherwise leave a
    // hole where every other row has a mark.
    var failed by remember(iconUrl) { mutableStateOf(false) }
    val hasIcon = !iconUrl.isNullOrBlank() && !failed

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            // The hashed colour belongs to the monogram, not to the mark. It
            // used to be painted unconditionally, so a favicon with any
            // transparency in it — which is most of them — sat on an arbitrary
            // colour picked from the source's name, and read as that site's
            // branding. A neutral container behind a real icon instead.
            .background(
                if (hasIcon) {
                    if (onImage) Color.White.copy(alpha = 0.9f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    monogramColor(sourceName, onImage)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (hasIcon) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                // Fit, not Crop: plenty of sites publish a wide wordmark as
                // their icon, and cropping one to a circle keeps the middle
                // few letters and throws the rest away. Inset so a square icon
                // does not touch the circle's edge.
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier
                    .size(size)
                    .padding(1.dp),
            )
        } else {
            Text(
                text = monogram(sourceName),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.55f).sp,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * The letter to stand in for a source.
 *
 * The first letter of the first word that is not an article: "The Independent"
 * is a T on the naive reading, which says nothing and collides with every
 * other publication that starts the same way.
 */
private fun monogram(name: String): String {
    val skip = setOf("the", "a", "an")
    val word = name.split(' ', '-', '–')
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && it.lowercase() !in skip }
        ?: name
    return word.firstOrNull()?.uppercase().orEmpty()
}

/**
 * A stable colour for a source with no icon.
 *
 * Hashed from the name rather than assigned, so it survives a resync, a
 * reinstall and an OPML round trip without being stored anywhere. Fixed
 * saturation and lightness keep every monogram at the same weight, so a row of
 * them reads as one system rather than a bag of sweets.
 */
private fun monogramColor(name: String, onImage: Boolean): Color {
    if (name.isEmpty()) return Color(0xFF64748B)
    val hue = ((name.hashCode().toLong() and 0xFFFFFFFFL) % 360L).toFloat()
    return Color.hsl(hue, saturation = 0.45f, lightness = if (onImage) 0.45f else 0.42f)
}
