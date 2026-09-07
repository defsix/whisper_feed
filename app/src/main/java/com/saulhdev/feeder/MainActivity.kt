package com.saulhdev.feeder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.sync.FeedSyncer
import com.saulhdev.feeder.ui.navigation.NAV_BASE
import com.saulhdev.feeder.ui.navigation.NavigationManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.saulhdev.feeder.ui.pages.WhisperSplash
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import com.saulhdev.feeder.ui.theme.AppTheme
import com.saulhdev.feeder.utils.THEME_DARK
import com.saulhdev.feeder.utils.THEME_LIGHT
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    private val viewModel: ArticleListViewModel by inject(ArticleListViewModel::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, or the window splash never installs.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            navController = rememberNavController()
            // Collected rather than read once, so changing the theme or the
            // typeface in Settings is reflected immediately instead of on the
            // next app start. This is also why the activity no longer
            // recreate()s itself on a theme change: a full restart to repaint
            // colours Compose already tracks cost a frame budget and flashed
            // the splash screen on its way back.
            val themeMode by prefs.overlayTheme.get()
                .collectAsState(initial = prefs.overlayTheme.getValue())
            val dynamic by prefs.dynamicColor.get()
                .collectAsState(initial = prefs.dynamicColor.getValue())
            val fontPref by prefs.appFont.get()
                .collectAsState(initial = prefs.appFont.getValue())
            val pureBlack by prefs.pureBlack.get()
                .collectAsState(initial = prefs.pureBlack.getValue())

            // One place decides light or dark, and the bars follow it rather
            // than re-deriving it from the preference on their own.
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> systemDark
            }
            TransparentSystemBars(dark = dark)

            AppTheme(
                mode = themeMode,
                dynamicColor = dynamic,
                pureBlack = pureBlack,
                fontPref = fontPref,
            ) {
                // The window background comes from the XML theme, whose parent
                // is a DayNight one — so it follows the *system* light/dark
                // setting, not the app's own theme preference. With the system
                // in light mode and the app set to Dark or Pure black, every
                // pixel Compose did not paint showed through as white. Painting
                // the background here makes the app's choice the only one that
                // matters.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // "Ready" is the first feed emission that actually has
                    // articles. The state flow starts eagerly with an empty
                    // list, so an emptiness check is the only signal available
                    // — which is why the splash is also capped by a timeout,
                    // for the case where the feed is genuinely empty.
                    val feedState by viewModel.articleListState.collectAsState()
                    WhisperSplash(ready = feedState.articles.isNotEmpty()) {
                        NavigationManager(
                            modifier = Modifier.imePadding(),
                            navController = navController,
                        )
                    }
                }
            }
        }

        configurePeriodicSync()
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        if (::navController.isInitialized) {
            navController.handleDeepLink(intent)
        }
    }

    /**
     * @param dark whether the resolved scheme is a dark one, passed in rather
     *   than read here. It used to call `isDarkTheme` and `overlayTheme
     *   .getValue()`, both of which are blocking DataStore reads, as
     *   DisposableEffect *keys* — and keys are re-evaluated on every
     *   recomposition, so this blocked the main thread once per frame.
     */
    @Composable
    fun TransparentSystemBars(dark: Boolean) {
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { dark },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { dark },
            )
            onDispose {}
        }
    }

    private fun configurePeriodicSync() {
        val workManager = WorkManager.getInstance(this)
        val shouldSync = (prefs.syncFrequency.getValue().toDouble()) > 0
        val replace = true
        if (shouldSync) {
            val constraints = Constraints.Builder()

            if (prefs.syncOnlyOnWifi.getValue()) {
                constraints.setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                constraints.setRequiredNetworkType(NetworkType.CONNECTED)
            }
            val timeInterval = (prefs.syncFrequency.getValue().toDouble() * 60).toLong()

            val workRequestBuilder = PeriodicWorkRequestBuilder<FeedSyncer>(
                timeInterval,
                TimeUnit.MINUTES,
            )

            val syncWork = workRequestBuilder
                .setConstraints(constraints.build())
                .addTag("PeriodicFeedSyncer")
                .build()

            workManager.enqueueUniquePeriodicWork(
                "feeder_periodic_3",
                when (replace) {
                    true  -> ExistingPeriodicWorkPolicy.UPDATE
                    false -> ExistingPeriodicWorkPolicy.KEEP
                },
                syncWork
            )

        } else {
            workManager.cancelUniqueWork("feeder_periodic_3")
        }
    }

    companion object {
        fun navigateIntent(context: Context, destination: String): Intent {
            val uri = "$NAV_BASE$destination".toUri()
            return Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
        }

        private suspend fun start(
            activity: Activity,
            targetIntent: Intent,
            extras: Bundle
        ): ActivityResult {
            return suspendCancellableCoroutine { continuation ->
                val intent = Intent(activity, MainActivity::class.java)
                    .putExtras(extras)
                    .putExtra("intent", targetIntent)
                val resultReceiver = createResultReceiver {
                    if (continuation.isActive) {
                        continuation.resume(it)
                    }
                }
                activity.startActivity(intent.putExtra("callback", resultReceiver))
            }
        }

        private fun createResultReceiver(callback: (ActivityResult) -> Unit): ResultReceiver {
            return object : ResultReceiver(Handler(Looper.myLooper()!!)) {

                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    val data = Intent()
                    if (resultData != null) {
                        data.putExtras(resultData)
                    }
                    callback(ActivityResult(resultCode, data))
                }
            }
        }
    }
}