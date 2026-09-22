package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ensureLeadImage
import com.saulhdev.feeder.utils.imageIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup

/**
 * The reader shows the picture the card showed.
 *
 * Two failures, one cause: the article body and the feed entry name the same
 * photograph differently, and the reader was using only the body's name.
 *
 * The Verge has no name for it at all. Its lead image is a Next.js fill image
 * — an `<img>` with no `src` and no `srcset`, the address supplied by
 * JavaScript after load. Fetched, the page carries it only in `og:image`, so
 * no reader working from the body's markup can find it, and every Verge
 * article opened without a picture while its card carried one.
 *
 * Gear Patrol has a different name for it: the feed offers
 * `…16x9-Lead-1.webp` and the body `…16x9-Lead-1.jpg?w=1920`. One asset, two
 * addresses, two cache entries, two downloads and two chances to fail.
 */
class LeadImageTest {

    private fun render(html: String, lead: String?): String {
        val body = Jsoup.parse(html, "https://example.com/story").body()
        ensureLeadImage(body, lead)
        return body.html()
    }

    private fun sources(html: String, lead: String?): List<String> {
        val body = Jsoup.parse(html, "https://example.com/story").body()
        ensureLeadImage(body, lead)
        return body.select("img").map { it.attr("src") }
    }

    @Test
    fun `a body with no picture gets the article's own`() {
        val lead = "https://platform.theverge.com/wp-content/uploads/STK048.png?w=1200"
        val out = sources("<p>Microsoft is laying off 268 Xbox staffers.</p>", lead)
        assertEquals(listOf(lead), out)
    }

    @Test
    fun `it goes above the article, not after it`() {
        val out = render("<p>Body.</p>", "https://cdn.example.com/lead.jpg")
        assertTrue("prepended", out.indexOf("<img") < out.indexOf("<p>"))
    }

    @Test
    fun `a body that already has it is left alone`() {
        val lead = "https://cdn.example.com/photo.jpg?w=1200"
        // The same asset at a different size: the query carries the crop.
        val out = sources("<img src=\"https://cdn.example.com/photo.jpg?w=560\"><p>x</p>", lead)
        assertEquals(1, out.size)
        assertEquals("the body's own copy is not disturbed", "https://cdn.example.com/photo.jpg?w=560", out[0])
    }

    @Test
    fun `the same photograph under another extension is pointed at the cached copy`() {
        val lead =
            "https://www.gearpatrol.com/wp-content/uploads/Caraway-16x9-Lead-1.webp"
        val out = sources(
            "<img src=\"https://www.gearpatrol.com/wp-content/uploads/Caraway-16x9-Lead-1.jpg?w=1920\"><p>x</p>",
            lead,
        )
        assertEquals("no second image", 1, out.size)
        assertEquals("and the one the card already fetched", lead, out[0])
    }

    @Test
    fun `a genuinely different first picture does not get rewritten`() {
        val lead = "https://cdn.example.com/hero.jpg"
        val out = sources("<img src=\"https://cdn.example.com/author-headshot.jpg\"><p>x</p>", lead)
        assertEquals("the lead is added, not substituted", 2, out.size)
        assertEquals(lead, out[0])
        assertEquals("https://cdn.example.com/author-headshot.jpg", out[1])
    }

    @Test
    fun `an image with no address is not mistaken for the lead`() {
        // The Verge's fill image, exactly as served.
        val lead = "https://cdn.example.com/hero.jpg"
        val out = sources("<img alt=\"Vector collage of the Xbox logo.\"><p>x</p>", lead)
        assertEquals(lead, out.first())
    }

    @Test
    fun `no lead means no change`() {
        assertEquals(emptyList<String>(), sources("<p>Body.</p>", null))
        assertEquals(emptyList<String>(), sources("<p>Body.</p>", "   "))
    }

    @Test
    fun `identity drops the scheme, the query and the fragment`() {
        assertEquals(
            imageIdentity("https://cdn.example.com/a/b.jpg?w=1&h=2#x"),
            imageIdentity("http://cdn.example.com/a/b.jpg?w=999"),
        )
    }
}
