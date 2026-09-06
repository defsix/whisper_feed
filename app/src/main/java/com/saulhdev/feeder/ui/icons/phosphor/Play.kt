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
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.Play: ImageVector
    get() {
        if (_play != null) {
            return _play!!
        }
        _play = ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256.0f,
            viewportHeight = 256.0f,
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(232.4f, 114.49f)
                lineTo(88.32f, 26.35f)
                arcToRelative(
                    16f,
                    16f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -16.2f,
                    -0.3f
                )
                arcTo(
                    15.86f,
                    15.86f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    64f,
                    39.87f
                )
                verticalLineTo(216.13f)
                arcTo(15.94f, 15.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 80f, 232f)
                arcToRelative(
                    16.07f,
                    16.07f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    8.36f,
                    -2.35f
                )
                lineTo(232.4f, 141.51f)
                arcToRelative(
                    15.81f,
                    15.81f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    0f,
                    -27f
                )
                close()
                moveTo(80f, 215.94f)
                verticalLineTo(40f)
                lineToRelative(143.83f, 88f)
                close()
            }
        }.build()

        return _play!!
    }

private var _play: ImageVector? = null
