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
package com.saulhdev.feeder.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.utils.READ_DIM
import org.koin.compose.koinInject
import com.saulhdev.feeder.data.content.asState

/**
 * How much of the screen an article has earned.
 *
 * The Mosaic sizes were positional — item 0 and every eleventh got the big
 * tile, whatever they happened to be — which meant a two-line aggregator stub
 * could take both columns while the story of the day sat in a thumbnail. The
 * weight below is a property of the *article*, so it does not change when a
 * sync reorders the feed: the same article gets the same size wherever it
 * lands, which is the thing the positional rule was protecting against and
 * gets for free here.
 *
 * Every signal is something already known locally. Nothing is inferred from a
 * server, and nothing here needs an account.
 *
 * The numbers are deliberately blunt and all in one place. They are the part
 * that wants tuning against a real feed rather than reasoning.
 */
object ArticleWeight {

    /** No picture, so the big shapes are out however good the article is. */
    const val NO_IMAGE = 0f

    /** Every article with an image starts here. */
    const val BASE = 1f

    /** At or above this an article can take both columns. */
    const val LARGE_AT = 2.2f

    /** At or above this it keeps its own column at full height. */
    const val MEDIUM_AT = 1.6f

    /**
     * How many tiles must pass between two large ones.
     *
     * Without it a burst of fresh articles from a favourite source — exactly
     * what a morning sync produces — turns the whole first screen into full
     * width tiles, which is a list, not a mosaic.
     */
    const val LARGE_GAP = 6

    /**
     * The most a reading habit can add.
     *
     * Deliberately smaller than a single freshness step. "Favour what they
     * read" converges on one site if you let it — shown more, so read more, so
     * weighted higher — and the counter to that is a cap plus the structural
     * rules below, not a smaller number alone. This is enough to break a tie
     * between two similar articles and never enough to outrank a fresh story
     * from somewhere else.
     */
    const val HABIT_MAX = 0.4f

    /** How far back reading habits are counted. */
    const val HABIT_WINDOW_DAYS = 30L

    /**
     * What a story several sources are covering is worth.
     *
     * The largest single term, and deliberately so. This is not a second
     * mechanism sitting beside the weighting and competing with it for the
     * hero slot — it is the weighting's answer to the same question everything
     * else here answers, and the answer that ought to win. Scaled by how many
     * sources are covering it, because three is a story and eight is the story.
     */
    fun breakingBonus(sources: Int): Float = when {
        sources < Clustering.MIN_SOURCES -> 0f
        sources < 5                      -> 1.2f
        sources < 8                      -> 1.6f
        else                             -> 2.0f
    }

    /**
     * How many items must pass before a source may take a second large slot.
     *
     * The first of the two structural diversity rules. A coefficient can be
     * tuned down but it still compounds; this cannot. Whatever the weights
     * say, one source does not own the screen.
     */
    const val SAME_SOURCE_LARGE_GAP = 24

    /**
     * The longest run of articles from one source before the rest are
     * displaced further down.
     *
     * Displaced, never dropped: a source posting six times in an hour is still
     * six articles the reader subscribed to, and they stay in the feed. They
     * just stop being the whole of it.
     */
    const val MAX_CONSECUTIVE_FROM_SOURCE = 3

    /**
     * How far down the feed to look for an opening anchor.
     *
     * A feed of nothing but yesterday's articles scores nothing above
     * [LARGE_AT], and opening on a flat grid of small tiles reads as a bug. The
     * best of the first few gets promoted instead — best of what is actually
     * there, rather than whatever sits at index 0.
     */
    const val ANCHOR_WITHIN = 6
}

/**
 * The weight for one article.
 *
 * @param affinity "more like this" scores by source id, as recorded by the
 *   article menu. This is the first thing that reads them back.
 * @param nowMs passed in rather than read, so a list is scored against one
 *   instant and an article near a boundary cannot be scored twice at two
 *   different ages within the same pass.
 */
