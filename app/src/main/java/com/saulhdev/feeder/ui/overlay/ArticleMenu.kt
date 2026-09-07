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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.DotsThreeVertical
import com.saulhdev.feeder.ui.icons.phosphor.EyeSlash
import com.saulhdev.feeder.ui.icons.phosphor.Prohibit
import com.saulhdev.feeder.ui.icons.phosphor.ShareNetwork
import com.saulhdev.feeder.ui.icons.phosphor.Sparkle

/**
 * What can be done with one article beyond opening it.
 *
 * Every entry acts immediately and none of them opens a dialog: this menu is
 * reached mid-scroll, often one-handed, and a confirmation step for "show me
 * less of this" would cost more than getting it wrong does.
 *
 * More and Less record a preference the ranking does not read yet. That is
 * deliberate rather than a stub — a signal is only useful with history behind
 * it, so collecting from the day the menu appears means the first release that
 * ranks has something to rank with. Hide source and Share work fully today.
 *
 * @param tint set on the hero shape, where the menu sits over a photograph and
 *   the theme's content colours cannot be relied on to be visible.
 */
@Composable
fun ArticleOverflowMenu(
    onMoreLikeThis: () -> Unit,
    onLessLikeThis: () -> Unit,
    onHideSource: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(
            imageVector = Phosphor.DotsThreeVertical,
            contentDescription = stringResource(R.string.more_options),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MenuEntry(R.string.more_like_this, Phosphor.Sparkle) {
            expanded = false
            onMoreLikeThis()
        }
        MenuEntry(R.string.less_like_this, Phosphor.Prohibit) {
            expanded = false
            onLessLikeThis()
        }
        MenuEntry(R.string.hide_source, Phosphor.EyeSlash) {
            expanded = false
            onHideSource()
        }
        MenuEntry(R.string.share, Phosphor.ShareNetwork) {
            expanded = false
            onShare()
        }
    }
}

@Composable
private fun MenuEntry(
    textId: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(textId)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
