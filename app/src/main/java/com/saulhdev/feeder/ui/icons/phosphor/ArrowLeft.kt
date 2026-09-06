package com.saulhdev.feeder.ui.icons.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.ArrowLeft: ImageVector
    get() {
        if (_arrowLeft != null) {
            return _arrowLeft!!
        }
        _arrowLeft = ImageVector.Builder(
            name = "ArrowLeft",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 256.0f,
            viewportHeight = 256.0f,
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(224f, 128f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -8f, 8f)
                horizontalLineTo(59.31f)
                lineToRelative(58.35f, 58.34f)
                arcToRelative(
                    8f,
                    8f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -11.32f,
                    11.32f
                )
                lineToRelative(-72f, -72f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -11.32f)
                lineToRelative(72f, -72f)
                arcToRelative(
                    8f,
                    8f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    11.32f,
                    11.32f
                )
                lineTo(59.31f, 120f)
                horizontalLineTo(216f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 224f, 128f)
                close()
            }
        }.build()

        return _arrowLeft!!
    }

private var _arrowLeft: ImageVector? = null