fun articleWeight(
    item: FeedItem,
    affinity: Map<String, Int>,
    nowMs: Long,
    habit: Map<Long, Float> = emptyMap(),
    clusters: Map<String, StoryCluster> = emptyMap(),
): Float {
    if (item.article.imageUrl.isNullOrBlank()) return ArticleWeight.NO_IMAGE

    var weight = ArticleWeight.BASE

    // Freshness. A news surface that gives its biggest slot to something from
    // Tuesday is not a news surface.
    val ageHours = ((nowMs - item.timeMillis).coerceAtLeast(0L)) / 3_600_000f
    weight += when {
        ageHours < 2f  -> 1.2f
        ageHours < 6f  -> 0.8f
        ageHours < 24f -> 0.4f
        ageHours < 72f -> 0f
        else           -> -0.4f
    }

    // What the reader has said about the source, clamped so a dozen taps on
    // one source cannot make every one of its articles large for ever.
    weight += (affinity[item.sourceId] ?: 0).coerceIn(-3, 3) * 0.35f

    // Headline length. This is about the shape on screen, not the writing: a
    // twenty-word headline set at titleMedium fills the large tile and pushes
    // the summary off it, which wastes the width it was given.
    val words = item.contentTitle.trim().split(Regex("\\s+")).size
    weight += when {
        words < 3   -> -0.2f
        words <= 12 -> 0.4f
        words <= 18 -> 0f
        else        -> -0.25f
    }

    // The large tile shows a line of summary. Without one it is a picture and a
    // headline in a lot of empty space.
    weight += if (item.article.description.isNotBlank()) 0.3f else -0.3f

    // Several sources on the same story. Only the lead is promoted: the others
    // are the same headline, and five large cards saying it would be worse
    // than one. They keep whatever they earned on their own.
    clusters[item.id]?.let { cluster ->
        if (cluster.leadId == item.id) weight += ArticleWeight.breakingBonus(cluster.sources)
    }

    // What the reader actually reads, as opposed to what they said. Taps on
    // "more like this" are rare and deliberate; opening an article is neither,
    // which is why this is capped so much lower than affinity.
    weight += (habit[item.feed.id] ?: 0f) * ArticleWeight.HABIT_MAX

    // Already read: it has had its turn.
    if (item.article.readAt != 0L) weight -= 1.5f

    // Saved, and pinned, are the reader saying this one matters.
    if (item.bookmarked) weight += 0.4f
    if (item.pinned) weight += 1.5f

    return weight
}

/**
 * Sizes for a whole list, in order.
 *
 * A list rather than a per-item function because two of the rules are about
 * neighbours — large tiles have to be spaced out, and the top of the feed needs
 * an anchor — and neither can be answered by looking at one article.
 */
fun feedEmphasisFor(
    items: List<FeedItem>,
    affinity: Map<String, Int>,
    nowMs: Long,
    habit: Map<Long, Float> = emptyMap(),
    clusters: Map<String, StoryCluster> = emptyMap(),
): List<FeedEmphasis> {
    val weights = items.map { articleWeight(it, affinity, nowMs, habit, clusters) }
    val sizes = MutableList(items.size) { FeedEmphasis.Small }

    var lastLarge = -ArticleWeight.LARGE_GAP - 1
    // The second structural rule: a source that has just had the big slot does
    // not get another one straight away, however well its next article scores.
    // Without it, a morning sync from a favourite source takes every large slot
    // on the screen and the feed reads as one publication.
    val lastLargeBySource = HashMap<String, Int>()
    weights.forEachIndexed { index, weight ->
        val source = items[index].sourceId
        val sourceGap = index - (lastLargeBySource[source] ?: Int.MIN_VALUE / 2)
        sizes[index] = when {
            weight >= ArticleWeight.LARGE_AT &&
                    index - lastLarge > ArticleWeight.LARGE_GAP &&
                    sourceGap > ArticleWeight.SAME_SOURCE_LARGE_GAP -> {
                lastLarge = index
                lastLargeBySource[source] = index
                FeedEmphasis.Large
            }

            // A large-weight article that lands inside either gap still
            // deserves more than the smallest tile.
            weight >= ArticleWeight.MEDIUM_AT                       -> FeedEmphasis.Medium
            else                                                    -> FeedEmphasis.Small
        }
    }

    // The opening anchor, if the scores did not produce one.
    val head = minOf(ArticleWeight.ANCHOR_WITHIN, items.size)
    if (head > 0 && sizes.take(head).none { it == FeedEmphasis.Large }) {
        val best = (0 until head)
            .filter { weights[it] > ArticleWeight.NO_IMAGE }
            .maxByOrNull { weights[it] }
        if (best != null) sizes[best] = FeedEmphasis.Large
    }

    return sizes
}

/**
 * The affinity preference, as a map.
 *
 * Stored as `sourceId:score` strings because DataStore has no map type; parsed
 * from the last colon so a source id containing one still splits correctly.
 */
fun parseAffinity(raw: Set<String>): Map<String, Int> = raw.mapNotNull { entry ->
    val at = entry.lastIndexOf(':')
    if (at <= 0) null
    else entry.substring(0, at) to (entry.substring(at + 1).toIntOrNull() ?: 0)
}.toMap()

/**
 * The sizes for the articles on screen, recomputed only when they change.
 *
 * Both surfaces need the same answer and neither should be threading an
 * affinity map through its own parameter list to get it, so the preference is
 * read here.
 */
