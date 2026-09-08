/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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

package com.saulhdev.feeder.ui.icons.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

/**
 * Phosphor "magnifying-glass", regular weight, from @phosphor-icons/core 2.1.1.
 *
 * The path is the upstream SVG's own, parsed at runtime rather than
 * transcribed into builder calls by hand. See docs/licenses/Phosphor-MIT.txt.
 */
val Phosphor.MagnifyingGlass: ImageVector
    get() {
        if (_magnifyingGlass != null) return _magnifyingGlass!!
        _magnifyingGlass = ImageVector.Builder(
            name = "MagnifyingGlass",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 256.0f,
            viewportHeight = 256.0f,
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(PATH_DATA).toNodes(),
                fill = SolidColor(Color(0xFF000000)),
            )
        }.build()
        return _magnifyingGlass!!
    }

private var _magnifyingGlass: ImageVector? = null

private const val PATH_DATA =
    "M229.66,218.34l-50.07-50.06a88.11,88.11,0,1,0-11.31,11.31l50.06,50.07a8,8,0,0,0,11.32-11.32ZM40,112a72,72,0,1,1,72,72A72.08,72.08,0,0,1,40,112Z"
