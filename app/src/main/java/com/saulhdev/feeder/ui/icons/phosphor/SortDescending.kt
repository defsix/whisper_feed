package com.saulhdev.feeder.ui.icons.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.SortDescending: ImageVector
    get() {
        if (_sortDescending != null) {
            return _sortDescending!!
        }
        _sortDescending = ImageVector.Builder(
            name = "SortDescending",
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
                // Middle horizontal bar
                moveTo(40f, 128f)
                arcToRelative(8f, 8f, 0f, false, true, 8f, -8f)
                horizontalLineTo(120f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, 16f)
                horizontalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, true, -8f, -8f)
                close()

                // Top horizontal bar
                moveTo(48f, 72f)
                horizontalLineTo(104f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                horizontalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                close()

                // Bottom horizontal bar
                moveTo(184f, 184f)
                horizontalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineTo(184f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                close()

                // Upward arrow on the right
                moveTo(229.66f, 82.34f)
                lineToRelative(-40f, -40f)
                arcToRelative(8f, 8f, 0f, false, false, -11.32f, 0f)
                lineToRelative(-40f, 40f)
                arcToRelative(8f, 8f, 0f, false, false, 11.32f, 11.32f)
                lineTo(176f, 67.31f)
                verticalLineTo(144f)
                arcToRelative(8f, 8f, 0f, false, false, 16f, 0f)
                verticalLineTo(67.31f)
                lineToRelative(26.34f, 26.35f)
                arcToRelative(8f, 8f, 0f, false, false, 11.32f, -11.32f)
                close()
            }
        }.build()
        return _sortDescending!!
    }

private var _sortDescending: ImageVector? = null
