package com.saulhdev.feeder.ui.icons.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.SortAscending: ImageVector
    get() {
        if (_sortAscending != null) {
            return _sortAscending!!
        }
        _sortAscending = ImageVector.Builder(
            name = "SortAscending",
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
                moveTo(128f, 128f)
                arcToRelative(8f, 8f, 0f, false, true, -8f, 8f)
                horizontalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, true, 0f, -16f)
                horizontalLineTo(120f)
                arcTo(8f, 8f, 0f, false, true, 128f, 128f)
                close()

                // Top horizontal bar
                moveTo(48f, 72f)
                horizontalLineTo(184f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                horizontalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                close()

                // Bottom horizontal bar
                moveTo(104f, 184f)
                horizontalLineTo(48f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, 16f)
                horizontalLineTo(104f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -16f)
                close()

                // Downward arrow on the right
                moveTo(229.66f, 162.34f)
                arcToRelative(8f, 8f, 0f, false, false, -11.32f, 0f)
                lineTo(192f, 188.69f)
                verticalLineTo(112f)
                arcToRelative(8f, 8f, 0f, false, false, -16f, 0f)
                verticalLineTo(188.69f)
                lineTo(149.66f, 162.34f)
                arcToRelative(8f, 8f, 0f, true, false, -11.32f, 11.32f)
                lineToRelative(40f, 40f)
                arcToRelative(8f, 8f, 0f, false, false, 11.32f, 0f)
                lineToRelative(40f, -40f)
                arcToRelative(8f, 8f, 0f, false, false, 0f, -11.32f)
                close()
            }
        }.build()
        return _sortAscending!!
    }

private var _sortAscending: ImageVector? = null
