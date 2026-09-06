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

        parser.parse(InputSource(inputStream))

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
