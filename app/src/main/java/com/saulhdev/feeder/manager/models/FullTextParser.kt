package com.saulhdev.feeder.manager.models

import org.jsoup.Jsoup
import com.saulhdev.feeder.utils.stripPageChrome
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import com.saulhdev.feeder.utils.HttpIdentity.asArticleReader
import com.saulhdev.feeder.utils.blobFullFile
import com.saulhdev.feeder.utils.blobFullFailedFile
import com.saulhdev.feeder.utils.FullTextAttempts
import com.saulhdev.feeder.utils.HttpStatusException
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.bytesSince
import com.saulhdev.feeder.utils.fullTextOutcome
import com.saulhdev.feeder.utils.isPermanentHttpFailure
import com.saulhdev.feeder.utils.receivedBytes
import com.saulhdev.feeder.utils.shouldPrefetchFullText
import kotlinx.coroutines.CancellationException
import com.saulhdev.feeder.utils.blobFullOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

fun scheduleFullTextParse() {
    Log.i("FeederFullText", "Scheduling a full text parse work")

    // This fetches and parses the body of every article the reader has, which
    // is the heaviest thing the app does — and it carried no constraints at
    // all. With no network requirement it would start on a phone with no
    // connection, fail article by article, and be retried; with no battery
    // requirement it would do that on a flat one.
    val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (prefs.syncOnlyOnWifi.getValue()) NetworkType.UNMETERED
            else NetworkType.CONNECTED
        )
        .setRequiresBatteryNotLow(true)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<FullTextWorker>()
        .addTag("FullTextWorker")
        .setConstraints(constraints)
        .keepResultsForAtLeast(1, TimeUnit.MINUTES)
    val workManager: WorkManager by inject(WorkManager::class.java)
    workManager.enqueueUniqueWork(
        "FullTextWorker",
        ExistingWorkPolicy.REPLACE,
        workRequest.build()
    )
}

class FullTextWorker(
    val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val okHttpClient: OkHttpClient = fullTextClient
    val repository: ArticleRepository by inject(ArticleRepository::class.java)
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)

    override suspend fun doWork(): Result {
        Log.i("FeederFullText", "Parsing full texts for articles if missing")
        val now = System.currentTimeMillis()
        val filesDir = context.filesDir
        // Chosen before anything is fetched, so a run with nothing to do
        // leaves no line in the history: it follows every sync, and twenty
        // lines of "nothing to fetch" would push the syncs themselves out.
        val toFetch = withContext(Dispatchers.IO) {
            repository.getFeedsItemsWithDefaultFullTextParse(
                allFeeds = prefs.fullTextForAllFeeds.getValue()
            )
                .firstOrNull()
                .orEmpty()
                .filter { item ->
                    !blobFullFile(item.uuid, filesDir).isFile &&
                        shouldPrefetchFullText(now, readAttempts(item.uuid, filesDir))
                }
        }
        if (toFetch.isEmpty()) return Result.success()

        val run = SyncLog.started(applicationContext, SyncLog.ORIGIN_FULL_TEXT)
        val receivedBefore = receivedBytes()
        var fetched = 0
        var failed = 0
        try {
            for (item in toFetch) {
                if (prefetchFullArticle(item, okHttpClient, filesDir)) fetched++ else failed++
            }
        } catch (e: CancellationException) {
            SyncLog.finished(applicationContext, run, "stopped after $fetched")
            throw e
        }
        SyncLog.finished(
            applicationContext,
            run,
            fullTextOutcome(fetched, failed, bytesSince(receivedBefore)),
        )
        // Success whatever happened to individual pages: each failure is
        // remembered against its article, and a retry of the whole run would
        // only fetch the same refusals again.
        return Result.success()
    }
}

/**
 * One prefetch, remembered if it fails.
 *
 * A refusal the server means (see isPermanentHttpFailure) is never asked
 * again; anything else - a timeout, a dropped connection, a server error - is
 * tried up to MAX_FULL_TEXT_ATTEMPTS times, FULL_TEXT_RETRY_AFTER_MS apart.
 * Opening the article still fetches it on the spot whatever this says; this
 * only stops the background asking.
 */
