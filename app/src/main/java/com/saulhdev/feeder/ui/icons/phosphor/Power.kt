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
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.Power: ImageVector
    get() {
        if (_power != null) {
            return _power!!
        }
        _power = Builder(
            name = "Power",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256.0f,
            viewportHeight = 256.0f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(120.0f, 124.0f)
                lineTo(120.0f, 48.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, true, 16.0f, 0.0f)
                verticalLineToRelative(76.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, true, -16.0f, 0.0f)
                close()
                moveTo(180.4f, 47.5f)
                arcToRelative(8.1f, 8.1f, 0.0f, false, false, -11.1f, 2.4f)
                arcToRelative(7.9f, 7.9f, 0.0f, false, false, 2.3f, 11.0f)
                arcToRelative(80.0f, 80.0f, 0.0f, true, true, -87.2f, 0.0f)
                arcToRelative(7.9f, 7.9f, 0.0f, false, false, 2.3f, -11.0f)
                arcToRelative(8.1f, 8.1f, 0.0f, false, false, -11.1f, -2.4f)
                arcToRelative(96.0f, 96.0f, 0.0f, true, false, 104.8f, 0.0f)
                close()
            }
        }
            .build()
        return _power!!
    }

private var _power: ImageVector? = null