@Composable
fun rememberFeedEmphasis(articles: List<FeedItem>): List<FeedEmphasis> {
    val prefs: FeedPreferences = koinInject()
    val raw by prefs.sourceAffinity.get().collectAsState(initial = emptySet())
    val affinity = remember(raw) { parseAffinity(raw) }
    val habit = rememberReadingHabits()
    val clusters = rememberStoryClusters(articles)

    // An article keeps the size it was first given. Without this the feed
    // reflows under the reader's finger: marking an article read subtracts
    // from its weight, so scrolling past one with read-on-scroll enabled
    // shrank it from a card to a row and shunted everything below it up the
    // screen. Every article did it in turn, so the whole list jumped
    // continuously while being scrolled.
    //
    // Read state was the visible cause but not the only one. Marking read also
    // rewrites the reading-habit counts and re-runs the clustering, so all
    // three inputs change identity at once and keying a cache on any of them
    // would defeat it.
    return remember(articles, affinity, habit, clusters) {
        FeedEmphasisMemory.settle(
            articles,
            feedEmphasisFor(articles, affinity, System.currentTimeMillis(), habit, clusters),
        )
    }
}

/**
 * The size each article has already been given, kept beyond any one screen.
 *
 * This was a `remember` inside [rememberFeedEmphasis], which fixed the overlay
 * and left the app broken in a way that looked unrelated. The overlay's feed
 * composes once and stays composed — opening an article launches a separate
 * activity over the top of it. The app's feed is the list pane of a
 * ListDetailPaneScaffold, and on a phone opening an article *replaces* it:
 * come back and the whole feed has been composed afresh, with an empty map,
 * so every size is recalculated — now with the article you just read weighed
 * down for having been read. The article you had just finished reading was the
 * one that shrank, which is why it read as a deliberate behaviour rather than
 * a bug.
 *
 * Held here, outside composition, so both surfaces answer the same way and
 * neither depends on how long its own screen happens to live.
 *
 * Synchronised rather than concurrent: it is touched once per feed
 * recomposition, from the main thread, on two surfaces that are never both
 * on screen. The lock is for the case where they are both composed — the app
 * behind, the panel in front — rather than for contention.
 */
internal object FeedEmphasisMemory {

    private val settled = LinkedHashMap<String, FeedEmphasis>()

    /**
     * Beyond this many remembered sizes, forget the ones not on screen.
     *
     * Generous on purpose. The feed window is a few hundred articles, and the
     * cost of forgetting one is that it changes size the next time it is seen
     * — so this should only ever be reached by somebody who has scrolled
     * through several filtered views in one sitting.
     */
    private const val MAX_REMEMBERED = 2_000

    @Synchronized
    fun settle(articles: List<FeedItem>, fresh: List<FeedEmphasis>): List<FeedEmphasis> {
        articles.forEachIndexed { index, item ->
            settled.getOrPut(item.id) { fresh.getOrNull(index) ?: FeedEmphasis.Medium }
        }
        if (settled.size > MAX_REMEMBERED) {
            val onScreen = articles.mapTo(HashSet()) { it.id }
            settled.keys.retainAll { it in onScreen }
        }
        return articles.map { settled[it.id] ?: FeedEmphasis.Medium }
    }

    /** For tests, which must not inherit sizes from one another. */
    @Synchronized
    fun forget() = settled.clear()
}

/**
 * The stories several sources are covering, recomputed only when the feed
 * changes.
 *
 * Off unless the reader has asked for it. The detection is inference, and
 * inference is occasionally wrong in public — a feed that suddenly gives a
 * hero slot to the wrong article, for reasons the reader did not ask for, is
 * worse than one that never tries.
 */
@Composable
fun rememberStoryClusters(articles: List<FeedItem>): Map<String, StoryCluster> {
    val prefs: FeedPreferences = koinInject()
    val enabled by prefs.breakingNews.asState()
    return remember(articles, enabled) {
        if (!enabled) emptyMap()
        else clusterStories(articles, System.currentTimeMillis())
    }
}

/**
 * How much each source is actually read, normalised against the most-read one.
 *
 * Entirely local, and it needs no account: `readAt` has been on every article
 * since the day read state existed, so the history is already here. An account
 * would only carry these counts to a second phone, which is the sync
 * milestone's job rather than a prerequisite for this.
 *
 * Normalised rather than absolute so the term means the same thing to someone
 * who reads four articles a week and someone who reads four hundred.
 */
@Composable
fun rememberReadingHabits(): Map<Long, Float> {
    val repo: ArticleRepository = koinInject()
    val since = remember {
        System.currentTimeMillis() -
                ArticleWeight.HABIT_WINDOW_DAYS * 24 * 60 * 60 * 1000
    }
    val counts by remember(since) { repo.readsPerSource(since) }
        .collectAsState(initial = emptyMap())
    return remember(counts) {
        val most = counts.values.maxOrNull()?.takeIf { it > 0 } ?: return@remember emptyMap()
        counts.mapValues { (_, reads) -> reads.toFloat() / most }
    }
}

