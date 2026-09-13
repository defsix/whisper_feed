package com.saulhdev.feeder.manager.models

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
import com.saulhdev.feeder.manager.bookmarks.BlockPrivateNetworks
import com.saulhdev.feeder.manager.bookmarks.UpgradeToHttps
import com.saulhdev.feeder.utils.HttpIdentity.asArticleReader
import com.saulhdev.feeder.utils.blobFullFile
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
        val itemsToSync: List<ArticleIdWithLink> =
            repository.getFeedsItemsWithDefaultFullTextParse(
                allFeeds = prefs.fullTextForAllFeeds.getValue()
            )
                .firstOrNull()
                ?: return Result.success()

        val success: Boolean = itemsToSync
            .map { feedItem ->
                parseFullArticleIfMissing(
                    feedItem = feedItem,
                    okHttpClient = okHttpClient,
                    filesDir = context.filesDir
                )
            }
            .fold(true) { acc, value ->
                acc && value
            }

        return when (success) {
            true  -> Result.success()
            false -> Result.failure()
        }
    }
}

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
        .addInterceptor(UpgradeToHttps())
        .addNetworkInterceptor(BlockPrivateNetworks())
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
        val article = Readability4JExtended(url, html).parse()

        // TODO set image on item if none already
        // naiveFindImageLink(article.content)?.let { Parser.unescapeEntities(it, true) }

        Log.d("FeederFullText", "Writing article ${feedItem.link}")
        withContext(Dispatchers.IO) {
            blobFullOutputStream(feedItem.uuid, filesDir).bufferedWriter().use { writer ->
                writer.write(article.contentWithUtf8Encoding)
            }
        }
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
