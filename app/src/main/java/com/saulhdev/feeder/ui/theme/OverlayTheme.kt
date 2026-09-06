/*
 * This file is part of 076 Feed
 * Copyright (c) 2026   076 Feed contributors
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
package com.saulhdev.feeder.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.SparseIntArray
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * The overlay's single source of truth for colour.
 *
 * The overlay previously had its own palette in [CardTheme] — a SparseIntArray
 * of five roles — while Compose content used a separate Material 3 scheme. Two
 * palettes meant two answers to the same question, and they could disagree: the
 * View side read the dynamic system colours unconditionally, ignoring the user's
 * "Dynamic Color" preference entirely, and used a fixed brand accent rather than
 * a Material You one. The preference was only ever honoured in the standalone
 * activity, not on the launcher surface that is the actual product.
 *
 * So the scheme is built once here and the legacy array is derived from it via
 * [toCardColors]. Compose gets a real [ColorScheme]; the RecyclerView cards get
 * exactly the same colours through the array they already consume, so none of
 * the existing View code has to change.
 */
object OverlayTheme {

    /**
     * Builds the scheme for a saved theme preference.
     *
     * @param mode one of the keys from `getThemes()`: auto_system,
     *   auto_system_black, light, dark, black.
     * @param dynamic whether to derive colours from the wallpaper. Only possible
     *   on API 31+; below that Material You has no system source to read, and
     *   the M3 baseline palette is used instead.
     */
    fun schemeFor(context: Context, mode: String, dynamic: Boolean): ColorScheme {
        val systemInDark = context.resources.configuration.uiMode
            .and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val dark = when (mode) {
            "light" -> false
            "dark", "black" -> true
            else -> systemInDark // auto_system, auto_system_black
        }
        val black = when (mode) {
            "black" -> true
            "auto_system_black" -> systemInDark
            else -> false
        }

        val base = when {
            dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            dark -> darkColorScheme()
            else -> lightColorScheme()
        }

        // "Black" is the dark scheme pushed to true black for OLED, which is a
        // background choice rather than a different palette — the accent and
        // text roles stay as the dark scheme defines them.
        return if (black) base.copy(background = Color.Black, surface = Color.Black) else base
    }

    /**
     * Projects a [ColorScheme] onto the five roles the legacy View code reads.
     *
     * Keeps [CardTheme.Colors] as the interface so the RecyclerView binders,
     * header tinting and window background need no changes, while the values
     * behind it now come from the same scheme Compose is using.
     */
    fun ColorScheme.toCardColors(isLight: Boolean): SparseIntArray = SparseIntArray().apply {
        put(CardTheme.Colors.CARD_BG.ordinal, surfaceContainer.toArgb())
        put(CardTheme.Colors.TEXT_COLOR_PRIMARY.ordinal, onSurface.toArgb())
        put(CardTheme.Colors.TEXT_COLOR_SECONDARY.ordinal, onSurfaceVariant.toArgb())
        put(CardTheme.Colors.ACCENT_COLOR.ordinal, primary.toArgb())
        put(CardTheme.Colors.OVERLAY_BG.ordinal, background.toArgb())
        put(CardTheme.Colors.IS_LIGHT.ordinal, if (isLight) 1 else 0)
    }

    /** Whether [scheme] is a light one, decided by its own background. */
    fun isLight(scheme: ColorScheme): Boolean = scheme.background.luminance() > 0.5f

    private fun Color.luminance(): Float =
        0.299f * red + 0.587f * green + 0.114f * blue
}
