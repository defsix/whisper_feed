/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.saulhdev.feeder.data.db.models

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import com.saulhdev.feeder.data.entity.Item
import com.saulhdev.feeder.data.entity.JsonFeed
import com.saulhdev.feeder.utils.HtmlToPlainTextConverter
import com.saulhdev.feeder.utils.relativeLinkIntoAbsolute
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import java.net.URI
import java.net.URL
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    tableName = "Article",
    indices = [
        Index(value = ["feedId", "uuid"], unique = true),
        Index(value = ["feedId", "guid"]),
        Index(value = ["uuid", "link"]),
        Index(value = ["feedId"]),
        // Every feed query orders by primarySortTime; without this SQLite sorted
        // the whole result set on each one.
        Index(value = ["primarySortTime"]),
        // Read state arrives from a server as a list of its own ids; without
        // an index this is a full scan per article on every sync.
        Index(value = ["remoteId"]),
        // Every question about read state — the unread badge, the unread-only
        // filter, the window of recently read articles the suggestion engine
        // learns from — asked this column and got a full table scan. The two
        // columns that made the feed itself quick were indexed and this one,
        // which the same queries lean on, was missed.
        //
        // Invisible at a few thousand rows. Forty feeds at a hundred items
        // each is four thousand and climbing, and that reader is exactly the
        // one who would notice.
        Index(value = ["readAt"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = CASCADE
        )
    ],
)
data class Article constructor(
    @PrimaryKey
    val uuid: String = "",
    val guid: String = "",
    val title: String = "",
    val plainTitle: String = "",
    val imageUrl: String? = null,
    val enclosureLink: String? = null,
    val plainSnippet: String = "",
    val description: String = "",
    val author: String? = "",
    @ColumnInfo(name = "pubDateV2", defaultValue = "0")
    val pubDate: Long = 0L,
    val link: String? = "",
    val feedId: Long = 0,
    @ColumnInfo(typeAffinity = ColumnInfo.INTEGER)
    val firstSyncedTime: Instant = Clock.System.now(),
    @ColumnInfo(typeAffinity = ColumnInfo.INTEGER)
    val primarySortTime: Instant = Clock.System.now(),
    val categories: ArrayList<String> = arrayListOf(),
    val pinned: Boolean = false,
    val bookmarked: Boolean = false,
    /**
     * When the article was opened, in epoch millis; 0 means unread.
     *
     * Nothing tracked reads before this, so "unread" and "read today" could not
     * be answered at all. Set when an article is opened, and never cleared by a
     * sync — re-parsing an existing entry keeps whatever is already stored.
     */
    @ColumnInfo(defaultValue = "0")
    val readAt: Long = 0L,

    /**
     * When the article was opened to read in full, in epoch millis; 0 if never.
     *
     * Deliberately not the same question as [readAt]. An article is marked
     * read by being scrolled past, which happens to forty of them in a flick
     * of the thumb, so `readAt` records that an article went by rather than
     * that anybody wanted it. This records the one thing that is unambiguous:
     * somebody tapped it and a reader or a browser opened.
     *
     * Set at the moment of opening and never revised afterwards. Whether the
     * app is ever seen again is not evidence about the article — returning
     * from the browser is not guaranteed, particularly from the launcher
     * overlay — and absence of evidence is not evidence.
     */
    @ColumnInfo(defaultValue = "0")
    val openedAt: Long = 0L,

    /**
     * How long the article's card has been visible on screen, in milliseconds.
     *
     * Accumulated across visits, counted only while the feed is actually in
     * front of somebody, and capped so one article left on screen overnight
     * cannot outweigh a year of ordinary reading.
     *
     * This is the difference between a headline that was scrolled past and one
     * that was stopped at, which is the distinction the ordering has been
     * missing — it could tell that forty articles went by and not that one of
     * them was looked at.
     */
    @ColumnInfo(defaultValue = "0")
    val dwellMs: Long = 0L,

    /**
     * What a Google Reader server calls this article, if one does.
     *
     * Null for every article on an account-less install, which is most of
     * them, and null until a sync has actually seen the article on the server.
     *
     * It exists because the two sides name the same article differently and
     * nothing connected them: `uuid` is generated here with
     * `UUID.randomUUID()` and means nothing anywhere else, while the server
     * assigns its own id. Read state arrives as a list of server ids, so
     * without this column the only options were to guess or to throw the
     * answer away — and the app threw it away, deliberately, rather than mark
     * the wrong articles read.
     *
     * Not the guid: an RSS guid is set by the publisher and the item id is
     * assigned by the server, so they are unrelated.
     */
    @ColumnInfo(defaultValue = "NULL")
    val remoteId: String? = null,
) {
    fun updateFromParsedEntry(
        entry: Item,
        entryGuid: String,
        feed: JsonFeed,
        feedId: Long,
    ): Article {
        // Be careful about nulls.
        val text = entry.content_html ?: entry.content_text ?: ""
        // Both description and summary used to call convert() independently, so
        // every article with only content_html was parsed through TagSoup twice.
        val plain: String by lazy(LazyThreadSafetyMode.NONE) {
            HtmlToPlainTextConverter().convert(text)
        }
        val fullText = entry.summary ?: entry.content_text ?: plain
        val description = fullText.trim()
        val summary: String = fullText.take(200)

        // Make double sure no base64 images are used as thumbnails
        val safeImage = when {
            entry.image?.startsWith("data") == true
                 -> null

            else -> entry.image
        }

        val absoluteImage = when {
            feed.feed_url != null && safeImage != null
                 -> relativeLinkIntoAbsolute(sloppyLinkToStrictURL(feed.feed_url), safeImage)

            else -> safeImage
        }

        val plainTitle = entry.title?.take(200) ?: this.plainTitle

        // Computed up front rather than inline in copy(): named arguments in a
        // copy() call cannot see each other, so `primarySortTime`'s reference to
        // `pubDate` resolved to the *existing* row's value, which is 0 for every
        // newly parsed article. Every new article therefore sorted and displayed
        // as its sync time instead of its publication time.
        val newPubDate = try {
            // Allow an actual pubdate to be updated
            Instant.parse(entry.date_published?.substringBefore('[') ?: "")
                .toEpochMilliseconds()
        } catch (_: Throwable) {
            // If a pubDate is missing, then don't update if one is already set
            this.pubDate.takeIf { it > 0L }
                ?: Clock.System.now().toEpochMilliseconds()
        }
        val newSortTime = if (newPubDate > 0L) {
            minOf(firstSyncedTime, Instant.fromEpochMilliseconds(newPubDate))
        } else {
            firstSyncedTime
        }

        return copy(
            guid = entryGuid,
            plainTitle = plainTitle,
            title = plainTitle,
            plainSnippet = summary,
            description = description.ifEmpty { this.description },
            imageUrl = absoluteImage,
            enclosureLink = entry.attachments?.firstOrNull()?.url,
            author = entry.author?.name ?: feed.author?.name,
            link = entry.url,
            pubDate = newPubDate,
            primarySortTime = newSortTime,
            feedId = feedId,
        )
    }

    val enclosureFilename: String?
        get() {
            enclosureLink?.let { enclosureLink ->
                var fname: String? = null
                try {
                    fname = URI(enclosureLink).path.split("/").last()
                } catch (_: Exception) {
                }
                return if (fname.isNullOrEmpty()) {
                    null
                } else {
                    fname
                }
            }
            return null
        }

    val domain: String?
        get() {
            val l: String? = enclosureLink ?: link
            if (l != null) {
                try {
                    return URL(l).host.replace("www.", "")
                } catch (_: Throwable) {
                }
            }
            return null
        }
}

@DatabaseView(
    """
    SELECT Article.uuid, Article.link
    FROM Article
    JOIN feeds f ON Article.feedId = f.id
    WHERE f.fulltextByDefault = 1 OR Article.bookmarked = 1
"""
)
data class ArticleIdWithLink(
    val uuid: String,
    val link: String
)
/**
 * How many of one source's articles have been read in a window.
 *
 * Not an entity and not a view — a projection Room fills in from a GROUP BY,
 * which is the whole shape of the answer and nothing more.
 */
data class SourceReadCount(
    val feedId: Long,
    val reads: Int,
)
