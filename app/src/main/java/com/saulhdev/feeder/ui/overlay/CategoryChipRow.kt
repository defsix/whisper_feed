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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R

/**
 * The category strip beneath the feed header.
 *
 * Selection maps onto the existing tag filter, so choosing a chip filters the
 * feed through the same path the filter sheet already uses. Selecting none is
 * "All": an empty tag set is what the article query treats as unfiltered.
 *
 * The "All" chip is always drawn, even with no categories to sit beside it.
 * That is partly so the row is visibly present before any source has been
 * tagged, and partly because an empty row is indistinguishable from one that
 * failed to render — which is precisely what made the first version of this
 * hard to verify on device.
 *
 * The permanent categories the design calls for (Unread, Saved, Recently
 * Added) are not here yet: articles currently carry no read state at all, so
 * they need the article-state model rather than a chip that lies.
 *
 * Unselected chips carry a filled container rather than Material's default
 * outline-over-nothing. The overlay's background opacity is user-adjustable, and
 * at a low setting an outlined chip has nothing behind it but the wallpaper —
 * the labels became unreadable on anything busy. A container costs a little of
 * the "unselected" contrast against the selected chip and buys legibility that
 * does not depend on what the user's wallpaper happens to be.
 */
@Composable
fun CategoryChipRow(
    categories: List<String>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sources default to an empty tag and the query does not exclude it, so an
    // untagged feed otherwise shows up here as a nameless chip.
    val named = categories.filter { it.isNotBlank() }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item(key = ALL_CHIP_KEY) {
            FilterChip(
                selected = selected.isEmpty(),
                // Already unfiltered, so re-tapping "All" should not churn the feed.
                onClick = { if (selected.isNotEmpty()) onSelectedChange(emptySet()) },
                label = { Text(stringResource(R.string.all_categories)) },
                shape = MaterialTheme.shapes.large,
                colors = legibleChipColors(),
                border = null,
            )
        }

        items(named, key = { it }) { category ->
            val isSelected = category in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectedChange(
                        if (isSelected) selected - category else selected + category
                    )
                },
                label = { Text(category) },
                shape = MaterialTheme.shapes.large,
                colors = legibleChipColors(),
                border = null,
            )
        }
    }
}

/** See the note on [CategoryChipRow] for why the unselected state is filled. */
@Composable
private fun legibleChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

private const val ALL_CHIP_KEY = "__all__"
