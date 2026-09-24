package com.saulhdev.feeder.manager.sync

import com.saulhdev.feeder.utils.backgroundMobileDataBlocked
import com.saulhdev.feeder.utils.isPowerSaveMode
import com.saulhdev.feeder.utils.stopReasonName
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.data.db.ID_ALL
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import com.saulhdev.feeder.manager.sync.service.LocalRssService
import com.saulhdev.feeder.manager.sync.service.RssServiceDispatcher
import com.saulhdev.feeder.manager.sync.service.SyncOutcome
import org.koin.java.KoinJavaComponent.inject
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.saulhdev.feeder.R
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.syncOutcome
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.ID_UNSET
import com.saulhdev.feeder.data.repository.SourcesRepository
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit

class FeedSyncer(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val TAG = "FeedSyncer"
    private val dispatcher: RssServiceDispatcher by inject(RssServiceDispatcher::class.java)
    private val sources: SourcesRepository by inject(SourcesRepository::class.java)
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(context, notificationManager)
    }

    override suspend fun doWork(): Result {
        var result: SyncResult
        // Written before anything else can fail, so even a run that dies
        // immediately leaves a line saying it started. See SyncLog.
        val origin = inputData.getString(SyncLog.ORIGIN_KEY) ?: "unlabelled"
        val run = SyncLog.started(applicationContext, origin)

        // Battery Saver pauses the syncs nobody asked for at this moment -
        // the schedule and the panel's - and nothing else. It is the reader
        // saying "use as little battery as you can", and a background fetch
        // of every feed is precisely what that is meant to stop. There is no
        // WorkManager constraint for it, so it is asked here.
        //
        // Success rather than retry: the scheduled sync comes round again at
        // its next slot, and a retry would back off and try again while the
        // saver was still on. Plugging in turns Battery Saver off by itself,
        // so this can never hold a sync that "Sync only while charging" is
        // waiting to run. Pull to refresh and the rest go through.
        if (origin in SyncLog.AUTOMATIC_ORIGINS && isPowerSaveMode(applicationContext)) {
            SyncLog.finished(applicationContext, run, "skipped: Battery Saver")
            return Result.success()
        }

        // On mobile data with Android keeping Whisper off it in the
        // background, a run gets seconds - until Whisper leaves the screen -
        // and is then cut off and retried, again and again. Skipped the same
        // way as for Battery Saver: the next slot may be on Wi-Fi, and pull
        // to refresh runs in the foreground, where the block does not apply.
        // Settings says which switch lifts it; see BackgroundDataHint.
        if (origin in SyncLog.AUTOMATIC_ORIGINS && backgroundMobileDataBlocked(applicationContext)) {
            SyncLog.finished(applicationContext, run, "skipped: background mobile data blocked")
            return Result.success()
        }

        // A sync the reader asked for keeps its network when they leave.
        //
        // Every pull to refresh in one day's reports was cut off the moment
        // Whisper left the screen - "stopped: network changed or dropped",
        // ending "blocked for Whisper" even on Wi-Fi - while the scheduled and
        // panel syncs, which Android starts itself, ran to the end in the
        // background. A foreground task is how Android lets work the reader
        // started go on after they have looked away; it has to show a
        // notification, which is the silent "Syncing" line in the shade, and
        // Android holds that back for the first ten seconds so a quick pull
        // never shows it at all. Asked while the app is still on screen,
        // which is when a pull starts; if Android refuses, the sync simply
        // runs as it did before.
        val foreground = origin == SyncLog.ORIGIN_PULL || origin == SyncLog.ORIGIN_PULL_PANEL
        var inForeground = false
        if (foreground) {
            try {
                setForeground(getForegroundInfo())
                inForeground = true
            } catch (e: CancellationException) {
                SyncLog.finished(applicationContext, run, "stopped: before starting")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not run the sync in the foreground", e)
            }
        }
        // Whether another sync holds the lock, so a run that spent its first
        // minutes waiting is not mistaken for a slow one.
        val queued = syncMutex.isLocked

        try {
            val feedId = inputData.getLong("feed_id", ID_UNSET)
            val feedTag = inputData.getString("feed_tag") ?: ""
            // False unless the request says so. This defaulted to true, and the
            // scheduled sync's request carries no such key - so every
            // scheduled sync forced a fetch of every feed however recently it
            // had been fetched. The report caught it: plugging in released
            // the panel's sync and the scheduled one together, the lock let
            // the panel's go first, and the scheduled one then waited three
            // minutes and downloaded all 120 feeds again. Only a sync the
            // reader asked for should skip the freshness check.
            val forceNetwork = inputData.getBoolean("force_network", false)
            val minFeedAgeMinutes = inputData.getInt("min_feed_age_minutes", 5)

            // A whole-feed refresh goes through whichever service is in
            // charge; a single feed or one tag is always local, because
            // neither the protocol nor this worker has a notion of syncing
            // part of an account.
            //
            // This used to call syncFeeds directly in every case, which meant
            // an account was reconciled only when its settings screen was
            // open: subscriptions added on another device did not arrive, and
            // read state never moved unless somebody went looking for it. The
            // scheduled sync was local-only without saying so.
            val wholeFeed = feedId == ID_UNSET && feedTag.isEmpty()
            val service = dispatcher.current()

            result = if (wholeFeed && service !is LocalRssService) {
                // An account says only whether it worked, so its line has no
                // feed count; see SyncResult.counted.
                when (val outcome = service.sync()) {
                    is SyncOutcome.Success -> SyncResult.uncounted
                    SyncOutcome.SignedOut -> {
                        // The token is gone, and retrying will not bring it
                        // back. Reported as success so WorkManager does not
                        // back off and retry a thing that needs the reader.
                        Log.w(TAG, "Account signed out; scheduled sync stopped")
                        SyncResult(due = 0, error = "signed out")
                    }

                    is SyncOutcome.Failed -> {
                        Log.e(TAG, "Account sync failed", outcome.cause)
                        outcome.cause?.let(SyncResult::broken) ?: SyncResult(due = 0, error = "error")
                    }
                }
            } else {
                syncFeeds(
                    context = context,
                    feedId = feedId,
                    feedTag = feedTag,
                    forceNetwork = forceNetwork,
                    minFeedAgeMinutes = minFeedAgeMinutes
                )
            }
        } catch (e: CancellationException) {
            // Cancellation is the reader leaving, not a sync that went wrong.
            // Reported as a failure it would retry on a backoff and log an
            // error for something nobody did wrong. See RssLocalSync.
            //
            // WorkManager stops a worker by cancelling it, so this is also
            // where a run cut off by a lost constraint ends - and its reason
            // is the thing "attempts 2" could not explain.
            // The worker can only ask why from Android 12; before that,
            // WorkManager's own record in the report is the only answer.
            val why = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stopReasonName(stopReason)
            } else {
                "no reason given"
            }
            SyncLog.finished(applicationContext, run, "stopped: $why")
            throw e
        } catch (e: Exception) {
            result = SyncResult.broken(e)
            Log.e(TAG, "Failure during sync", e)
        }

        val notes = listOfNotNull(
            if (queued) "queued" else null,
            if (inForeground) "foreground" else null,
        )
        SyncLog.finished(
            applicationContext,
            run,
            syncOutcome(result, notes),
        )
        // Signed out stays a success to WorkManager: retrying cannot bring a
        // token back, and a backoff would only repeat the same line.
        val success = result.ok || result.error == "signed out"
        // A notice that syncing had stopped is taken back by the sync that
        // proves it has started again.
        if (success) {
            try {
                SyncWatchdog.clearIfCurrent(applicationContext, sources, prefs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear the stuck-sync notice", e)
            }
        }
        return when (success) {
            true  -> Result.success()
            false -> Result.failure()
        }
    }
}

