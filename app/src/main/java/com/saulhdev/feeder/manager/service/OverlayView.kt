package com.saulhdev.feeder.manager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import com.google.android.libraries.gsa.d.a.OverlayController
import com.saulhdev.feeder.MainActivity
import com.saulhdev.feeder.NeoApp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.navigation.Routes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.saulhdev.feeder.data.repository.SourcesRepository
import androidx.compose.ui.platform.LocalDensity
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.FeedScaffold
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.extensions.safeShareIntent
import androidx.compose.material3.MaterialTheme
import com.saulhdev.feeder.manager.glance.GlanceState
import com.saulhdev.feeder.manager.glance.GlanceStateHolder
import com.saulhdev.feeder.ui.theme.CardTheme
import com.saulhdev.feeder.ui.theme.OverlayTheme
import com.saulhdev.feeder.ui.theme.fontFamilyFor
import com.saulhdev.feeder.ui.theme.typographyFor
import com.saulhdev.feeder.ui.theme.OverlayThemeHolder
import com.saulhdev.feeder.ui.theme.WhisperShapes
import com.saulhdev.feeder.utils.extensions.safeStartActivity
import com.saulhdev.feeder.utils.extensions.setCustomTheme
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

class OverlayView(val context: Context) :
    OverlayController(context, R.style.AppTheme, R.style.WindowTheme),
    KoinComponent, OverlayBridge.OverlayBridgeCallback {
    private lateinit var themeHolder: OverlayThemeHolder
    private val syncScope = CoroutineScope(Dispatchers.IO) + CoroutineName("NeoFeedSync")
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val viewModel: ArticleListViewModel by inject(ArticleListViewModel::class.java)
    private val articles: SyncRestClient by inject(SyncRestClient::class.java)
    val prefs: FeedPreferences by inject()
    private val sourcesRepo: SourcesRepository by inject()
    private val composeHost = OverlayComposeHost()

    /**
     * The Material 3 scheme the overlay is currently drawn with.
     *
     * Compose content and the legacy View code both derive from this one value,
     * so they cannot disagree. It replaces an earlier stopgap that tracked only
     * whether the background was dark; see OverlayTheme.
     */
    private val overlayScheme = mutableStateOf(
        OverlayTheme.schemeFor(context, "auto_system", dynamic = true)
    )

    private var feedAdded = false

    /** Feed content, mirrored from the view model into Compose state. */
    private val articlesState = mutableStateOf<List<FeedItem>>(emptyList())
    private val bookmarksState = mutableStateOf<List<FeedItem>>(emptyList())
    private val isSyncingState = mutableStateOf(false)

    /** Typeface preference, mirrored so the overlay matches the app. */
    private val overlayFont = mutableStateOf(prefs.appFont.getValue())

    /**
     * Overlay background transparency, mirrored from preferences.
     *
     * onScroll runs on every frame of the launcher's swipe, and reading this
     * from DataStore there blocked each frame on a disk read.
     */
    @Volatile
    private var overlayAlpha = prefs.overlayTransparency.getValue()

    /** Last colour handed to the window, so repeat frames do no work. */
    private var lastBackgroundColor: Int? = null

    /** Weather, sunset and counts for the strip above the categories. */
    private val glanceHolder: GlanceStateHolder by inject()
    private val glanceState = mutableStateOf(GlanceState())
    private val showBookmarks = mutableStateOf(false)

    /** The source hidden most recently, so the feed can offer the way back. */
    private val hiddenSourceState = mutableStateOf<FeedItem?>(null)

    /** Mirrored from the view model, which owns the query the feed is filtered by. */
    private val searchQuery = mutableStateOf("")
    private val isFilterActive = mutableStateOf(false)

    /**
     * Whether the sort-and-filter sheet is showing.
     *
     * Held here rather than inside the composition so that the hardware back
     * gesture can close the sheet instead of the whole panel — the sheet is
     * drawn in this window's own Compose tree, so nothing else knows it is up.
     */
    private val filterSheetOpen = mutableStateOf(false)

    /** Whether the header has been swapped for the search field. */
    private val searching = mutableStateOf(false)

    /**
     * System bar insets, in pixels, as reported to the overlay's root view.
     *
     * Passed into Compose rather than read there via WindowInsets: this window
     * is created against the launcher's token with an unusual flag set, and the
     * overlay's own inset listener is the value already known to be right.
     */
    private val topInsetPx = mutableStateOf(0)
    private val bottomInsetPx = mutableStateOf(0)

    private var pendingCloseOnResume = false

    /**
     * Set while an activity this overlay started is in front of the launcher.
     *
     * Tapping an article showed the home screen wallpaper and then the
     * browser, and backing out of the browser landed on the home screen rather
     * than on the feed. The cause is in the overlay protocol, not in this app's
     * own handling: the launcher calls ILauncherOverlay.onPause() when it
     * pauses, which OverlayControllerBinder turns into closeOverlay(0), which
     * arrives here as closePanelIfNeeded(0) — and flag 0 means *no animation*,
     * so the panel snaps shut and reveals the workspace while the browser is
     * still starting. The launcher then calls onResume() on the way back,
     * which closes it a second time.
     *
     * Both closes are consequences of us starting the activity, so while this
     * is set both are ignored. It cannot be a short time window: the user is
     * in the browser for as long as they like, and the resume arrives at the
     * end of that.
     */
    private var keepingPanelForOurLaunch = false

    /**
     * Starts an activity and leaves the panel open behind it.
     *
     * Every launch from the overlay goes through here, not only opening an
     * article: a share sheet or the settings screen appearing over the feed
     * has the same reason to leave the feed underneath it.
     */
    private inline fun launchKeepingPanel(block: () -> Unit) {
        keepingPanelForOurLaunch = true
        block()
    }

    private lateinit var rootView: View

    private val closeSystemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
            // Something else taking the screen is worth closing for. Us taking
            // it is not — see keepingPanelForOurLaunch.
            if (keepingPanelForOurLaunch) return
            closePanelIfNeeded(1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Owners must exist before any Compose content is added on window attach.
        // slidingPanelLayout is the load-bearing one: it is what Compose resolves
        // as the window's content child, and owners placed below it are not found.
        // Both it and the container are assigned before this runs. See
        // OverlayComposeHost.attachTo for why the node matters.
        composeHost.onCreate()
        composeHost.attachTo(this.slidingPanelLayout, this.container)

        rootView = View.inflate(
            ContextThemeWrapper(this, R.style.AppTheme),
            R.layout.overlay_layout,
            this.container
        )
        val mainContainer = rootView.findViewById<ViewGroup>(R.id.overlay_root)

        themeHolder = OverlayThemeHolder(this)

        val bgColor = themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal)
        getWindow().setBackgroundDrawable((bgColor and 0x00ffffff).toDrawable())

        initInsets()
        // Compose content can only go in once the window exists, and the attach
        // itself is the reliable signal for that — unlike onResume. post() keeps
        // the insertion out of the attach traversal, where mutating the hierarchy
        // is unsafe.
        rootView.doOnAttach { it.post { initFeed() } }
        refreshNotifications()

        syncScope.launch {
            viewModel.articleListState.collect {
                articlesState.value = it.articles
                isSyncingState.value = it.isSyncing
                isFilterActive.value = it.isFilterModified
            }
        }
        syncScope.launch {
            viewModel.bookmarksState.collect { bookmarksState.value = it.bookmarkedArticles }
        }
        syncScope.launch {
            viewModel.recentlyHidden.collect { hiddenSourceState.value = it }
        }
        syncScope.launch {
            viewModel.searchQuery.collect { searchQuery.value = it }
        }
        syncScope.launch {
            prefs.appFont.get().collect { overlayFont.value = it }
        }
        syncScope.launch {
            prefs.overlayTransparency.get().collect { overlayAlpha = it }
        }
        syncScope.launch {
            prefs.pureBlack.get().drop(1).collect { mainScope.launch { updateTheme() } }
        }
        syncScope.launch {
            glanceHolder.state.collect { glanceState.value = it }
        }
        glanceHolder.refreshIfStale()
        syncScope.launch {
            prefs.overlayTheme.get().collect {
                mainScope.launch {
                    applyNewTheme(it)
                }
            }
        }
        // Dynamic colour now feeds the overlay's scheme too, so a change to it
        // has to rebuild the theme exactly as a theme-mode change does.
        syncScope.launch {
            prefs.dynamicColor.get().drop(1).collect {
                mainScope.launch { updateTheme() }
            }
        }
        NeoApp.bridge.setCallback(this)

        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(closeSystemDialogsReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(closeSystemDialogsReceiver, filter)
        }
    }

    override fun closePanelIfNeeded(flags: Int) {
        if (keepingPanelForOurLaunch) {
            // The launcher asks twice: once without the animation flag as it
            // pauses, once with it as it resumes. The resume one is the end of
            // the trip, so it is what clears this — leaving the panel open and
            // the feed exactly where the article was tapped from.
            if (flags and 1 != 0) keepingPanelForOurLaunch = false
            return
        }
        super.closePanelIfNeeded(flags)
    }

    override fun onBackPressed() {
        if (filterSheetOpen.value) {
            filterSheetOpen.value = false
            return
        }
        if (searching.value) {
            searching.value = false
            viewModel.setSearchQuery("")
            return
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        // Backstop only. onResume is not guaranteed here: upstream fires it from
        // updateActivityState(), i.e. when the launcher signals a resume, which a
        // swipe into the overlay does not necessarily do. The attach hook set up
        // in onCreate is what actually drives this; both are idempotent.
        initFeed()
        if (pendingCloseOnResume) {
            pendingCloseOnResume = false
            closePanelIfNeeded(1)
        }
    }

    private fun updateTheme(force: String? = null) {
        // The feed reads overlayScheme directly, so setting the scheme is the
        // whole update; there are no View widgets left here to re-tint.
        setTheme(force)
    }

    private fun setTheme(force: String?) {
        val scheme = OverlayTheme.schemeFor(
            context = context,
            mode = force ?: prefs.overlayTheme.getValue(),
            // Previously ignored on this surface: the overlay always read the
            // dynamic system colours regardless of what the user had chosen.
            dynamic = prefs.dynamicColor.getValue(),
            pureBlack = prefs.pureBlack.getValue(),
        )
        overlayScheme.value = scheme
        themeHolder.setTheme(
            with(OverlayTheme) { scheme.toCardColors(OverlayTheme.isLight(scheme)) }
        )
        setCustomTheme()
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun getNavigationBarHeight(): Int {
        val resourceId =
            context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun applyInsets(statusBarTop: Int, navBarBottom: Int, left: Int = 0, right: Int = 0) {
        topInsetPx.value = maxOf(statusBarTop, getStatusBarHeight())
        bottomInsetPx.value = maxOf(navBarBottom, getNavigationBarHeight())
    }

    private fun initInsets() {
        applyInsets(getStatusBarHeight(), getNavigationBarHeight())

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            applyInsets(insets.top, insets.bottom, insets.left, insets.right)
            windowInsets
        }

        rootView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                ViewCompat.requestApplyInsets(v)
            }

            override fun onViewDetachedFromWindow(v: View) {}
        })
    }


    /**
     * Adds the Compose feed, once, after the overlay's window exists.
     *
     * Deliberately not done in [onCreate]: composition is created when a
     * ComposeView attaches to a window, and during onCreate the overlay's window
     * has not been added yet, so the attach — and any failure in it — would
     * happen inside upstream's Java setup where it cannot be caught. Adding the
     * view to an already-attached parent makes the attach synchronous, so a
     * failure is catchable here.
     */
    private fun initFeed() {
        if (feedAdded) return
        val host = rootView.findViewById<ViewGroup>(R.id.feed_host) ?: return
        feedAdded = true

        try {
            host.addView(ComposeView(host.context).apply { setFeedContent() })
        } catch (t: Throwable) {
            Log.e("OverlayView", "Feed failed to compose", t)
            host.removeAllViews()
        }
    }

    private fun ComposeView.setFeedContent() {
        setContent {
            MaterialTheme(
                colorScheme = overlayScheme.value,
                typography = typographyFor(fontFamilyFor(overlayFont.value)),
                shapes = WhisperShapes,
            ) {
                val density = LocalDensity.current
                val categories by sourcesRepo.getAllTagsFlow()
                    .collectAsState(initial = emptyList())
                val selected by prefs.categoryFilter.get()
                    .collectAsState(initial = emptySet())
                val layout by prefs.feedLayout.get()
                    .collectAsState(initial = LAYOUT_CARDS)

                FeedScaffold(
                    articles = if (showBookmarks.value) bookmarksState.value
                    else articlesState.value,
                    categories = categories,
                    selectedCategories = selected,
                    isRefreshing = isSyncingState.value,
                    isFilterActive = isFilterActive.value,
                    isShowingBookmarks = showBookmarks.value,
                    glanceState = glanceState.value,
                    topInset = with(density) { topInsetPx.value.toDp() },
                    bottomInset = with(density) { bottomInsetPx.value.toDp() },
                    // setValue blocks on the datastore write, so keep it off the
                    // main thread; the feed updates through the existing flow.
                    onCategoriesChange = { syncScope.launch { prefs.categoryFilter.setValue(it) } },
                    onRefresh = { refreshNotifications() },
                    onArticleClick = { openArticle(it) },
                    onBookmark = { item, on -> viewModel.bookmarkArticle(item.id, on) },
                    onShare = {
                        launchKeepingPanel {
                            context.safeShareIntent(it.link, it.contentTitle)
                        }
                    },
                    onArticleSeen = { viewModel.markReadOnScroll(it.id) },
                    onPin = { item, pinned -> viewModel.setPinned(item.id, pinned) },
                    onMoreLikeThis = { viewModel.recordAffinity(it.sourceId, 1) },
                    onLessLikeThis = { viewModel.recordAffinity(it.sourceId, -1) },
                    onHideSource = { viewModel.hideSource(it) },
                    hiddenSource = hiddenSourceState.value,
                    onUndoHideSource = { viewModel.undoHideSource() },
                    onDismissHideSource = { viewModel.forgetHiddenSource() },
                    layout = layout,
                    isFilterSheetOpen = filterSheetOpen.value,
                    onFilterSheetOpenChange = { filterSheetOpen.value = it },
                    searchQuery = searchQuery.value,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    isSearching = searching.value,
                    onSearchingChange = {
                        searching.value = it
                        if (!it) viewModel.setSearchQuery("")
                    },
                    // Replaces a click handler that started a new collector on the
                    // view model every press without ever cancelling the previous
                    // one; both lists are now collected once and simply chosen
                    // between here.
                    onBookmarksClick = { showBookmarks.value = !showBookmarks.value },
                    onSettings = {
                        launchKeepingPanel {
                            context.safeStartActivity(
                                MainActivity.navigateIntent(context, Routes.SETTINGS)
                            )
                        }
                    },
                )
            }
        }
    }

    private fun openArticle(item: FeedItem) {
        // Counts on the glance row have to reflect reading done here too, not
        // only in the app — this is the surface most articles are opened from.
        syncScope.launch { viewModel.markRead(item.id) }
        launchKeepingPanel {
            if (prefs.articleOpenMode.getValue() == FeedPreferences.OPEN_MODE_BROWSER) {
                context.launchView(item.link)
            } else {
                context.safeStartActivity(
                    MainActivity.navigateIntent(context, "${Routes.ARTICLE_VIEW}/${item.id}")
                )
            }
        }
    }

    override fun onDestroy() {
        try {
            context.unregisterReceiver(closeSystemDialogsReceiver)
        } catch (_: Exception) {
        }
        composeHost.onDestroy()
        super.onDestroy()
        NeoApp.bridge.setCallback(null)
    }

    override fun onScroll(f: Float) {
        super.onScroll(f)

        val bgColor = themeHolder.currentTheme.get(CardTheme.Colors.OVERLAY_BG.ordinal)
        val alpha = if (f <= 0f) 0f else overlayAlpha
        val color = (alpha * 255.0f).toInt() shl 24 or (bgColor and 0x00ffffff)
        if (color == lastBackgroundColor) return
        lastBackgroundColor = color
        getWindow().setBackgroundDrawable(color.toDrawable())
    }

    override fun onClientMessage(action: String) {
        if (prefs.debugging.getValue()) {
            Log.d("OverlayView", "New message by OverlayBridge: $action")
        }
        if (action == "openContentView" && !keepingPanelForOurLaunch) {
            pendingCloseOnResume = true
        }
    }

    override fun applyNewTheme(value: String) {
        updateTheme(value)
    }

    override fun applyNewTransparency(value: Float) {
        themeHolder.prefs.overlayTransparency.setValue(value)
    }

    override fun applyCompactCard(value: Boolean) {
        // Upstream swapped the RecyclerView's adapter here to switch card
        // density. There is no adapter now, and "compact" is really one of the
        // layout modes the design calls for (Cards, Magazine, List, Mosaic),
        // so it belongs with those rather than as a lone boolean. The callback
        // stays wired and refreshes; the density itself does nothing until the
        // layout modes land. It is not currently reachable from settings.
        refreshNotifications()
    }

    private fun refreshNotifications() {
        syncScope.launch {
            articles.syncAllFeeds()
        }
    }

}
