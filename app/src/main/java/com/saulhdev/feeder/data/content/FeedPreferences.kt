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
import com.saulhdev.feeder.ui.icons.phosphor.ListDashes
import com.saulhdev.feeder.utils.getFeedLayouts
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.ui.icons.phosphor.BookBookmark
import com.saulhdev.feeder.ui.icons.phosphor.BracketsSquare
import com.saulhdev.feeder.ui.icons.phosphor.Browser
import com.saulhdev.feeder.ui.icons.phosphor.Bug
import com.saulhdev.feeder.ui.icons.phosphor.CaretDown
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.Circle
import com.saulhdev.feeder.ui.icons.phosphor.Clock
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.utils.READ_KEEP
import com.saulhdev.feeder.utils.getReadVisibility
import com.saulhdev.feeder.ui.icons.phosphor.EyeSlash
import com.saulhdev.feeder.ui.icons.phosphor.FunnelSimple
import com.saulhdev.feeder.ui.icons.phosphor.Hash
import com.saulhdev.feeder.ui.icons.phosphor.Info
import com.saulhdev.feeder.ui.icons.phosphor.Megaphone
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
import com.saulhdev.feeder.utils.LEGACY_THEME_BLACK
import com.saulhdev.feeder.utils.LEGACY_THEME_SYSTEM_BLACK
import com.saulhdev.feeder.utils.THEME_DARK
import com.saulhdev.feeder.utils.THEME_FOLLOW_SYSTEM
import com.saulhdev.feeder.utils.getFonts
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

    /**
     * Moves anyone off the retired theme values.
     *
     * "black" and "auto_system_black" are no longer entries, so a preference
     * still holding one would leave the theme dialog with nothing selected.
     * Each maps onto its base mode plus the pure-black switch, which is what
     * they always meant.
     */
    private fun migrateRetiredThemeValues() {
        CoroutineScope(Dispatchers.IO).launch {
            when (overlayTheme.getValue()) {
                LEGACY_THEME_BLACK -> {
                    overlayTheme.setValue(THEME_DARK)
                    pureBlack.setValue(true)
                }

                LEGACY_THEME_SYSTEM_BLACK -> {
                    overlayTheme.setValue(THEME_FOLLOW_SYSTEM)
                    pureBlack.setValue(true)
                }
            }
        }
    }
    /* Theme */
    var overlayTheme = StringSelectionPref(
        titleId = R.string.pref_ovr_theme,
        icon = Phosphor.PaintRoller,
        key = OVERLAY_THEME,
        dataStore = dataStore,
        defaultValue = "auto_system",
        entries = getThemes(context)
    )

    /**
     * True black rather than dark grey, for OLED screens.
     *
     * Separate from the theme rather than two more entries in its list. "Dark"
     * and "Black" answered two different questions — whether to be dark, and
     * how dark — and crossing them meant the list had to carry a "follow
     * system, but black" entry as well. One switch alongside three modes says
     * the same thing without the combinatorics.
     */
    val pureBlack = BooleanPref(
        titleId = R.string.pref_pure_black,
        summaryId = R.string.pref_pure_black_summary,
        icon = Phosphor.Circle,
        key = PURE_BLACK,
        dataStore = dataStore,
        defaultValue = false
    )

    /**
     * Wallpaper-derived theming, off by default.
     *
     * Whisper ships looking like Whisper: the brand palette is the default and
     * Material You is opt-in for anyone who would rather the app follow their
     * wallpaper. See OverlayTheme for how the brand scheme is generated.
     */
    val dynamicColor = BooleanPref(
        titleId = R.string.pref_dynamic_color,
        summaryId = R.string.pref_dynamic_color_summary,
        icon = Phosphor.Swatches,
        key = OVERLAY_DYNAMIC_THEME,
        dataStore = dataStore,
        defaultValue = false
    )

    /**
     * Typeface. Inter is the brand face and the default; "System default" hands
     * typography back to the device, which also respects a user's own font
     * choice on OEMs that offer one.
     */
    var appFont = StringSelectionPref(
        titleId = R.string.pref_app_font,
        icon = Phosphor.BracketsSquare,
        key = APP_FONT,
        dataStore = dataStore,
        defaultValue = "inter",
        entries = getFonts(context)
    )

    var feedLayout = StringSelectionPref(
        titleId = R.string.pref_feed_layout,
        icon = Phosphor.ListDashes,
        key = FEED_LAYOUT,
        dataStore = dataStore,
        defaultValue = LAYOUT_CARDS,
        entries = getFeedLayouts(context),
    )

    /* Glance row */

    /**
     * The strip of status chips above the category filters.
     *
     * Off by default: the weather chip is the only part of the app that talks to
     * a third party at all, so it is something a user opts into rather than
     * something they have to discover and turn off.
     */
    val glanceEnabled = BooleanPref(
        titleId = R.string.pref_glance_row,
        summaryId = R.string.pref_glance_row_summary,
        icon = Phosphor.Clock,
        key = GLANCE_ENABLED,
        dataStore = dataStore,
        defaultValue = false
    )

    /** Display name of the chosen place, empty until one is picked. */
    val glancePlaceName = StringPref(
        titleId = R.string.pref_glance_place,
        icon = Phosphor.Clock,
        key = GLANCE_PLACE_NAME,
        dataStore = dataStore,
        defaultValue = "",
        route = NavRoute.GlanceLocation
    )

    /** "lat,lon" for the chosen place. Stored as text to avoid a float pref pair. */
    val glancePlaceCoords = StringPref(
        titleId = R.string.pref_glance_place,
        icon = Phosphor.Clock,
        key = GLANCE_PLACE_COORDS,
        dataStore = dataStore,
        defaultValue = ""
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

    /**
     * Prefetch the full article for every feed, not one feed at a time.
     *
     * The per-source switch stays: this is the blanket answer for people who
     * always want the whole article, and turning it on means never visiting
     * forty source screens to say so. It only widens — a source with its own
     * switch on still prefetches when this is off.
     */
    var fullTextForAllFeeds = BooleanPref(
        titleId = R.string.pref_full_text_all_feeds,
        summaryId = R.string.pref_full_text_all_feeds_summary,
        icon = Phosphor.CloudArrowDown,
        key = FULL_TEXT_ALL_FEEDS,
        dataStore = dataStore,
        defaultValue = false
    )

    /**
     * Volume keys page the feed instead of changing the volume.
     *
     * Off by default — an app quietly taking over the volume keys is a
     * surprise, and this one only pays off for people who read one-handed.
     * See VolumeScroll for why this rather than a motion gesture.
     */
    /**
     * What becomes of an article once it has been opened.
     *
     * Saved and pinned articles are never hidden by this, whatever it is set
     * to: those are the two ways a reader has said "keep this", and a setting
     * about tidying away what is finished should not throw away what was
     * deliberately kept.
     */
    var readVisibility = StringSelectionPref(
        titleId = R.string.pref_read_visibility,
        summaryId = R.string.pref_read_visibility_summary,
        icon = Phosphor.EyeSlash,
        key = READ_VISIBILITY,
        dataStore = dataStore,
        defaultValue = READ_KEEP,
        entries = getReadVisibility(context)
    )

    var volumeKeyScroll = BooleanPref(
        titleId = R.string.pref_volume_key_scroll,
        summaryId = R.string.pref_volume_key_scroll_summary,
        icon = Phosphor.CaretDown,
        key = VOLUME_KEY_SCROLL,
        dataStore = dataStore,
        defaultValue = false
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
    /**
     * The same report, handed to the share sheet instead of to Downloads.
     *
     * Export is right for this phone and useless to anyone else's: it leaves a
     * file in Downloads and expects the person to find it, attach it and send
     * it, and most people stop before the end of that sentence. A tester gets
     * the report into whatever they already use to talk to us, in one tap.
     *
     * No note dialog. Whatever they type in their mail or messaging app is the
     * note, and asking twice for the same sentence is how a report gets
     * abandoned. The subject line carries the version and the device so a
     * report is identifiable before it is opened.
     */
    var reportProblem = StringPref(
        titleId = R.string.report_problem,
        summaryId = R.string.report_problem_summary,
        icon = Phosphor.Megaphone,
        key = REPORT_PROBLEM,
        dataStore = dataStore,
        onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { Diagnostics.share(context, note = "") }
                    .onFailure {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.diagnostics_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
        }
    )

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
    /**
     * Categories the feed is narrowed to — an INCLUDE list, empty meaning "All".
     *
     * Deliberately separate from [tagsFilter], which the filter sheet uses as an
     * EXCLUDE list ("hide these"). Both were previously the same preference read
     * with opposite meanings: the article query treated it as an include list
     * while processArticles excluded exactly what that query had selected, so
     * choosing any category reliably produced an empty feed. Selecting a
     * category to browse and muting a category are different intentions and now
     * have different preferences.
     */
    var categoryFilter = StringSetPref(
        titleId = R.string.all_categories,
        icon = Phosphor.Hash,
        key = CATEGORY_FILTER,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    var sourcesFilter = StringSetPref(
        titleId = R.string.title_sources,
        icon = Phosphor.Info,
        key = FILTER_SOURCES,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    /**
     * Sources the user has asked never to see again, by feed id.
     *
     * Separate from [sourcesFilter] on purpose, even though both end up hiding
     * a source's articles. That one is the filter sheet's scratchpad — set
     * while browsing, reset by the Reset button, and expected to be temporary.
     * This is an answer to "hide this source", which should survive a filter
     * reset and be undone deliberately, from the sources screen.
     */
    var hiddenSources = StringSetPref(
        titleId = R.string.title_sources,
        icon = Phosphor.Info,
        key = HIDDEN_SOURCES,
        dataStore = dataStore,
        defaultValue = emptySet(),
    )

    /**
     * "More like this" and "Less like this", recorded as they are given.
     *
     * Stored as `feedId:score` strings, because DataStore has no map type and a
     * string set is what every other multi-value preference here already uses.
     * Nothing reads this back yet — Milestone 5 will. It is written from the
     * start anyway so that the menu entries do something real from the day they
     * appear, rather than being two buttons that lie until the ranking arrives.
     */
    var sourceAffinity = StringSetPref(
        titleId = R.string.title_sources,
        icon = Phosphor.Info,
        key = SOURCE_AFFINITY,
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

    init {
        migrateRetiredThemeValues()
    }

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
        val APP_FONT = stringPreferencesKey("pref_app_font")
        val PURE_BLACK = booleanPreferencesKey("pref_pure_black")
        val GLANCE_ENABLED = booleanPreferencesKey("pref_glance_enabled")
        val GLANCE_PLACE_NAME = stringPreferencesKey("pref_glance_place_name")
        val GLANCE_PLACE_COORDS = stringPreferencesKey("pref_glance_place_coords")
        val OVERLAY_OPACITY = floatPreferencesKey("pref_overlay_opacity")
        val ARTICLE_OPEN_MODE = stringPreferencesKey("pref_article_open_mode")
        val FEED_LAYOUT = stringPreferencesKey("pref_feed_layout")
        val CATEGORY_FILTER = stringSetPreferencesKey("pref_category_filter")
        val EXPORT_DIAGNOSTICS = stringPreferencesKey("pref_export_diagnostics")
        val REPORT_PROBLEM = stringPreferencesKey("pref_report_problem")

        /** Open the tapped article in Whisper's own reader, using cached content. */
        const val OPEN_MODE_READER = "reader"

        /** Hand the article's URL to the device's default browser. */
        const val OPEN_MODE_BROWSER = "browser"
        val REMOVE_DUPLICATES = booleanPreferencesKey("pref_remove_duplicates")
        val VOLUME_KEY_SCROLL = booleanPreferencesKey("pref_volume_key_scroll")
        val READ_VISIBILITY = stringPreferencesKey("pref_read_visibility")
        val FULL_TEXT_ALL_FEEDS = booleanPreferencesKey("pref_full_text_all_feeds")
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
        val HIDDEN_SOURCES = stringSetPreferencesKey("pref_hidden_sources")
        val SOURCE_AFFINITY = stringSetPreferencesKey("pref_source_affinity")
        val FILTER_TAGS = stringSetPreferencesKey("filter_tags")
        val FILTER_SORT = stringPreferencesKey("filter_sorting")
        val FILTER_SORT_ASC = booleanPreferencesKey("filter_sorting_ascending")
    }
}