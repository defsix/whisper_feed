package com.saulhdev.feeder.data.db.models

import com.saulhdev.feeder.utils.usableImageUrl
import android.graphics.Color
import androidx.room.Embedded
import androidx.room.Relation
import com.saulhdev.feeder.data.entity.FeedCategory
import com.saulhdev.feeder.manager.models.StoryCardContent

data class FeedItem(
    @Embedded
    val article: Article,

    @Relation(
        parentColumn = "feedId",
        entityColumn = "id"
    )
    val feed: Feed
) {
    fun toStoryCardContent(): StoryCardContent {
        return StoryCardContent(
            title = contentTitle,
            text = article.description,
            backgroundUrl = article.imageUrl ?: "",
            tag = feedTag,
            link = link,
            source = FeedCategory(
                sourceId,
                feedTitle,
                Color.GREEN,
                feed.feedImage.toString()
            )
        )
    }

    val id: String
        get() = article.uuid

    /**
     * The article's picture if it can be fetched, or null.
     *
     * Read this rather than `article.imageUrl` anywhere the answer decides a
     * layout or starts a request: the column also holds whatever a feed wrote
     * before the check existed, and an address with no host is a picture that
     * fails every single time it is asked for. See usableImageUrl.
     */
    val imageUrl: String?
        get() = usableImageUrl(article.imageUrl)

    val link: String
        get() = article.link ?: ""

    val sourceId: String
        get() = feed.id.toString()

    val feedTitle: String
        get() = feed.title

    /**
     * The source's mark, or null when it has none.
     *
     * feedImage defaults to an empty URL rather than null, and one call site
     * used to write the *feed's own address* into it, so a blank check is not
     * enough — a value that is not an image has to be treated as absent too.
     */
    val feedIconUrl: String?
        get() = feed.feedImage.toString().takeIf { it.isNotBlank() && it != feed.url.toString() }

    val displayTitle: String
        get() = "${feed.title} [RSS]"

    val contentTitle: String
        get() = article.title

    /**
     * The column the feed is ordered by. SQL already orders on primarySortTime
     * and the card renders it, but this sorted on pubDate — so the list order
     * and the dates shown on it were derived from different columns.
     */
    val timeMillis: Long
        get() = article.primarySortTime.toEpochMilliseconds()

    val bookmarked: Boolean
        get() = article.bookmarked

    val pinned: Boolean
        get() = article.pinned

    /** Every category this article's source carries. */
    val feedTags: List<String>
        get() = feed.tags

    /**
     * The single category shown on a card. A feed tagged "Tech,News" was
     * drawing the raw string, comma and all.
     */
    val feedTag: String
        get() = feed.tags.firstOrNull().orEmpty()

    val domain: String?
        get() = article.domain

    val enclosureFilename: String?
        get() = article.enclosureFilename
}