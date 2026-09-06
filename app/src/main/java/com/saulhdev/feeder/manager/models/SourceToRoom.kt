package com.saulhdev.feeder.manager.models

import com.saulhdev.feeder.data.db.dao.FeedSourceDao
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SourceToRoom() : ParserToDatabase<Feed>, KoinComponent {
    private val dao: FeedSourceDao by inject()

    override suspend fun getItem(id: String): Feed? =
        dao.getFeedByURL(sloppyLinkToStrictURLNoThrows(id))

    override suspend fun saveItem(item: Feed) {
        val existing = dao.getFeedByURL(item.url)

        // Don't want to remove existing feed on OPML imports
        if (existing != null) {
            dao.update(item)
        } else {
            dao.insert(item)
        }
    }
}