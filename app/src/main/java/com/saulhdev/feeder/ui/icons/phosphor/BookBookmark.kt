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

val Phosphor.BookBookmark: ImageVector
    get() {
        if (_book_bookmark != null) {
            return _book_bookmark!!
        }
        _book_bookmark = Builder(
            name = "Book-bookmark",
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
                moveTo(208.0f, 24.0f)
                lineTo(72.0f, 24.0f)
                arcTo(32.1f, 32.1f, 0.0f, false, false, 40.0f, 56.0f)
                lineTo(40.0f, 224.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 8.0f, 8.0f)
                lineTo(192.0f, 232.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 0.0f, -16.0f)
                lineTo(56.0f, 216.0f)
                arcToRelative(16.0f, 16.0f, 0.0f, false, true, 16.0f, -16.0f)
                lineTo(208.0f, 200.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 8.0f, -8.0f)
                lineTo(216.0f, 32.0f)
                arcTo(8.0f, 8.0f, 0.0f, false, false, 208.0f, 24.0f)
                close()
                moveTo(120.0f, 40.0f)
                horizontalLineToRelative(48.0f)
                verticalLineToRelative(72.0f)
                lineTo(148.8f, 97.6f)
                arcToRelative(8.1f, 8.1f, 0.0f, false, false, -9.6f, 0.0f)
                lineTo(120.0f, 112.0f)
                close()
                moveTo(200.0f, 184.0f)
                lineTo(72.0f, 184.0f)
                arcToRelative(32.2f, 32.2f, 0.0f, false, false, -16.0f, 4.3f)
                lineTo(56.0f, 56.0f)
                arcTo(16.0f, 16.0f, 0.0f, false, true, 72.0f, 40.0f)
                horizontalLineToRelative(32.0f)
                verticalLineToRelative(88.0f)
                arcToRelative(8.0f, 8.0f, 0.0f, false, false, 12.8f, 6.4f)
                lineTo(144.0f, 114.0f)
                lineToRelative(27.2f, 20.4f)
                arcTo(7.7f, 7.7f, 0.0f, false, false, 176.0f, 136.0f)
                arcToRelative(9.4f, 9.4f, 0.0f, false, false, 3.6f, -0.8f)
                arcTo(8.2f, 8.2f, 0.0f, false, false, 184.0f, 128.0f)
                lineTo(184.0f, 40.0f)
                horizontalLineToRelative(16.0f)
                close()
            }
        }
            .build()
        return _book_bookmark!!
    }

private var _book_bookmark: ImageVector? = null
