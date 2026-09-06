package com.saulhdev.feeder.manager.models

import com.saulhdev.feeder.data.db.dao.FeedArticleDao
import com.saulhdev.feeder.data.db.models.Article
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BookmarksToRoom() : ParserToDatabase<Article>, KoinComponent {
    private val dao: FeedArticleDao by inject()

    override suspend fun getItem(id: String): Article? =
        dao.getArticleById(id)

    override suspend fun saveItem(item: Article) {
        dao.upsert(item)
    }
}