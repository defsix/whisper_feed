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
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

/**
 * Phosphor "prohibit", regular weight, from @phosphor-icons/core 2.1.1.
 *
 * The path is the upstream SVG's own, parsed at runtime rather than
 * transcribed into builder calls by hand — the same artwork, and no chance of
 * a typo in three hundred coordinates. See docs/licenses/Phosphor-MIT.txt.
 */
val Phosphor.Prohibit: ImageVector
    get() {
        if (_prohibit != null) return _prohibit!!
        _prohibit = ImageVector.Builder(
            name = "Prohibit",
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
        return _prohibit!!
    }

private var _prohibit: ImageVector? = null

private const val PATH_DATA =
    "M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm88,104a87.56,87.56,0,0,1-20.41,56.28L71.72,60.4A88,88,0,0,1,216,128ZM40,128A87.56,87.56,0,0,1,60.41,71.72L184.28,195.6A88,88,0,0,1,40,128Z"
