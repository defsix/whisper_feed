package com.saulhdev.feeder

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import androidx.multidex.MultiDexApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import okhttp3.OkHttpClient
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.jakewharton.threetenabp.AndroidThreeTen
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.FeedPreferences.Companion.prefsModule
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.discovery.DiscoveryWorker
import com.saulhdev.feeder.manager.service.OverlayBridge
import com.saulhdev.feeder.utils.ApplicationCoroutineScope
import com.saulhdev.feeder.utils.MainThreadWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.GlobalContext
import org.koin.dsl.koinConfiguration
import org.koin.java.KoinJavaComponent.inject

@OptIn(KoinExperimentalAPI::class)
class NeoApp : MultiDexApplication(), KoinStartup, ImageLoaderFactory {
    val activityHandler = ActivityHandler()
    // Built by coreModule now, not here; see AppModules.kt.
    private val applicationCoroutineScope: ApplicationCoroutineScope by inject(
        ApplicationCoroutineScope::class.java
    )
    private val wm: WorkManager by inject(WorkManager::class.java)

    fun onAppStarted() {
        registerActivityLifecycleCallbacks(activityHandler)
        stampOnboardingForExistingInstalls()
        DiscoveryWorker.schedule(this)
    }

    /**
     * Tells an upgrade from a fresh install, once.
     *
     * There is no version number to compare against — the preference simply
     * does not exist on either — so the question is answered by the only
     * honest signal available: an install that already has sources has been
     * used, and somebody who has been reading with this for a year must not be
     * welcomed to it as though they had just arrived.
     *
     * A genuinely new install has no sources, so this does nothing and the
     * welcome runs. It stays correct if they close the app before finishing:
     * still no sources, so they are still new.
     */
    private fun stampOnboardingForExistingInstalls() {
        applicationCoroutineScope.launch(Dispatchers.IO) {
            val prefs: FeedPreferences = get()
            if (prefs.onboardingSeen.getValue()) return@launch
            val sources: SourcesRepository = get()
            if (sources.getAllSources().isNotEmpty()) {
                prefs.onboardingSeen.setValue(true)
                prefs.tourSeen.setValue(true)
            }
        }
    }

    @KoinExperimentalAPI
    override fun onKoinStartup() = koinConfiguration {
        androidLogger()
        androidContext(this@NeoApp)
        modules(coreModule, prefsModule, dataModule, modelModule)
    }

    override fun onCreate() {
        super.onCreate()
        // First, so it sees everything that follows — including this class's
        // own startup work, which is where main-thread disk reads hide best.
        MainThreadWatch.install()
        instance = this
        // TODO remove on future release
        AndroidThreeTen.init(this)
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            com.google.android.material.color.DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> DynamicColors.isDynamicColorAvailable() }
                .build()
        )
        wm.pruneWork()
        carryOverHiddenSources()
        reapRemovedSourcesOnUnsave()
        onAppStarted()
    }

    /**
     * Turns off anything hidden under the old two-state arrangement.
     *
     * Hiding a source and switching it off used to be separate: both kept its
     * articles out of the feed, and only one of them stopped it syncing.
     * They are one thing now, so a source somebody hid before this release
     * has to end up off rather than quietly coming back the moment the set
     * stopped being read.
     *
     * Runs once and clears the set, so it costs a preference read on
     * subsequent starts and nothing else. Off the main thread, because it is
     * a DataStore read and a database write and neither belongs in onCreate.
     */
    /**
     * Drops a removed source when the last thing saved from it is unsaved.
     *
     * Wired here because articles cannot hold a reference to sources without
     * making a cycle of a dependency that already runs the other way.
     */
    private fun reapRemovedSourcesOnUnsave() {
        val articles: ArticleRepository by inject(ArticleRepository::class.java)
        val sources: SourcesRepository by inject(SourcesRepository::class.java)
        articles.onSavedRemoved = { feedId ->
            applicationCoroutineScope.launch(Dispatchers.IO) {
                runCatching { sources.reapIfEmpty(feedId) }
            }
        }
    }

    private fun carryOverHiddenSources() {
        val prefs: FeedPreferences by inject(FeedPreferences::class.java)
        val sources: SourcesRepository by inject(SourcesRepository::class.java)
        applicationCoroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val hidden = prefs.hiddenSources.getValue()
                if (hidden.isEmpty()) return@runCatching
                sources.setEnabled(hidden.mapNotNull(String::toLongOrNull), enabled = false)
                prefs.hiddenSources.setValue(emptySet())
            }
        }
    }

    /**
     * The one image loader, instead of the default Coil builds for itself.
     *
     * Two reasons, and the first is the same one the feed and full-text
     * clients have: every `<img src>` in the app comes out of somebody else's
     * XML, so an article can point the phone at an address on its own network
     * and learn whether it answered. Images went out through a client with no
     * such guard.
     *
     * The second is that the default loader keeps no disk cache tuned for this
     * and a memory cache sized for a gallery app. A feed is a long scroll of
     * pictures the reader passes once, so the useful cache is on disk, where a
     * revisit is free and the heap is not the thing paying for it.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .onlyPublicHttps()
                .build()
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                .build()
        }
        // Feed cards are small and fixed; decoding a 4000px newspaper photo at
        // full size to draw it 300px wide is where a scroll's memory goes.
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()

    override fun onTerminate() {
        super.onTerminate()
        GlobalContext.get().close()
    }

    companion object {
        private const val TAG = "NeoFeed"

        @JvmStatic
        var instance: NeoApp? = null
            private set

        val bridge = OverlayBridge()
    }
}

class ActivityHandler : ActivityLifecycleCallbacks {
    val activities = HashSet<Activity>()
    var foregroundActivity: Activity? = null
    private var startedActivities = 0


    fun finishAll(recreateApp: Boolean = true) {
        HashSet(activities).forEach { if (recreateApp) it.recreate() else it.finish() }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity == foregroundActivity) {
            foregroundActivity = null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        foregroundActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity == foregroundActivity)
            foregroundActivity = null
        activities.remove(activity)
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activities.add(activity)
    }
}

/**
 * How much disk the image cache may take: 128 MB.
 *
 * Coil's default is 2% of the free space on the data partition, which on a
 * mostly empty phone is gigabytes for pictures nobody will look at twice, and
 * on a full one is a few megabytes — the wrong way round on both.
 */
private const val IMAGE_DISK_CACHE_BYTES = 128L * 1024 * 1024
