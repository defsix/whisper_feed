package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.viewmodels.matchesSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

private fun item(
    title: String = "",
    source: String = "",
    description: String = "",
) = FeedItem(
    article = Article(title = title, description = description),
    feed = Feed(title = source, url = URL("https://example.com/feed")),
)

class ArticleSearchTest {

    @Test
    fun `an empty query matches everything`() {
        assertTrue(item(title = "anything").matchesSearch(""))
        assertTrue(item(title = "anything").matchesSearch("   "))
    }

    @Test
    fun `it finds a headline`() {
        assertTrue(item(title = "NASA launches a satellite").matchesSearch("satellite"))
    }

    @Test
    fun `it finds a source, so "everything from the BBC" works`() {
        assertTrue(item(title = "Something", source = "BBC News").matchesSearch("bbc"))
    }

    @Test
    fun `it finds the summary, which is where the detail usually is`() {
        assertTrue(
            item(title = "Short headline", description = "About the Ballon d'Or")
                .matchesSearch("ballon")
        )
    }

    @Test
    fun `it ignores case and surrounding space`() {
        val article = item(title = "Gemini updates")
        assertTrue(article.matchesSearch("GEMINI"))
        assertTrue(article.matchesSearch("  gemini  "))
    }

    @Test
    fun `it does not match what is not there`() {
        assertFalse(item(title = "Gemini updates", source = "XDA").matchesSearch("cricket"))
    }
}
