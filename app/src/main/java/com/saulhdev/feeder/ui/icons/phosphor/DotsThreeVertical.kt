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

val Phosphor.DotsThreeVertical: ImageVector
    get() {
        if (_dotsThreeVertical != null) {
            return _dotsThreeVertical!!
        }
        _dotsThreeVertical = ImageVector.Builder(
            name = "DotsThreeVertical",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 256.0f,
            viewportHeight = 256.0f,
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(140f, 128f)
                arcToRelative(12f, 12f, 0f, isMoreThanHalf = true, isPositiveArc = true, -12f, -12f)
                arcTo(12f, 12f, 0f, isMoreThanHalf = false, isPositiveArc = true, 140f, 128f)
                close()
                moveTo(128f, 72f)
                arcToRelative(
                    12f,
                    12f,
                    0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    -12f,
                    -12f
                )
                arcTo(12f, 12f, 0f, isMoreThanHalf = false, isPositiveArc = false, 128f, 72f)
                close()
                moveTo(128f, 184f)
                arcToRelative(12f, 12f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 12f)
                arcTo(12f, 12f, 0f, isMoreThanHalf = false, isPositiveArc = false, 128f, 184f)
                close()
            }
        }.build()

        return _dotsThreeVertical!!
    }

private var _dotsThreeVertical: ImageVector? = null