private suspend fun prefetchFullArticle(
    item: ArticleIdWithLink,
    okHttpClient: OkHttpClient,
    filesDir: File,
): Boolean {
    val (ok, error) = parseFullArticle(item, okHttpClient, filesDir)
    // parseFullArticle catches everything, the worker being stopped included;
    // that is not the page failing, and must not count against it.
    if (error is CancellationException) throw error
    if (!ok) {
        val permanent = item.link.isNullOrBlank() ||
            (error is HttpStatusException && isPermanentHttpFailure(error.code))
        val previous = readAttempts(item.uuid, filesDir) ?: FullTextAttempts.none
        withContext(Dispatchers.IO) {
            runCatching {
                blobFullFailedFile(item.uuid, filesDir)
                    .writeText(previous.next(System.currentTimeMillis(), permanent).encode())
            }
        }
    }
    return ok
}

private fun readAttempts(uuid: String, filesDir: File): FullTextAttempts? =
    runCatching {
        blobFullFailedFile(uuid, filesDir).takeIf { it.isFile }?.readText()
    }.getOrNull()?.let(FullTextAttempts::decode)

/**
 * The client full-article fetches share.
 *
 * One per process rather than one per worker or per call: OkHttp's value is
 * its connection pool, and a client made for a single request throws that away
 * before it can be used.
 */
val fullTextClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .asArticleReader()
        // The same guard the feed client has, and needed here more than there.
        // A feed address is at least something the reader typed or imported;
        // an article link is a string a publisher put in an XML file, and this
        // client fetches every one of them in the background. A hostile — or
        // merely compromised — feed listing <link>http://192.168.1.1/admin</link>
        // would otherwise have the phone reach into its own network and store
        // what came back. A network interceptor rather than a check on the
        // address, because OkHttp follows redirects itself and a public host
        // redirecting inward would walk straight past a check made up front.
        .onlyPublicHttps()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

suspend fun parseFullArticleIfMissing(
    feedItem: ArticleIdWithLink,
    okHttpClient: OkHttpClient,
    filesDir: File
): Boolean {
    val fullArticleFile = blobFullFile(itemId = feedItem.uuid, filesDir = filesDir)
    return fullArticleFile.isFile || parseFullArticle(
        feedItem = feedItem,
        okHttpClient = okHttpClient,
        filesDir = filesDir
    ).first
}

suspend fun parseFullArticle(
    feedItem: ArticleIdWithLink,
    okHttpClient: OkHttpClient,
    filesDir: File
): Pair<Boolean, Throwable?> = withContext(Dispatchers.Default) {
    return@withContext try {
        val url = feedItem.link ?: return@withContext false to null
        Log.d("FeederFullText", "Fetching full page ${feedItem.link}")
        val html: String = okHttpClient.curl(URL(url)) ?: return@withContext false to null

        // TODO verify encoding is respected in reader
        Log.i("FeederFullText", "Parsing article ${feedItem.link}")
        // Cleaned as a document first: the byline blocks and asides are only
        // recognisable by their class names, which extraction discards. See
        // stripPageChrome.
        val page = Jsoup.parse(html, url).also(::stripPageChrome)
        val article = Readability4JExtended(url, page).parse()

        // TODO set image on item if none already
        // naiveFindImageLink(article.content)?.let { Parser.unescapeEntities(it, true) }

        Log.d("FeederFullText", "Writing article ${feedItem.link}")
        withContext(Dispatchers.IO) {
            blobFullOutputStream(feedItem.uuid, filesDir).bufferedWriter().use { writer ->
                writer.write(article.contentWithUtf8Encoding)
            }
        }
        // Fetched after all, perhaps from the reader opening it: the record
        // of earlier failures no longer applies.
        withContext(Dispatchers.IO) { blobFullFailedFile(feedItem.uuid, filesDir).delete() }
        true to null
    } catch (e: Throwable) {
        Log.e(
            "FeederFullText",
            "Failed to get fulltext for ${feedItem.link}: ${e.message}",
            e
        )
        false to e
    }
}
