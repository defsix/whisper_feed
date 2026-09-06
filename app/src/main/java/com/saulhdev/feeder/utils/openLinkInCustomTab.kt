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

package com.saulhdev.feeder.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.saulhdev.feeder.R
import com.saulhdev.feeder.utils.extensions.safeStartActivity

fun openLinkInCustomTab(
    context: Context,
    link: String
): Boolean {
    @ColorInt val colorPrimaryLight =
        ContextCompat.getColor(context, R.color.md_theme_primary)
    @ColorInt val colorPrimaryDark =
        ContextCompat.getColor(context, R.color.md_theme_primary)
    try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(colorPrimaryLight)
                    .build()
            )
            .setColorSchemeParams(
                CustomTabsIntent.COLOR_SCHEME_DARK, CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(colorPrimaryDark)
                    .build()
            )
            .build()

        val targetIntent = customTabsIntent.intent.apply {
            data = Uri.parse(link)
        }
        context.safeStartActivity(targetIntent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.app_name, Toast.LENGTH_SHORT).show()
        return false
    } catch (e: Exception) {
        android.util.Log.e("openLinkInCustomTab", "Failed to open link: ${e.message}")
        return false
    }
    return true
}
