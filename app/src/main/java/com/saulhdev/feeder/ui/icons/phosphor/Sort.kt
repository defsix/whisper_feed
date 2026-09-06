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
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.Sort: ImageVector
    get() {
        if (_sort != null) {
            return _sort!!
        }
        _sort = Builder(
            name = "SortAz",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 256f,
            viewportHeight = 256f
        ).apply {

            // Letter A (symbolic triangle with crossbar)
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(40f, 96f)
                lineTo(56f, 48f)
                lineTo(72f, 96f)
                lineTo(64f, 96f)
                lineTo(60f, 84f)
                lineTo(52f, 84f)
                lineTo(48f, 96f)
                close()
                moveTo(55f, 76f)
                horizontalLineToRelative(6f)
                lineTo(58f, 66f)
                close()
            }

            // Letter Z (symbolic zigzag)
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(40f, 120f)
                lineTo(80f, 120f)
                lineTo(40f, 160f)
                lineTo(80f, 160f)
                lineTo(80f, 148f)
                lineTo(56f, 148f)
                lineTo(80f, 124f)
                lineTo(80f, 120f)
                close()
            }

            // Down arrow
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(176f, 48f)
                verticalLineTo(184f)
                lineTo(160f, 168f)
                lineTo(152f, 176f)
                lineTo(184f, 208f)
                lineTo(216f, 176f)
                lineTo(208f, 168f)
                lineTo(192f, 184f)
                verticalLineTo(48f)
                close()
            }

        }.build()
        return _sort!!
    }

private var _sort: ImageVector? = null
