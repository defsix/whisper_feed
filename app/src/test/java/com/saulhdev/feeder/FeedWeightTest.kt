package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.ArticleWeight
import com.saulhdev.feeder.ui.overlay.FeedEmphasis
import com.saulhdev.feeder.ui.overlay.articleWeight
import com.saulhdev.feeder.ui.overlay.feedEmphasisFor
import com.saulhdev.feeder.ui.overlay.parseAffinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import kotlin.time.Instant

private const val NOW = 1_800_000_000_000L
private var seq = 0

private fun item(
    source: String = "BBC",
    hoursAgo: Long = 1,
    title: String = "A perfectly ordinary headline about something",
    description: String = "A summary.",
    read: Boolean = false,
    saved: Boolean = false,
    pinned: Boolean = false,
    image: String? = "https://example.com/a.jpg",
) = FeedItem(
    article = Article(
        uuid = "w${seq++}",
        title = title,
        description = description,
        imageUrl = image,
        readAt = if (read) NOW - 1000 else 0L,
        bookmarked = saved,
        pinned = pinned,
        primarySortTime = Instant.fromEpochMilliseconds(NOW - hoursAgo * 3_600_000),
    ),
    feed = Feed(
        id = source.hashCode().toLong(),
        title = source,
        url = URL("https://${source.lowercase()}.example/feed"),
    ),
)

/**
 * The weighting decides how much of the screen an article gets, so getting it
 * wrong is visible on every scroll. These pin the properties that were argued
 * for rather than the exact numbers, which are still being tuned — a test that
 * asserted 2.5 would fail on every adjustment without ever catching a bug.
 */
class FeedWeightTest {

    private fun weight(item: FeedItem, affinity: Map<String, Int> = emptyMap()) =
        articleWeight(item, affinity, NOW)

    @Test
    fun `an article with no picture can never reach the large band`() {
        val perfect = item(image = null, hoursAgo = 0, saved = true, pinned = true)
        assertTrue(weight(perfect) < ArticleWeight.LARGE_AT)
    }

    @Test
    fun `fresher outranks older, all else equal`() {
        assertTrue(weight(item(hoursAgo = 1)) > weight(item(hoursAgo = 12)))
        assertTrue(weight(item(hoursAgo = 12)) > weight(item(hoursAgo = 100)))
    }

    @Test
    fun `reading an article takes it out of the running`() {
        val unread = item()
        val read = item(read = true)
        assertTrue(weight(read) < weight(unread))
        assertTrue(weight(read) < ArticleWeight.MEDIUM_AT)
    }

    @Test
    fun `a habit cannot outrank a fresh story from elsewhere`() {
        // The property the cap exists for: favouring what someone reads must
        // not become "only ever show that one site".
        val favourite = item(source = "Favourite", hoursAgo = 30)
        val strangerButFresh = item(source = "Stranger", hoursAgo = 1)
        val habit = mapOf(favourite.feed.id to 1f)
        assertTrue(
            articleWeight(favourite, emptyMap(), NOW, habit) <
                    articleWeight(strangerButFresh, emptyMap(), NOW, habit)
        )
    }

    @Test
    fun `affinity is clamped, so a dozen taps cannot own the feed forever`() {
        val ten = mapOf(item().sourceId to 10)
        val three = mapOf(item().sourceId to 3)
        val subject = item()
        assertEquals(
            articleWeight(subject, mapOf(subject.sourceId to 3), NOW),
            articleWeight(subject, mapOf(subject.sourceId to 10), NOW),
            0.001f,
        )
        assertTrue(ten.isNotEmpty() && three.isNotEmpty())
    }

    @Test
    fun `large tiles are spaced out even when everything scores highly`() {
        val items = (0 until 40).map { item(source = "S$it", hoursAgo = 0) }
        val sizes = feedEmphasisFor(items, emptyMap(), NOW)
        val larges = sizes.withIndex().filter { it.value == FeedEmphasis.Large }.map { it.index }
        larges.zipWithNext().forEach { (a, b) ->
            assertTrue("large tiles at $a and $b are too close", b - a > ArticleWeight.LARGE_GAP)
        }
    }

    @Test
    fun `one source cannot take every large slot`() {
        // A morning sync from a favourite source: forty fresh articles, all
        // from one publisher, all scoring identically.
        val items = (0 until 40).map { item(source = "OnlySource", hoursAgo = 0) }
        val sizes = feedEmphasisFor(items, emptyMap(), NOW)
        val larges = sizes.withIndex().filter { it.value == FeedEmphasis.Large }.map { it.index }
        larges.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "same source took large slots at $a and $b",
                b - a > ArticleWeight.SAME_SOURCE_LARGE_GAP,
            )
        }
    }

    @Test
    fun `a flat feed of old articles still opens on something`() {
        // Nothing here clears the large threshold on its own; opening on a
        // uniform wall of small cards reads as a bug rather than a choice.
        val items = (0 until 10).map { item(source = "S$it", hoursAgo = 200) }
        val sizes = feedEmphasisFor(items, emptyMap(), NOW)
        assertTrue(
            "no anchor near the top",
            sizes.take(ArticleWeight.ANCHOR_WITHIN).any { it == FeedEmphasis.Large },
        )
    }

    @Test
    fun `nothing is ever removed, only resized`() {
        val items = (0 until 25).map { item(source = "S$it", read = it % 2 == 0) }
        assertEquals(items.size, feedEmphasisFor(items, emptyMap(), NOW).size)
    }

    @Test
    fun `saving an article is not the same as pinning it`() {
        // These used to be one switch: bookmarking set both, so a saved
        // article collected both bonuses and pinning meant nothing on its own.
        val saved = item(saved = true)
        val pinned = item(pinned = true)
        val both = item(saved = true, pinned = true)
        assertTrue(weight(saved) < weight(pinned))
        assertTrue(weight(pinned) < weight(both))
    }

    @Test
    fun `affinity parses back the way it was written`() {
        assertEquals(mapOf("12" to -2, "7" to 4), parseAffinity(setOf("12:-2", "7:4")))
        // Junk in the set must not take the rest of it down with it.
        assertEquals(mapOf("3" to 1), parseAffinity(setOf("3:1", "nonsense", "", ":9")))
    }
}
