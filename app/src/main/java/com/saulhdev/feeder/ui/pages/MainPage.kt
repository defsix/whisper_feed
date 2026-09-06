package com.saulhdev.feeder.ui.pages

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.saulhdev.feeder.ui.components.dialog.DrawPermissionRequestDialog

/**
 * The app's home screen — the feed, and only the feed.
 *
 * Settings and Data sources used to be the second and third pages of a pager
 * behind a navigation bar, which spent a permanent strip of screen on two
 * destinations that are visited rarely. They are ordinary destinations now,
 * reached from the feed's overflow menu, so the feed gets the whole window.
 *
 * @param pageIndex retained so existing `Main(page)` deep links still resolve;
 *   there is only one page now, so it is ignored.
 */
@Composable
fun MainPage(@Suppress("UNUSED_PARAMETER") pageIndex: Int = 0) {
    ArticleListPage()

    if (!Settings.canDrawOverlays(LocalContext.current)) DrawPermissionRequestDialog()
}
