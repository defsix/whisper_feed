package com.saulhdev.feeder.manager.models

import com.saulhdev.feeder.data.db.dao.FeedSourceDao
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.utils.isSameFeedUrl
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URL

class SourceToRoom() : ParserToDatabase<Feed>, KoinComponent {
    private val dao: FeedSourceDao by inject()

    override suspend fun getItem(id: String): Feed? =
        findExisting(sloppyLinkToStrictURLNoThrows(id))

    /**
     * Writes one source from an OPML import.
     *
     * An import must not double what is already subscribed, and matching on the
     * URL string alone is not enough for that: an export from another reader
     * routinely differs from what is stored by a `www.`, a trailing slash or a
     * scheme, all of which reach the same feed.
     *
     * When the feed is already here, the stored row wins and only its
     * categories grow. That is the opposite of what this did before — it called
     * `update(item)` with the *imported* row, which carried `id = ID_UNSET`, so
     * the update matched no primary key and silently did nothing at all, and
     * would have overwritten the user's own title and settings if it had
     * worked.
     */
    override suspend fun saveItem(item: Feed) {
        val existing = findExisting(item.url)
        if (existing == null) {
            dao.insert(item)
            return
        }

        val merged = (existing.tags + item.tags).distinct().joinToString(",")
        if (merged != existing.tag) {
            dao.update(existing.copy(tag = merged))
        }
    }

    private suspend fun findExisting(url: URL): Feed? =
        dao.getFeedByURL(url)
            ?: dao.loadAllFeeds().firstOrNull { isSameFeedUrl(it.url, url) }
}
