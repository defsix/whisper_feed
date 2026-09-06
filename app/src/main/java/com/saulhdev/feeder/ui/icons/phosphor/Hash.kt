/*
 * This file is part of Neo Feed
 * Copyright (c) 2023   Saul Henriquez <henriquez.saul@gmail.com>
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

val Phosphor.Hash: ImageVector
    get() {
        if (_hash != null) {
            return _hash!!
        }
        _hash = Builder(
            name = "Hash",
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
                moveTo(224.0f, 88.0f)
                lineTo(175.4f, 88.0f)
                lineToRelative(8.5f, -46.6f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, -15.8f, -2.8f)
                lineToRelative(-9.0f, 49.4f)
                lineTo(111.4f, 88.0f)
                lineToRelative(8.5f, -46.6f)
                arcToRelative(8.0f, 8.0f, 0.0f, true, false, -15.8f, -2.8f)
                lineTo(95.1f, 88.0f)
                lineTo(43.6f, 88.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, true, false, 0.0f, 16.0f)
                lineTo(92.2f, 104.0f)
                lineToRelative(-8.7f, 48.0f)
                lineTo(32.0f, 152.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 0.0f, 16.0f)
                lineTo(80.6f, 168.0f)
                lineToRelative(-8.5f, 46.6f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 6.5f, 9.3f)
                lineTo(80.0f, 223.9f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 7.9f, -6.6f)
                lineToRelative(9.0f, -49.4f)
                horizontalLineToRelative(47.7f)
                lineToRelative(-8.5f, 46.6f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 6.5f, 9.3f)
                lineTo(144.0f, 223.8f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 7.9f, -6.6f)
                lineToRelative(9.0f, -49.4f)
                horizontalLineToRelative(51.5f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 0.0f, -16.0f)
                lineTo(163.8f, 151.8f)
                lineToRelative(8.7f, -48.0f)
                lineTo(224.0f, 103.8f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 0.0f, -16.0f)
                close()
                moveTo(147.5f, 152.0f)
                lineTo(99.8f, 152.0f)
                lineToRelative(8.7f, -48.0f)
                horizontalLineToRelative(47.7f)
                close()
            }
        }
            .build()
        return _hash!!
    }

private var _hash: ImageVector? = null
