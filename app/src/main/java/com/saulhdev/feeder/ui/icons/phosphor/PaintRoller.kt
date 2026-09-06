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

val Phosphor.PaintRoller: ImageVector
    get() {
        if (_paint_roller != null) {
            return _paint_roller!!
        }
        _paint_roller = Builder(
            name = "Paint-roller",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f,
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(232.0f, 88.0f)
                lineTo(216.0f, 88.0f)
                lineTo(216.0f, 64.0f)
                arcToRelative(16.0f, 16.0f, 0.0f, false, false, -16.0f, -16.0f)
                lineTo(48.0f, 48.0f)
                arcTo(16.0f, 16.0f, 0.0f, false, false, 32.0f, 64.0f)
                lineTo(32.0f, 88.0f)
                lineTo(16.0f, 88.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 0.0f, 16.0f)
                lineTo(32.0f, 104.0f)
                verticalLineToRelative(24.0f)
                arcToRelative(16.0f, 16.0f, 0.0f, false, false, 16.0f, 16.0f)
                lineTo(200.0f, 144.0f)
                arcToRelative(16.0f, 16.0f, 0.0f, false, false, 16.0f, -16.0f)
                lineTo(216.0f, 104.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(50.0f)
                lineTo(131.6f, 182.6f)
                arcTo(16.2f, 16.2f, 0.0f, false, false, 120.0f, 198.0f)
                verticalLineToRelative(34.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 16.0f, 0.0f)
                lineTo(136.0f, 198.0f)
                lineToRelative(100.4f, -28.6f)
                arcTo(16.2f, 16.2f, 0.0f, false, false, 248.0f, 154.0f)
                lineTo(248.0f, 104.0f)
                arcTo(16.0f, 16.0f, 0.0f, false, false, 232.0f, 88.0f)
                close()
                moveTo(200.0f, 128.0f)
                lineTo(48.0f, 128.0f)
                lineTo(48.0f, 64.0f)
                lineTo(200.0f, 64.0f)
                lineTo(200.0f, 95.9f)
                horizontalLineToRelative(0.0f)
                lineTo(200.0f, 128.0f)
                close()
            }
        }
            .build()
        return _paint_roller!!
    }

private var _paint_roller: ImageVector? = null
