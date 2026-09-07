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
package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R

/**
 * Saving an article, everywhere it can be done.
 *
 * One mark, one meaning. A heart on the card saved an article while a bookmark
 * ribbon in the header listed the saved ones — two borrowed glyphs for one
 * idea, and a heart in a reader reads as "liked" rather than "kept".
 *
 * The saved state is a tinted disc behind the mark rather than a filled
 * variant of it. That is not a stylistic preference: the mark is three
 * separate blades, and stroking it for an unsaved state puts two hairlines
 * around each one, which merge into a smudge at the 20dp this is drawn at. The
 * disc reads at any size and matches the header's own toggles.
 *
 * @param onImage true when this sits over a photograph, where the theme's
 *   containers cannot be relied on for contrast and white on a translucent
 *   scrim is the only thing that works against an unknown image.
 */
@Composable
fun SaveButton(
    saved: Boolean,
    onSavedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    onImage: Boolean = false,
) {
    val container = when {
        !saved -> Color.Transparent
        onImage -> Color.White.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val tint = when {
        onImage -> Color.White
        saved -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = { onSavedChange(!saved) },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_whisper_save),
            contentDescription = stringResource(
                if (saved) R.string.action_unsave else R.string.action_save
            ),
            tint = tint,
            modifier = Modifier
                .clip(CircleShape)
                .background(container)
                .padding(if (saved) 5.dp else 0.dp)
                .size(size),
        )
    }
}
