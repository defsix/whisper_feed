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
package com.saulhdev.feeder.viewmodels

import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.ui.overlay.ArticleWeight
import com.saulhdev.feeder.ui.overlay.parseAffinity
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * One source, and everything Whisper has concluded about it.
 *
 * @param nudge the weight this source's articles get from the two learned
 *   signals together — the number the feed actually uses, rather than the raw
 *   counts it was derived from. Showing the inputs without the output would
 *   explain the arithmetic and not the effect.
 */
data class LearnedSource(
    val id: Long,
    val title: String,
    val iconUrl: String?,
    val affinity: Int,
    val reads: Int,
    val nudge: Float,
    val hidden: Boolean,
)

/**
 * What the reader has taught the app, laid out so it can be disagreed with.
 *
 * `docs/REFERENCES.md` §4 found no open-source prior art for transparent,
 * resettable preference learning in a feed reader. This is the transparent
 * half: every number the weighting reads from the reader's own behaviour is
 * on one screen, attributed to the source it came from, next to the button
 * that throws it away.
 *
 * Nothing here is inferred or modelled. Affinity is the count of More and Less
 * presses; reads is the count of articles opened. Both are plain tallies the
 * reader can check against their own memory, which is the point — a score
 * nobody can audit is one nobody can correct.
 */
class LearnedViewModel(
    private val articleRepo: ArticleRepository,
    private val sourcesRepo: SourcesRepository,
    private val prefs: FeedPreferences,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    private val since = System.currentTimeMillis() -
            ArticleWeight.HABIT_WINDOW_DAYS * 24 * 60 * 60 * 1000

    val sources: StateFlow<List<LearnedSource>> = combine(
        sourcesRepo.getAllSourcesFlow(),
        prefs.sourceAffinity.get(),
        prefs.hiddenSources.get(),
        articleRepo.readsPerSource(since),
    ) { feeds, affinityRaw, hidden, reads ->
        val affinity = parseAffinity(affinityRaw)
        val most = reads.values.maxOrNull()?.takeIf { it > 0 }
        feeds.map { feed ->
            val id = feed.id
            val score = affinity[id.toString()] ?: 0
            val read = reads[id] ?: 0
            val habit = if (most == null) 0f else read.toFloat() / most
            LearnedSource(
                id = id,
                title = feed.title,
                iconUrl = feed.feedImage.toString()
                    .takeIf { it.isNotBlank() && it != feed.url.toString() },
                affinity = score,
                reads = read,
                // The same arithmetic the weighting does, not an approximation
                // of it: a screen that explains a different sum than the one
                // being run is worse than no screen.
                nudge = score.coerceIn(-3, 3) * 0.35f + habit * ArticleWeight.HABIT_MAX,
                hidden = id.toString() in hidden,
            )
        }.sortedWith(
            compareByDescending<LearnedSource> { it.nudge }.thenBy { it.title.lowercase() }
        )
    }.stateIn(ioScope, SharingStarted.Eagerly, emptyList())

    /** Puts one source back in the feed, without touching anything else. */
    fun unhide(id: Long) {
        ioScope.launch {
            prefs.hiddenSources.setValue(prefs.hiddenSources.getValue() - id.toString())
        }
    }

    /** Forgets one source's scores, leaving every other source alone. */
    fun forget(id: Long) {
        ioScope.launch {
            val remaining = prefs.sourceAffinity.getValue()
                .filterNot { it.substringBeforeLast(':') == id.toString() }
                .toSet()
            prefs.sourceAffinity.setValue(remaining)
        }
    }

    /**
     * Throws away everything learned, in one action.
     *
     * Read state is deliberately not cleared. It is a record of what happened
     * rather than an opinion about it — clearing it would mark a year of read
     * articles unread, which is not what "forget what you have learned" means
     * to anyone who presses it.
     */
    fun resetAll() {
        ioScope.launch {
            prefs.sourceAffinity.setValue(emptySet())
            prefs.hiddenSources.setValue(emptySet())
        }
    }
}
