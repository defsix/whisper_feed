/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
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
package com.saulhdev.feeder.manager.sync.greader

import com.saulhdev.feeder.utils.HttpIdentity.asFeedReader
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * A client for the Google Reader API, as spoken by FreshRSS, Miniflux,
 * Inoreader and BazQux.
 *
 * Google's own Reader has been gone since 2013; what survived is its HTTP
 * interface, which those services implement because everything already spoke
 * it. One client therefore buys several services, needs no Google account and
 * no Play Services, and works against something a reader runs themselves.
 * `docs/REFERENCES.md` §2 records why this rather than Feedly, whose API turned
 * out to be enterprise-gated.
 *
 * The protocol is old and shows it. Authentication is a form post that returns
 * a token in a `key=value` body rather than JSON; writes need a *second*,
 * short-lived token fetched separately; and identifiers come in three shapes
 * (see [GoogleReaderIds]). None of that is avoidable, so it is all handled
 * here and nowhere else.
 *
 * This class does the protocol and nothing else: no database, no reconciling,
 * no decisions about what a sync means. That belongs above it.
 */
class GoogleReaderApi(
    private val serverUrl: String,
    private val client: OkHttpClient = defaultClient,
) {

    /** What a sign-in produced, or why it did not. */
    sealed interface AuthResult {
        data class Success(val token: String) : AuthResult
        data class Rejected(val reason: String) : AuthResult
        data class Unreachable(val cause: Throwable) : AuthResult
    }

    private val base: HttpUrl? =
        serverUrl.trimEnd('/').toHttpUrlOrNull()

    /**
     * Signs in with ClientLogin, the protocol's own scheme.
     *
     * Not OAuth: these are self-hosted services, and most of them issue an
     * application password for exactly this. The response is a plain-text body
     * of `key=value` lines, of which `Auth` is the one that matters.
     */
    suspend fun signIn(username: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val url = base?.newBuilder()
                ?.addPathSegments("accounts/ClientLogin")
                ?.build()
                ?: return@withContext AuthResult.Rejected("That is not a valid address")

            val body = FormBody.Builder()
                .add("Email", username)
                .add("Passwd", password)
                .build()

            try {
                client.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                    if (!r.isSuccessful) {
                        return@withContext AuthResult.Rejected("The server said ${r.code}")
                    }
                    val auth = r.body.string()
                        .lineSequence()
                        .firstOrNull { it.startsWith("Auth=") }
                        ?.removePrefix("Auth=")
                        ?.trim()
                    if (auth.isNullOrEmpty()) AuthResult.Rejected("No token in the reply")
                    else AuthResult.Success(auth)
                }
            } catch (t: Throwable) {
                AuthResult.Unreachable(t)
            }
        }

    /**
     * The write token, which is separate from the auth token and short-lived.
     *
     * Every mutating call needs one, and servers expire them aggressively, so
     * it is fetched per batch of writes rather than cached for the session.
     */
    suspend fun writeToken(auth: String): String? = withContext(Dispatchers.IO) {
        runCatching { getString(auth, "reader/api/0/token").trim() }.getOrNull()
    }

    /** Every feed the account subscribes to, with the folders it is filed in. */
    suspend fun subscriptions(auth: String): List<Subscription> = withContext(Dispatchers.IO) {
        val json = getString(auth, "reader/api/0/subscription/list", "output" to "json")
        subscriptionsAdapter.fromJson(json)?.subscriptions.orEmpty()
    }

    /** The ids of everything in a stream, for working out what is unread. */
    suspend fun itemIds(
        auth: String,
        stream: String = GoogleReaderIds.STREAM_READING_LIST,
        excludeTag: String? = null,
        limit: Int = 1000,
        since: Long? = null,
    ): List<String> = withContext(Dispatchers.IO) {
        val params = buildList {
            add("s" to stream)
            add("n" to limit.toString())
            add("output" to "json")
            excludeTag?.let { add("xt" to it) }
            since?.let { add("ot" to (it / 1000).toString()) }
        }
        val json = getString(auth, "reader/api/0/stream/items/ids", *params.toTypedArray())
        itemRefsAdapter.fromJson(json)?.itemRefs.orEmpty()
            .mapNotNull { GoogleReaderIds.itemId(it.id) }
    }

    /**
     * The items in a stream, with the addresses they point at.
     *
     * This is the only call that says which server id belongs to which
     * article, and the app needs that because it does not take its articles
     * from the server: they are fetched from the feeds themselves, so every
     * one has a local id the server has never seen. The link is the one thing
     * both sides know.
     *
     * Only the id and the alternate link are read. The rest of the payload is
     * the article itself, which this app already has in a better form —
     * extracted full text, a chosen image, a built summary — so taking the
     * server's copy would mean losing those or fetching twice.
     */
    suspend fun streamContents(
        auth: String,
        stream: String = GoogleReaderIds.STREAM_READING_LIST,
        limit: Int = 1000,
        since: Long? = null,
    ): List<StreamItem> = withContext(Dispatchers.IO) {
        val params = buildList {
            add("n" to limit.toString())
            add("output" to "json")
            since?.let { add("ot" to (it / 1000).toString()) }
        }
        val json = getString(
            auth,
            "reader/api/0/stream/contents/" + stream,
            *params.toTypedArray(),
        )
        streamContentsAdapter.fromJson(json)?.items.orEmpty()
    }

    /** Marks items read or unread, starred or not, in one call per batch. */
    suspend fun editTag(
        auth: String,
        token: String,
        itemIds: List<String>,
        addTag: String? = null,
        removeTag: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext true
        val url = base?.newBuilder()?.addPathSegments("reader/api/0/edit-tag")?.build()
            ?: return@withContext false

        val body = FormBody.Builder().apply {
            add("T", token)
            addTag?.let { add("a", it) }
            removeTag?.let { add("r", it) }
            // One parameter per item, repeated — the protocol has no list form.
            itemIds.forEach { add("i", it) }
        }.build()

        runCatching {
            client.newCall(authorised(auth, url).post(body).build()).execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Subscribes, unsubscribes, renames or refiles one feed. */
    suspend fun editSubscription(
        auth: String,
        token: String,
        action: String,
        feedUrl: String,
        title: String? = null,
        addLabel: String? = null,
        removeLabel: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val url = base?.newBuilder()?.addPathSegments("reader/api/0/subscription/edit")?.build()
            ?: return@withContext false

        val body = FormBody.Builder().apply {
            add("T", token)
            add("ac", action)
            add("s", GoogleReaderIds.feedStream(feedUrl))
            title?.let { add("t", it) }
            addLabel?.let { add("a", GoogleReaderIds.labelStream(it)) }
            removeLabel?.let { add("r", GoogleReaderIds.labelStream(it)) }
        }.build()

        runCatching {
            client.newCall(authorised(auth, url).post(body).build()).execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun authorised(auth: String, url: HttpUrl) = Request.Builder()
        .url(url)
        // The protocol's own header, unchanged since Google Reader.
        .header("Authorization", "GoogleLogin auth=$auth")

    private fun getString(auth: String, path: String, vararg params: Pair<String, String>): String {
        val url = base?.newBuilder()
            ?.addPathSegments(path)
            ?.apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            ?.build()
            ?: throw IOException("Not a valid server address: $serverUrl")

        client.newCall(authorised(auth, url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $path")
            return r.body.string()
        }
    }

    companion object {
        /**
         * Identified as Whisper, not as a browser. This is an API belonging to
         * a server the reader chose, so there is nothing to be gained by
         * pretending to be anything else.
         */
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder().asFeedReader().build()
        }

        private val moshi: Moshi =
            Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        internal val subscriptionsAdapter = moshi.adapter(SubscriptionList::class.java)
        internal val itemRefsAdapter = moshi.adapter(ItemRefList::class.java)
        internal val streamContentsAdapter = moshi.adapter(StreamContents::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class SubscriptionList(val subscriptions: List<Subscription> = emptyList())

@JsonClass(generateAdapter = true)
data class Subscription(
    val id: String = "",
    val title: String = "",
    val url: String? = null,
    val htmlUrl: String? = null,
    val iconUrl: String? = null,
    val categories: List<SubscriptionCategory> = emptyList(),
) {
    /** The feed's address, from whichever field this server chose to fill in. */
    val feedUrl: String get() = url ?: GoogleReaderIds.feedUrl(id)

    /** The folders it is filed in, as the category names Whisper uses. */
    val folders: List<String> get() = categories.map { GoogleReaderIds.labelName(it.id) }
}

@JsonClass(generateAdapter = true)
data class SubscriptionCategory(val id: String = "", val label: String? = null)

@JsonClass(generateAdapter = true)
data class ItemRefList(val itemRefs: List<ItemRef> = emptyList())

@JsonClass(generateAdapter = true)
data class ItemRef(val id: String = "")

@JsonClass(generateAdapter = true)
data class StreamContents(val items: List<StreamItem> = emptyList())

@JsonClass(generateAdapter = true)
data class StreamItem(
    val id: String = "",
    val alternate: List<StreamLink> = emptyList(),
) {
    /**
     * The article's address, and the server's id for it in the short form.
     *
     * Null when either is missing, which is how a malformed item costs one
     * article rather than the whole mapping.
     */
    fun mapping(): Pair<String, String>? {
        val link = alternate.firstOrNull { !it.href.isNullOrBlank() }?.href ?: return null
        val item = GoogleReaderIds.itemId(id) ?: return null
        return link to item
    }
}

@JsonClass(generateAdapter = true)
data class StreamLink(val href: String? = null, val type: String? = null)
