/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Saul Henriquez <henriquez.saul@gmail.com>
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
package com.saulhdev.feeder.data.content

import android.content.Context
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.entity.SORT_CHRONOLOGICAL
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.BookBookmark
import com.saulhdev.feeder.ui.icons.phosphor.Browser
import com.saulhdev.feeder.ui.icons.phosphor.Bug
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.Clock
import com.saulhdev.feeder.ui.icons.phosphor.FunnelSimple
import com.saulhdev.feeder.ui.icons.phosphor.Hash
import com.saulhdev.feeder.ui.icons.phosphor.Info
import com.saulhdev.feeder.ui.icons.phosphor.PaintRoller
import com.saulhdev.feeder.ui.icons.phosphor.SubtractSquare
import com.saulhdev.feeder.ui.icons.phosphor.Swatches
import com.saulhdev.feeder.ui.icons.phosphor.WifiHigh
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.utils.Diagnostics
import com.saulhdev.feeder.utils.getItemsPerFeed
import com.saulhdev.feeder.utils.getMastodonItemsPerFeed
import com.saulhdev.feeder.utils.getSortingOptions
import com.saulhdev.feeder.utils.getSyncFrequency
import com.saulhdev.feeder.utils.getSyncRange
import com.saulhdev.feeder.utils.getThemes
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class FeedPreferences private constructor(val context: Context) : KoinComponent {
    private val dataStore: DataStore<Preferences> by inject()
    /* Theme */
    var overlayTheme = StringSelectionPref(
        titleId = R.string.pref_ovr_theme,
        icon = Phosphor.PaintRoller,
        key = OVERLAY_THEME,
        dataStore = dataStore,
        defaultValue = "auto_system",
        entries = getThemes(context)
    )

    val dynamicColor = BooleanPref(
        titleId = R.string.pref_dynamic_color,
        icon = Phosphor.Swatches,
        key = OVERLAY_DYNAMIC_THEME,
        dataStore = dataStore,
        defaultValue = true
    )

    var overlayTransparency = FloatPref(
        titleId = R.string.pref_transparency,
        icon = Phosphor.SubtractSquare,
        key = OVERLAY_OPACITY,
        dataStore = dataStore,
        defaultValue = 1f,
        maxValue = 1f,
        minValue = 0f,
        steps = 100,
        specialOutputs = { "${(it * 100).roundToInt()}%" }
    )

    /**
     * Where tapping an article takes you.
     *
     * Replaces the old openInBrowser/offlineReader boolean pair, which encoded
     * three modes across two switches and required the user to guess the
     * combination. "Offline reader" was the worst of it: it selected the built-in
     * reader but read as if it controlled caching, which it never did — articles
     * are cached at sync time regardless of this setting (see RssLocalSync).
     */
    var articleOpenMode = StringSelectionPref(
        titleId = R.string.pref_article_open_mode,
        icon = Phosphor.Browser,
        key = ARTICLE_OPEN_MODE,
        dataStore = dataStore,
        defaultValue = OPEN_MODE_READER,
        entries = mapOf(
            OPEN_MODE_READER to context.getString(R.string.article_open_reader),
            OPEN_MODE_BROWSER to context.getString(R.string.article_open_browser),
        )
    )

    var removeDuplicates = BooleanPref(
        titleId = R.string.pref_remove_duplicates,
        icon = Phosphor.FunnelSimple,
        key = REMOVE_DUPLICATES,
        dataStore = dataStore,
        defaultValue = true
    )

    /*SAVE UTILITY PREF*/
    var showBookmarks = BooleanPref(
        titleId = R.string.title_bookmarks,
        icon = Phosphor.BookBookmark,
        key = SHOW_BOOKMARKS,
        dataStore = dataStore,
        defaultValue = false
    )

    /* Sync */
    var syncOnlyOnWifi = BooleanPref(
        titleId = R.string.pref_sync_wifi,
        icon = Phosphor.WifiHigh,
        key = SYNC_ON_WIFI,
        dataStore = dataStore,
        defaultValue = true
    )

    var syncFrequency = StringSelectionPref(
        titleId = R.string.pref_sync_frequency,
        icon = Phosphor.Clock,
        key = SYNC_FREQUENCY,
        dataStore = dataStore,
        defaultValue = "1",
        entries = getSyncFrequency(context)
    )

    var syncRange = StringSelectionPref(
        titleId = R.string.pref_sync_range,
        icon = Phosphor.Clock,
        key = SYNC_RANGE,
        dataStore = dataStore,
        defaultValue = "1w",
        entries = getSyncRange(context)
    )

    var itemsPerFeed = StringSelectionPref(
        titleId = R.string.pref_items_per_feed,
        icon = Phosphor.Hash,
        key = ITEMS_PER_FEED,
        dataStore = dataStore,
        defaultValue = "25",
        entries = getItemsPerFeed()
    )

    var mastodonItemsPerFeed = StringSelectionPref(
        titleId = R.string.pref_mastodon_items_per_feed,
        icon = Phosphor.Hash,
        key = MASTODON_ITEMS_PER_FEED,
        dataStore = dataStore,
        defaultValue = "20",
        entries = getMastodonItemsPerFeed()
    )

    var blockedWords = StringSetPref(
        titleId = R.string.pref_blocked_words,
        summaryId = R.string.pref_blocked_words_summary,
        icon = Phosphor.Hash,
        key = BLOCKED_WORDS,
        dataStore = dataStore,
        defaultValue = emptySet(),
        route = NavRoute.BlockedWords,
    )

    /* Others */
    var enabledPlugins = StringSetPref(
        titleId = R.string.title_plugin_list,
        icon = Phosphor.Hash,
        key = PLUGINS,
        dataStore = dataStore,
        defaultValue = setOf()
    )

    var about = StringPref(
        titleId = R.string.title_about,
        icon = Phosphor.Info,
        key = ABOUT,
        dataStore = get(),
        route = NavRoute.About
    )

    /**
     * Writes a diagnostics report to Downloads.
     *
     * Deliberately reachable from the phone alone: this app is developed and
     * tested on device, where adb needs a network that is not always available,
     * and an app can read its own logcat without any permission. See Diagnostics.
     */
    var exportDiagnostics = StringPref(
        titleId = R.string.pref_export_diagnostics,
        summaryId = R.string.pref_export_diagnostics_summary,
        icon = Phosphor.Bug,
        key = EXPORT_DIAGNOSTICS,
        dataStore = dataStore,
        onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                val location = Diagnostics.export(context)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        location?.let { context.getString(R.string.diagnostics_saved, it) }
                            ?: context.getString(R.string.diagnostics_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )

    var debugging = BooleanPref(
        titleId = R.string.debug_logcat_printing,
        defaultValue = false,
        icon = Phosphor.Bug,
        key = DEBUG,
        dataStore = dataStore,
    )

    /* Sort & Filter */
    var sourcesFilter = StringSetPref(
        titleId = R.string.title_sources,
        icon = Phosphor.Info,
        key = FILTER_SOURCES,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    var tagsFilter = StringSetPref(
        titleId = R.string.source_tags,
        icon = Phosphor.Info,
        key = FILTER_TAGS,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    var sortingFilter = StringSelectionPref(
        titleId = R.string.sorting_order,
        icon = Phosphor.Info,
        key = FILTER_SORT,
        dataStore = dataStore,
        defaultValue = SORT_CHRONOLOGICAL,
        entries = getSortingOptions(context),
    )

    var sortingAsc = BooleanPref(
        titleId = R.string.sorting_order,
        defaultValue = false,
        icon = Phosphor.CaretUp,
        key = FILTER_SORT_ASC,
        dataStore = dataStore,
    )

    companion object {
        val prefsModule = module {
            singleOf(::FeedPreferences)
            singleOf(::provideDataStore)
        }

        private fun provideDataStore(context: Context): DataStore<Preferences> {
            return PreferenceDataStoreFactory.create(
                produceFile = {
                    context.preferencesDataStoreFile("neo_feed")
                },
                migrations = listOf(
                    SharedPreferencesMigration(
                        context,
                        "com.saulhdev.neofeed.prefs"
                    )
                )
            )
        }

        val OVERLAY_THEME = stringPreferencesKey("pref_overlay_theme")
        val OVERLAY_DYNAMIC_THEME = booleanPreferencesKey("pref_dynamic_theme")
        val OVERLAY_OPACITY = floatPreferencesKey("pref_overlay_opacity")
        val ARTICLE_OPEN_MODE = stringPreferencesKey("pref_article_open_mode")
        val EXPORT_DIAGNOSTICS = stringPreferencesKey("pref_export_diagnostics")

        /** Open the tapped article in 076 Feed's own reader, using cached content. */
        const val OPEN_MODE_READER = "reader"

        /** Hand the article's URL to the device's default browser. */
        const val OPEN_MODE_BROWSER = "browser"
        val REMOVE_DUPLICATES = booleanPreferencesKey("pref_remove_duplicates")
        val SHOW_BOOKMARKS = booleanPreferencesKey("pref_show_bookmarks")
        val SYNC_ON_WIFI = booleanPreferencesKey("pref_sync_only_wifi")
        val SYNC_FREQUENCY = stringPreferencesKey("pref_sync_frequency")
        val SYNC_RANGE = stringPreferencesKey("pref_sync_range")
        val ITEMS_PER_FEED = stringPreferencesKey("pref_items_per_feed")
        val MASTODON_ITEMS_PER_FEED = stringPreferencesKey("pref_mastodon_items_per_feed")
        val BLOCKED_WORDS = stringSetPreferencesKey("pref_blocked_words")
        val PLUGINS = stringSetPreferencesKey("pref_enabled_plugins")
        val ABOUT = stringPreferencesKey("pref_about")
        val DEBUG = booleanPreferencesKey("pref_debugging")

        // Filter & Sort
        val FILTER_SOURCES = stringSetPreferencesKey("filter_sources")
        val FILTER_TAGS = stringSetPreferencesKey("filter_tags")
        val FILTER_SORT = stringPreferencesKey("filter_sorting")
        val FILTER_SORT_ASC = booleanPreferencesKey("filter_sorting_ascending")
    }
}