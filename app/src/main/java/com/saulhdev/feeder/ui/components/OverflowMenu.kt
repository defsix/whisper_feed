/*
 * This file is part of Neo Feed
 * Copyright (c) 2023   Neo Feed Team
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

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.DotsThreeVertical

@Composable
fun OverflowMenu(
    /**
     * What a screen reader calls the button.
     *
     * It announced "Settings" everywhere, on every screen that has one of
     * these — including the ones with no settings in the menu at all.
     */
    description: Int = R.string.more_options,
    block: @Composable OverflowMenuScope.() -> Unit)
{
    val showMenu = remember { mutableStateOf(false) }
    val overflowMenuScope = remember { OverflowMenuScopeImpl(showMenu) }

    Box{
        IconButton(
            onClick = { showMenu.value = true }
        ) {
            Icon(
                imageVector = Phosphor.DotsThreeVertical,
                contentDescription = stringResource(id = description),
                modifier = Modifier.size(HeaderIconSize),
            )
        }
        DropdownMenu(
            expanded = showMenu.value,
            onDismissRequest = { showMenu.value = false },
        ) {
            block(overflowMenuScope)
        }
    }
}

interface OverflowMenuScope {
    fun hideMenu()
}

private class OverflowMenuScopeImpl(private val showState: MutableState<Boolean>) :
    OverflowMenuScope {
    override fun hideMenu() {
        showState.value = false
    }
}
