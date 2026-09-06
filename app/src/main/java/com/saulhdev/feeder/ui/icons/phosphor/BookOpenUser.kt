package com.saulhdev.feeder.ui.icons.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.BookOpenUser: ImageVector
    get() {
        if (_BookOpenUser != null) {
            return _BookOpenUser!!
        }
        _BookOpenUser = ImageVector.Builder(
            name = "BookOpenUser",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 256f,
            viewportHeight = 256f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(232f, 72f)
                lineTo(160f, 72f)
                arcToRelative(
                    40f,
                    40f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -32f,
                    16f
                )
                arcTo(40f, 40f, 0f, isMoreThanHalf = false, isPositiveArc = false, 96f, 72f)
                lineTo(24f, 72f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, -8f, 8f)
                lineTo(16f, 200f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8f, 8f)
                lineTo(96f, 208f)
                arcToRelative(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, 24f, 24f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16f, 0f)
                arcToRelative(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, 24f, -24f)
                horizontalLineToRelative(72f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8f, -8f)
                lineTo(240f, 80f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 232f, 72f)
                close()
                moveTo(96f, 192f)
                lineTo(32f, 192f)
                lineTo(32f, 88f)
                lineTo(96f, 88f)
                arcToRelative(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, 24f, 24f)
                verticalLineToRelative(88f)
                arcTo(39.81f, 39.81f, 0f, isMoreThanHalf = false, isPositiveArc = false, 96f, 192f)
                close()
                moveTo(224f, 192f)
                lineTo(160f, 192f)
                arcToRelative(
                    39.81f,
                    39.81f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -24f,
                    8f
                )
                lineTo(136f, 112f)
                arcToRelative(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, 24f, -24f)
                horizontalLineToRelative(64f)
                close()
                moveTo(89.6f, 43.19f)
                arcToRelative(48f, 48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 76.8f, 0f)
                arcToRelative(
                    8f,
                    8f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = true,
                    -12.79f,
                    9.62f
                )
                arcToRelative(
                    32f,
                    32f,
                    0f,
                    isMoreThanHalf = false,
                    isPositiveArc = false,
                    -51.22f,
                    0f
                )
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 89.6f, 43.19f)
                close()
            }
        }.build()

        return _BookOpenUser!!
    }

@Suppress("ObjectPropertyName")
private var _BookOpenUser: ImageVector? = null