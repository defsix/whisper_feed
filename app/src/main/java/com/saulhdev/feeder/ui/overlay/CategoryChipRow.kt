/*
 * This file is part of 076 Feed
 * Copyright (c) 2026   076 Feed contributors
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
import androidx.compose.material3.FilterChipDefaults
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
 */
@Composable
fun CategoryChipRow(
    categories: List<String>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return

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
                shape = FilterChipDefaults.shape,
            )
        }

        items(categories, key = { it }) { category ->
            val isSelected = category in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectedChange(
                        if (isSelected) selected - category else selected + category
                    )
                },
                label = { Text(category) },
                shape = FilterChipDefaults.shape,
            )
        }
    }
}

private const val ALL_CHIP_KEY = "__all__"
