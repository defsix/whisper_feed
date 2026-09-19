package com.saulhdev.feeder.manager.models

import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ccil.cowan.tagsoup.Parser
import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.util.Stack

class OPMLParser(private val opmlToDb: ParserToDatabase<Feed>) : ContentHandler {

    private val parser: Parser = Parser()
    private val tagStack: Stack<String> = Stack()
    private var isFeedTag = false
    private var ignoring = 0
    var feeds: MutableList<Feed> = mutableListOf()

    init {
        parser.contentHandler = this
    }

    @Throws(IOException::class, SAXException::class)
    suspend fun parseInputStream(inputStream: InputStream) = withContext(Dispatchers.IO) {
        feeds = mutableListOf()
        tagStack.clear()
        isFeedTag = false
        ignoring = 0

        parser.parse(InputSource(xmlSafe(inputStream)))

        for (feed in feeds) {
            opmlToDb.saveItem(feed)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if ("outline" == localName) {
            when {
                ignoring > 0 -> ignoring--
                isFeedTag -> isFeedTag = false
                else -> tagStack.pop()
            }
        }
    }

    override fun processingInstruction(target: String?, data: String?) {
    }

    override fun startPrefixMapping(prefix: String?, uri: String?) {
    }

    override fun ignorableWhitespace(ch: CharArray?, start: Int, length: Int) {
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
    }

    override fun endDocument() {
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes?) {
        if ("outline" == localName) {
            when {
                // Nesting not allowed
                ignoring > 0 || isFeedTag -> ignoring++
                outlineIsFeed(atts) -> {
                    isFeedTag = true
                    val feedTitle = unescape(
                        atts?.getValue("title") ?: atts?.getValue("text")
                        ?: ""
                    )
                    val feed = Feed(
                        title = feedTitle,
                        tag = if (tagStack.isNotEmpty()) tagStack.peek() else "",
                        url = sloppyLinkToStrictURL(atts?.getValue("xmlurl") ?: "")
                    )

                    feeds.add(feed)
                }

                else -> tagStack.push(
                    unescape(
                        atts?.getValue("title")
                            ?: atts?.getValue("text")
                            ?: ""
                    )
                )
            }
        }
    }

    private fun outlineIsFeed(atts: Attributes?): Boolean =
        atts?.getValue("xmlurl") != null

    override fun skippedEntity(name: String?) {
    }

    override fun setDocumentLocator(locator: Locator?) {
    }

    override fun endPrefixMapping(prefix: String?) {
    }

    override fun startDocument() {
    }
}

/**
 * A bare `&` is not XML, and real OPML files are full of them.
 *
 * Of twenty-four country files in one widely-used public feed directory,
 * thirteen fail to parse — "Breaking news, showbiz & celebrity photos" in a
 * title attribute, "World & Nation", and four hundred and seventy-nine more
 * across the set. Anything that writes OPML by string concatenation produces
 * this, which is most things that write OPML.
 *
 * SAX is strict and correct to be: one of those rejects the whole document,
 * so a single unescaped ampersand two hundred feeds in cost the reader all
 * two hundred. Nothing was half-imported — the parse throws before anything
 * is saved — but "Failed to import OPML" was the entire explanation for a
 * file that any other reader would have opened.
 *
 * So ampersands that do not begin an entity are escaped before parsing. Only
 * that: no attempt to repair unclosed tags or stray angle brackets, because
 * those are ambiguous and this is not, and guessing at somebody's
 * subscription list is worse than declining it.
 */
internal fun xmlSafe(input: InputStream): InputStream {
    // Whole file in memory rather than streamed: an OPML of a thousand feeds
    // is a couple of hundred kilobytes, and a streaming fixer would have to
    // buffer across chunk boundaries to recognise an entity anyway.
    val text = input.bufferedReader().use { it.readText() }
    val fixed = BARE_AMPERSAND.replace(text, "&amp;")
    return fixed.byteInputStream()
}

/** An `&` not already starting a named, decimal or hexadecimal entity. */
private val BARE_AMPERSAND =
    Regex("&(?!(?:#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);)")
