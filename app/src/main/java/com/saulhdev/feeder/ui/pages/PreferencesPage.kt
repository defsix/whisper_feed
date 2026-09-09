/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
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

package com.saulhdev.feeder.ui.pages

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.StringSelectionPref
import com.saulhdev.feeder.manager.models.scheduleFullTextParse
import com.saulhdev.feeder.ui.components.PreferenceGroup
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.dialog.BaseDialog
import com.saulhdev.feeder.ui.components.dialog.StringSelectionPrefDialogUI
import androidx.compose.runtime.DisposableEffect
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import com.saulhdev.feeder.NeoApp
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.Power
import kotlinx.coroutines.launch
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PreferencesPage(
    prefs: FeedPreferences = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncClient: SyncRestClient = koinInject()
    val title = stringResource(id = R.string.title_settings)
    // The row acts on the feed, which lives in a view model this screen does
    // not otherwise touch; see FeedPreferences.markEverythingRead.
    val articles: ArticleListViewModel = koinNeoViewModel()
    DisposableEffect(articles) {
        FeedPreferences.markEverythingRead = { articles.markAllRead() }
        onDispose { FeedPreferences.markEverythingRead = null }
    }

    val servicePrefs = listOf(
        prefs.itemsPerFeed,
        prefs.syncFrequency,
        prefs.syncRange,
        prefs.syncOnlyOnWifi,
        prefs.articleOpenMode,
        prefs.fullTextForAllFeeds,
        prefs.removeDuplicates,
        prefs.breakingNews,
        prefs.stickyTop,
        prefs.markReadOnScroll,
        prefs.markAllRead,
        prefs.learned,
        prefs.showTour,
        prefs.launcherSetup,
        prefs.readVisibility,
        prefs.volumeKeyScroll,
    )
    val filterPrefs = listOf(
        prefs.blockedWords,
    )
    val glancePrefs = listOf(
        prefs.glanceEnabled,
        prefs.glancePlaceName,
    )
    val themePrefs = listOf(
        prefs.feedLayout,
        prefs.overlayTheme,
        prefs.pureBlack,
        prefs.appFont,
        prefs.dynamicColor,
        prefs.overlayTransparency,
    )
    // Both ways of getting a feed into the app, together, at the top. Data
    // sources was previously only in the feed's overflow menu.
    val sourcePrefs = listOf(
        prefs.sources,
        prefs.starterSources,
    )
    val syncPrefs = listOf(
        prefs.backupFolder,
        prefs.account,
    )
    val debugPrefs = listOf(
        prefs.reportProblem,
        prefs.exportDiagnostics,
        prefs.about,
    )

    // Turning the global switch on should start downloading now, not at the
    // next scheduled sync — the setting reads as an instruction, not a plan.
    val fullTextForAll by prefs.fullTextForAllFeeds.get()
        .collectAsState(initial = remember { prefs.fullTextForAllFeeds.getValue() })
    var wasFullTextForAll by remember { mutableStateOf(fullTextForAll) }
    LaunchedEffect(fullTextForAll) {
        if (fullTextForAll && !wasFullTextForAll) scheduleFullTextParse()
        wasFullTextForAll = fullTextForAll
    }

    val openDialog = remember { mutableStateOf(false) }
    var dialogPref by remember { mutableStateOf<Any?>(null) }
    val onPrefDialog = { pref: Any ->
        dialogPref = pref
        openDialog.value = true
    }

    ViewWithActionBar(
        title = title,
        largeTitle = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = paddingValues.calculateTopPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Reload and Restart, which used to be in the feed's overflow
            // menu. They are actions rather than settings, so they are buttons
            // at the top rather than rows in a group pretending to be
            // preferences.
            item(key = "actions") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    OutlinedActionButton(
                        text = stringResource(R.string.action_reload),
                        icon = Phosphor.ArrowCounterClockwise,
                        modifier = Modifier.weight(1f),
                        onClick = { scope.launch { syncClient.syncAllFeeds() } },
                    )
                    OutlinedActionButton(
                        text = stringResource(R.string.action_restart),
                        icon = Phosphor.Power,
                        modifier = Modifier.weight(1f),
                        onClick = { NeoApp.instance?.restart(false) },
                    )
                }
            }

            item(key = R.string.title_sources) {
                PreferenceGroup(
                    stringResource(id = R.string.title_sources),
                    prefs = sourcePrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.title_service) {
                PreferenceGroup(
                    stringResource(id = R.string.title_service),
                    prefs = servicePrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_account) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_account),
                    prefs = syncPrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_cat_filters) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_filters),
                    prefs = filterPrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_glance_row) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_glance_row),
                    prefs = glancePrefs,
                    onPrefDialog = onPrefDialog
                )
            }
            item(key = R.string.pref_cat_overlay) {
                PreferenceGroup(
                    stringResource(id = R.string.pref_cat_overlay),
                    prefs = themePrefs,
                    onPrefDialog = onPrefDialog
                )

                if (!Settings.canDrawOverlays(context)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = stringResource(R.string.draw_permission_required))
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            ) {
                                Text(text = stringResource(R.string.go_to_settings))
                            }
                        }
                    }
                }
            }
            item(key = R.string.title_other) {
                PreferenceGroup(
                    stringResource(id = R.string.title_other),
                    prefs = debugPrefs,
                    onPrefDialog = onPrefDialog
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (openDialog.value) {
            BaseDialog(openDialogCustom = openDialog) {
                when (dialogPref) {
                    is StringSelectionPref -> StringSelectionPrefDialogUI(
                        pref = dialogPref as StringSelectionPref,
                        openDialogCustom = openDialog
                    )
                }
            }
        }
    }
}