/**
 * Whether read articles should be faded, for the current setting.
 *
 * A separate reader from the hide rule, which lives in the view model where
 * the list is built: fading is a rendering decision and removing is a
 * filtering one, and putting both in the same place would mean the feed had to
 * be rebuilt to change how it looks.
 */
@Composable
fun rememberDimRead(): Boolean {
    val prefs: FeedPreferences = koinInject()
    val setting by prefs.readVisibility.asState()
    return setting == READ_DIM
}

/**
 * One line of the answer to "why is this the size it is".
 *
 * @param labelId what the signal is, in the reader's words.
 * @param amount how much it added or took away, so the biggest reason is
 *   identifiable rather than merely listed.
 */
data class WeightReason(
    val labelId: Int,
    val amount: Float,
)

/**
 * Why one article got the emphasis it did, in the order that matters most.
 *
 * Derived from the same terms [articleWeight] sums, and deliberately so: an
 * explanation computed separately from the thing it explains drifts the first
 * time either is edited, and then confidently says the wrong thing. Every
 * branch here mirrors one there.
 *
 * The whole point of the weighting is that a reader can disagree with it.
 * That needs the reasons visible at the article, not only the totals on a
 * settings screen — by the time someone is in Settings they have stopped
 * asking about the article that prompted the question.
 */
fun weightReasons(
    item: FeedItem,
    affinity: Map<String, Int>,
    nowMs: Long,
    habit: Map<Long, Float> = emptyMap(),
    clusters: Map<String, StoryCluster> = emptyMap(),
): List<WeightReason> {
    if (item.article.imageUrl.isNullOrBlank()) {
        return listOf(WeightReason(R.string.why_no_image, 0f))
    }

    val reasons = mutableListOf<WeightReason>()

    clusters[item.id]?.let { cluster ->
        if (cluster.leadId == item.id) {
            reasons += WeightReason(
                R.string.why_breaking,
                ArticleWeight.breakingBonus(cluster.sources),
            )
        }
    }

    val ageHours = ((nowMs - item.timeMillis).coerceAtLeast(0L)) / 3_600_000f
    when {
        ageHours < 2f  -> reasons += WeightReason(R.string.why_very_fresh, 1.2f)
        ageHours < 6f  -> reasons += WeightReason(R.string.why_fresh, 0.8f)
        ageHours < 24f -> reasons += WeightReason(R.string.why_today, 0.4f)
        ageHours >= 72f -> reasons += WeightReason(R.string.why_old, -0.4f)
    }

    val score = (affinity[item.sourceId] ?: 0).coerceIn(-3, 3)
    if (score != 0) reasons += WeightReason(R.string.why_affinity, score * 0.35f)

    val read = (habit[item.feed.id] ?: 0f) * ArticleWeight.HABIT_MAX
    if (read > 0.05f) reasons += WeightReason(R.string.why_read_often, read)

    val words = item.contentTitle.trim().split(Regex("\\s+")).size
    when {
        words < 3   -> reasons += WeightReason(R.string.why_title_short, -0.2f)
        words <= 12 -> reasons += WeightReason(R.string.why_title_good, 0.4f)
        words > 18  -> reasons += WeightReason(R.string.why_title_long, -0.25f)
    }

    reasons += if (item.article.description.isNotBlank())
        WeightReason(R.string.why_has_summary, 0.3f)
    else WeightReason(R.string.why_no_summary, -0.3f)

    if (item.article.readAt != 0L) reasons += WeightReason(R.string.why_read, -1.5f)
    if (item.bookmarked) reasons += WeightReason(R.string.why_saved, 0.4f)
    if (item.pinned) reasons += WeightReason(R.string.why_pinned, 1.5f)

    return reasons.sortedByDescending { kotlin.math.abs(it.amount) }
}

/**
 * The reasons for one article, with the signals read from preferences.
 *
 * A composable so a card can ask without being handed two maps it has no
 * other use for. It is cheap — the two flows behind it are already collected
 * once per feed by [rememberFeedEmphasis], and the reasons themselves are a
 * dozen comparisons.
 */
@Composable
fun rememberWeightReasons(
    item: FeedItem,
    clusters: Map<String, StoryCluster> = emptyMap(),
): List<WeightReason> {
    val prefs: FeedPreferences = koinInject()
    val raw by prefs.sourceAffinity.get().collectAsState(initial = emptySet())
    val affinity = remember(raw) { parseAffinity(raw) }
    val habit = rememberReadingHabits()
    return remember(item.id, affinity, habit, clusters) {
        weightReasons(item, affinity, System.currentTimeMillis(), habit, clusters)
    }
}