private const val syncNotificationId = 42624
private const val syncChannelId = "feederSyncNotifications"
private const val syncNotificationGroup = "com.saulhdev.neofeed.SYNC"

private fun createNotificationChannel(
    context: Context,
    notificationManager: NotificationManagerCompat
) {
    val name = context.getString(R.string.sync_status)
    val description = context.getString(R.string.sync_status)

    val channel =
        NotificationChannel(syncChannelId, name, NotificationManager.IMPORTANCE_LOW)
    channel.description = description

    notificationManager.createNotificationChannel(channel)
}

fun createForegroundInfo(
    context: Context,
    notificationManager: NotificationManagerCompat
): ForegroundInfo {
    createNotificationChannel(context, notificationManager)

    val syncingText = context.getString(R.string.syncing)

    val notification =
        NotificationCompat.Builder(context.applicationContext, syncChannelId)
            .setContentTitle(syncingText)
            .setTicker(syncingText)
            .setGroup(syncNotificationGroup)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            // A status line, not an alert: no sound, no vibration, no pop-up.
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()

    return ForegroundInfo(
        syncNotificationId,
        notification,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else 0
    )
}

fun requestFeedSync(
    feedId: Long = ID_UNSET,
    feedTag: String = "",
    forceNetwork: Boolean = false,
    /** What asked for it, for the sync record. See SyncLog. */
    origin: String,
) {
    val workManager: WorkManager by inject(WorkManager::class.java)
    val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    
    val data = workDataOf(
        "feed_id" to feedId,
        "feed_tag" to feedTag,
        "force_network" to forceNetwork,
        SyncLog.ORIGIN_KEY to origin,
    )
    Log.d(TAG, "requestFeedSync: $data")
    val constraints = Constraints.Builder()
    if (prefs.syncOnlyOnWifi.getValue() && !forceNetwork) {
        constraints.setRequiredNetworkType(NetworkType.UNMETERED)
    } else {
        constraints.setRequiredNetworkType(NetworkType.CONNECTED)
    }

    val workRequest = OneTimeWorkRequestBuilder<FeedSyncer>()
        .addTag("FeedSyncer")
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .keepResultsForAtLeast(1, TimeUnit.MINUTES)
        .setConstraints(constraints.build())
        .setInputData(data)
        .build()

    workManager.enqueueUniqueWork(
        "feeder_sync_onetime_$feedId",
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}

/**
 * A whole-feed sync that waits for the scheduled sync's conditions.
 *
 * Its own unique name, and KEEP rather than REPLACE, for two reasons. REPLACE
 * under the one-time name would cancel a pull-to-refresh already running the
 * moment the launcher recreated the panel. And a request that is waiting for a
 * charger needs no second copy queued behind it every time the panel opens.
 */
fun requestAutomaticFeedSync() {
    val workManager: WorkManager by inject(WorkManager::class.java)
    val prefs: FeedPreferences by inject(FeedPreferences::class.java)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (prefs.syncOnlyOnWifi.getValue()) NetworkType.UNMETERED else NetworkType.CONNECTED
        )
        .setRequiresCharging(prefs.syncOnlyWhenCharging.getValue())
        .setRequiresBatteryNotLow(true)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<FeedSyncer>()
        .addTag("FeedSyncer")
        .setConstraints(constraints)
        .setInputData(
            workDataOf(
                "feed_id" to ID_ALL,
                "feed_tag" to "",
                "force_network" to false,
                SyncLog.ORIGIN_KEY to SyncLog.ORIGIN_PANEL,
            )
        )
        .build()

    workManager.enqueueUniqueWork(AUTOMATIC_SYNC_WORK, ExistingWorkPolicy.KEEP, workRequest)
}

/** The name the panel's automatic sync is enqueued under. See requestAutomaticFeedSync. */
const val AUTOMATIC_SYNC_WORK = "feeder_sync_automatic"

/**
 * The name the scheduled sync is enqueued under.
 *
 * One constant rather than a string in two places: the diagnostics report
 * asks WorkManager about the work by this name, and a report that looked
 * under a name the scheduler had stopped using would say "not scheduled"
 * about a sync that was running perfectly well.
 */
const val PERIODIC_SYNC_WORK = "feeder_periodic_3"
