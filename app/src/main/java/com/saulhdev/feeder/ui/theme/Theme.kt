package com.saulhdev.feeder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app's theme.
 *
 * This used to be a second, independent implementation of the same rule the
 * overlay applies, and the two had already drifted: it took a plain
 * `darkTheme: Boolean`, so the "Black" and "Automatic (from system; black)"
 * options collapsed into ordinary dark, and it carried an unused Purple/Pink
 * palette left over from the project template. It now delegates to
 * [OverlayTheme.schemeFor], which is what the launcher surface uses, so both
 * answer the colour question the same way.
 *
 * @param mode a key from `getThemes()`: auto_system, auto_system_black, light,
 *   dark, black.
 * @param dynamicColor whether to derive colours from the wallpaper.
 * @param fontPref the saved `appFont` value: "inter" or "system".
 */
@Composable
fun AppTheme(
    mode: String,
    dynamicColor: Boolean,
    fontPref: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    MaterialTheme(
        colorScheme = OverlayTheme.schemeFor(context, mode, dynamicColor),
        typography = typographyFor(fontFamilyFor(fontPref)),
        shapes = WhisperShapes,
        content = content
    )
}
