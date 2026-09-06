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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.Filter: ImageVector
    get() {
        if (_filter != null) {
            return _filter!!
        }
        _filter = ImageVector.Builder(
            name = "Filter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 0.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(230.6f, 49.53f)
                arcToRelative(15.81f, 15.81f, 0.0f, false, false, -14.6f, -9.53f)
                lineTo(40f, 40f)
                arcToRelative(16f, 16f, 0.0f, false, false, -11.81f, 26.76f)
                lineToRelative(0.08f, 0.09f)
                lineTo(96f, 139.17f)
                verticalLineTo(216f)
                arcToRelative(16f, 16f, 0.0f, false, false, 24.87f, 13.32f)
                lineToRelative(32f, -21.34f)
                arcTo(16f, 16f, 0.0f, false, false, 160f, 194.66f)
                verticalLineTo(139.17f)
                lineToRelative(67.74f, -72.32f)
                lineToRelative(0.08f, -0.09f)
                arcTo(15.8f, 15.8f, 0.0f, false, false, 230.6f, 49.53f)
                close()
                moveTo(40f, 56f)
                lineTo(40f, 56f) // This is a placeholder "noop" move, can be removed
                close()
                moveTo(146.18f, 130.58f)
                arcToRelative(8f, 8f, 0.0f, false, false, -2.18f, 5.42f)
                verticalLineTo(194.66f)
                lineTo(112f, 216f)
                verticalLineTo(136f)
                arcToRelative(8f, 8f, 0.0f, false, false, -2.16f, -5.47f)
                lineTo(40f, 56f)
                horizontalLineTo(216f)
                close()
            }
        }.build()
        return _filter!!
    }

private var _filter: ImageVector? = null
