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

val Phosphor.Filtered: ImageVector
    get() {
        if (_filtered != null) {
            return _filtered!!
        }
        _filtered = ImageVector.Builder(
            name = "Filtered",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(227.82f, 66.76f)
                arcToRelative(16.0f, 16.0f, 0f, false, false, -11.82f, -26.76f)
                horizontalLineTo(40f)
                arcToRelative(16.0f, 16.0f, 0f, false, false, -11.81f, 26.76f)
                lineToRelative(0.08f, 0.09f)
                lineTo(96f, 139.17f)
                verticalLineTo(216f)
                arcToRelative(16.0f, 16.0f, 0f, false, false, 24.87f, 13.32f)
                lineToRelative(32f, -21.34f)
                arcToRelative(16.0f, 16.0f, 0f, false, false, 7.13f, -13.32f)
                verticalLineTo(139.17f)
                lineToRelative(67.73f, -72.32f)

                moveTo(40f, 56f)
                horizontalLineToRelative(0f)

                moveTo(146.19f, 130.59f)
                arcToRelative(8.0f, 8.0f, 0f, false, false, -2.19f, 5.41f)
                verticalLineTo(194.66f)
                lineTo(112f, 216f)
                verticalLineTo(136f)
                arcToRelative(8.0f, 8.0f, 0f, false, false, -2.16f, -5.46f)
                lineTo(40f, 56f)
                horizontalLineTo(216f)

                moveTo(245.68f, 210.4f)
                arcToRelative(8.0f, 8.0f, 0f, false, true, -11.32f, 11.32f)
                lineTo(216f, 203.32f)
                lineTo(197.66f, 221.67f)
                arcToRelative(8.0f, 8.0f, 0f, false, true, -11.31f, -11.32f)
                lineTo(204.69f, 192f)
                lineTo(186.35f, 173.65f)
                arcToRelative(8.0f, 8.0f, 0f, false, true, 11.31f, -11.31f)
                lineTo(216f, 180.69f)
                lineTo(234.34f, 162.35f)
                arcToRelative(8.0f, 8.0f, 0f, false, true, 11.32f, 11.31f)
                lineTo(227.31f, 192f)
                close()
            }
        }.build()
        return _filtered!!
    }

private var _filtered: ImageVector? = null