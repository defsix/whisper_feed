package com.saulhdev.feeder

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.multidex.MultiDexApplication
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.saulhdev.feeder.manager.bookmarks.BlockPrivateNetworks
import okhttp3.OkHttpClient
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.jakewharton.threetenabp.AndroidThreeTen
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.FeedPreferences.Companion.prefsModule
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.manager.glance.GlanceStateHolder
import com.saulhdev.feeder.manager.glance.WeatherRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.bookmarks.FeedDiscovery
import com.saulhdev.feeder.manager.discovery.DiscoveryWorker
import com.saulhdev.feeder.manager.mastodon.MastodonApi
import com.saulhdev.feeder.manager.mastodon.MastodonAuth
import com.saulhdev.feeder.manager.mastodon.MastodonStorage
import com.saulhdev.feeder.manager.service.OverlayBridge
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.utils.ApplicationCoroutineScope
import com.saulhdev.feeder.utils.extensions.ToastMaker
import com.saulhdev.feeder.utils.extensions.restartApp
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import com.saulhdev.feeder.viewmodels.ArticleViewModel
import com.saulhdev.feeder.viewmodels.MastodonAuthViewModel
import com.saulhdev.feeder.viewmodels.SearchFeedViewModel
import com.saulhdev.feeder.viewmodels.SortFilterViewModel
import com.saulhdev.feeder.data.content.SyncAccount
import com.saulhdev.feeder.manager.backup.BackupStore
import com.saulhdev.feeder.manager.sync.service.GoogleReaderService
import com.saulhdev.feeder.manager.sync.service.LocalRssService
import com.saulhdev.feeder.manager.sync.service.RssServiceDispatcher
import com.saulhdev.feeder.viewmodels.AccountViewModel
import com.saulhdev.feeder.viewmodels.LearnedViewModel
import com.saulhdev.feeder.viewmodels.SourceEditViewModel
import com.saulhdev.feeder.viewmodels.SourceListViewModel
import com.saulhdev.feeder.viewmodels.BookmarkImportViewModel
import com.saulhdev.feeder.viewmodels.BrokenFeedsViewModel
import com.saulhdev.feeder.viewmodels.SuggestionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.GlobalContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject

@OptIn(KoinExperimentalAPI::class)
class NeoApp : MultiDexApplication(), KoinStartup, ImageLoaderFactory {
    val activityHandler = ActivityHandler()
    private val applicationCoroutineScope = ApplicationCoroutineScope()
    private val wm: WorkManager by inject(WorkManager::class.java)

    private fun savedStateHandle() = SavedStateHandle()

    private val modelModule = module {
        single {
            savedStateHandle()
        }
        viewModelOf(::SourceEditViewModel)
        viewModelOf(::LearnedViewModel)
        viewModelOf(::AccountViewModel)
        viewModelOf(::SearchFeedViewModel)
        viewModelOf(::ArticleListViewModel)
        viewModelOf(::SourceListViewModel)
        viewModelOf(::ArticleViewModel)
        viewModelOf(::SortFilterViewModel)
        viewModelOf(::SuggestionsViewModel)
        viewModelOf(::BookmarkImportViewModel)
        viewModelOf(::BrokenFeedsViewModel)
        viewModelOf(::MastodonAuthViewModel)
    }

    // TODO Move to its class
    private val dataModule = module {
        single<NeoFeedDb> { NeoFeedDb.getInstance(this@NeoApp) }
        single { get<NeoFeedDb>().feedArticleDao() }
        single { get<NeoFeedDb>().feedSourceDao() }
        singleOf(::ArticleRepository)
        singleOf(::SourcesRepository)
        singleOf(::SyncRestClient)
        singleOf(::MastodonStorage)
        singleOf(::MastodonAuth)
        singleOf(::MastodonApi)
        singleOf(::FeedDiscovery)
        single { SyncAccount(this@NeoApp) }
        single { BackupStore(this@NeoApp, get(), get()) }
        single { LocalRssService(this@NeoApp, get()) }
        single {
            RssServiceDispatcher(
                account = get(),
                local = get(),
                googleReaderFactory = {
                    GoogleReaderService(this@NeoApp, get(), get(), get())
                },
            )
        }
        singleOf(::WeatherRepository)
        singleOf(::GlanceStateHolder)
    }

    private val coreModule = module {
        single { contentResolver }
        single { WorkManager.getInstance(this@NeoApp) }
        single<ToastMaker> {
            object : ToastMaker {
                override suspend fun makeToast(text: String) = withContext(Dispatchers.Main) {
                    Toast.makeText(get(), text, Toast.LENGTH_SHORT).show()
                }

                override suspend fun makeToast(resId: Int) = withContext(Dispatchers.Main) {
                    Toast.makeText(get(), resId, Toast.LENGTH_SHORT).show()
                }
            }
        }
        single { applicationCoroutineScope }
        single<NeoApp> { this@NeoApp }
    }

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
        onAppStarted()
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
                .addNetworkInterceptor(BlockPrivateNetworks())
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

    fun restart(recreate: Boolean = false) {
        if (recreate) {
            activityHandler.finishAll(true)
        } else {
            restartApp()
        }
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
