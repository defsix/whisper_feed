/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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

package com.saulhdev.feeder.viewmodels

import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.models.fullTextClient
import com.saulhdev.feeder.manager.models.parseFullArticleIfMissing
import com.saulhdev.feeder.utils.blobFullFile
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.plus

class ArticleViewModel(
    private val articleRepo: ArticleRepository,
    private val sourcesRepo: SourcesRepository,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    private val articleId: MutableStateFlow<String> = MutableStateFlow("")

    fun setArticleId(value: String) {
        articleId.update { value }
    }

    /**
     * Whether the readable version of the open article is here yet.
     *
     * A feed entry is usually a paragraph and a link — that is what RSS is —
     * so a reader that shows only what the feed sent shows a teaser. The full
     * page is fetched and put through Readability, which is what "open in the
     * browser" was really being used for.
     */
    enum class FullText { Absent, Loading, Ready, Failed }

    private val _fullText = MutableStateFlow(FullText.Absent)
    val fullText: StateFlow<FullText> = _fullText.asStateFlow()

    /**
     * Fetches the readable article unless it is already stored.
     *
     * On demand, when the article is opened, rather than for every article of
     * every feed during sync: the reader is opened deliberately, and one page
     * fetched on a tap costs far less than the browser it replaces. A feed
     * with "fetch full articles" set still prefetches during sync, and this
     * finds that work already done.
     */
    fun loadFullText(id: String, link: String?, filesDir: File) {
        if (link.isNullOrBlank()) {
            _fullText.value = FullText.Failed
            return
        }
        if (blobFullFile(id, filesDir).isFile) {
            _fullText.value = FullText.Ready
            return
        }
        _fullText.value = FullText.Loading
        ioScope.launch {
            val ok = parseFullArticleIfMissing(
                feedItem = ArticleIdWithLink(uuid = id, link = link),
                okHttpClient = fullTextClient,
                filesDir = filesDir,
            )
            _fullText.value = if (ok) FullText.Ready else FullText.Failed
        }
    }

    /** Reset when the reader moves to another article. */
    fun resetFullText() {
        _fullText.value = FullText.Absent
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val articleState = articleId.flatMapLatest {
        articleRepo.getArticleById(it)
    }.mapLatest {
        it?.let {
            ArticlePageState(
                article = it,
                source = sourcesRepo.loadFeedById(it.feedId),
                isBookmarked = it.bookmarked
            )
        } ?: ArticlePageState()
    }
        .stateIn(
            ioScope,
            SharingStarted.Lazily,
            ArticlePageState()
        )

    fun unpinArticle(id: String) {
        viewModelScope.launch {
            articleRepo.unpinArticle(id)
        }
    }

    fun bookmarkArticle(id: String, boolean: Boolean) {
        viewModelScope.launch {
            articleRepo.bookmarkArticle(id, boolean)
        }
    }
}

data class ArticlePageState(
    val article: Article? = null,
    val source: Feed? = null,
    val isBookmarked: Boolean = false,
)