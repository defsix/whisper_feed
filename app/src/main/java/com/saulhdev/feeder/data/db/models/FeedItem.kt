package com.saulhdev.feeder.data.db.models

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

    val link: String
        get() = article.link ?: ""

    val sourceId: String
        get() = feed.id.toString()

    val feedTitle: String
        get() = feed.title

    val displayTitle: String
        get() = "${feed.title} [RSS]"

    val contentTitle: String
        get() = article.title

    val timeMillis: Long
        get() = article.pubDate

    val bookmarked: Boolean
        get() = article.bookmarked

    val pinned: Boolean
        get() = article.pinned

    val feedTag: String
        get() = feed.tag

    val domain: String?
        get() = article.domain

    val enclosureFilename: String?
        get() = article.enclosureFilename
}