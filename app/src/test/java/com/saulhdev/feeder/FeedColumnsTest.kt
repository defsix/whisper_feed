package com.saulhdev.feeder

import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.LAYOUT_MOSAIC
import com.saulhdev.feeder.ui.overlay.feedColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A tablet with no article open drew one card across the whole screen, and
 * its photo at that size stuttered an older chip. Columns, by width.
 */
class FeedColumnsTest {

    @Test
    fun `a phone is unchanged`() {
        assertEquals(1, feedColumns(LAYOUT_CARDS, 411))
        assertEquals(2, feedColumns(LAYOUT_MOSAIC, 411))
    }

    @Test
    fun `a tablet upright has two columns, and three on its side`() {
        assertEquals(2, feedColumns(LAYOUT_CARDS, 800))
        assertEquals(3, feedColumns(LAYOUT_CARDS, 1280))
    }

    @Test
    fun `there are never more than three columns of cards, or four of tiles`() {
        assertEquals(3, feedColumns(LAYOUT_CARDS, 2560))
        assertEquals(4, feedColumns(LAYOUT_MOSAIC, 2560))
        assertEquals(4, feedColumns(LAYOUT_MOSAIC, 800))
    }

    @Test
    fun `beside an open article the feed is one column again`() {
        assertEquals(1, feedColumns(LAYOUT_CARDS, 360))
        assertEquals(2, feedColumns(LAYOUT_MOSAIC, 360))
    }

    private val page = File("src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt").readText()

    @Test
    fun `the feed chooses its container by columns, not by layout alone`() {
        assertTrue(page.contains("val isGridLayout = columns > 1"))
        assertTrue(page.contains("val feedWidthDp = if (articleOpenBeside) FEED_BESIDE_ARTICLE_DP else windowWidthDp"))
        assertTrue(page.contains("columns = columns,"))
        assertTrue("reading still tracked in columns", page.contains("isGrid = isGridLayout,"))
        // The first build asked whether the detail pane was expanded, which a
        // wide screen reports with no article in it: the tablet stayed in one
        // column. It is the selected article that counts.
        assertTrue(page.contains("?.takeIf { it.pane == ListDetailPaneScaffoldRole.Detail }?.contentKey != null"))
        assertTrue(page.contains("val articleOpenBeside = articleSelected &&"))
        assertTrue(!page.contains("scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded"))
    }

    @Test
    fun `a lead story spans the lanes only in Mosaic`() {
        assertTrue(page.contains("if (feedLayoutIsGrid(layout) &&\n                                                            emphasis.getOrNull(index) =="))
    }

    @Test
    fun `the place in the feed survives the switch between one column and several`() {
        assertTrue(page.contains("gridState.scrollToItem(listState.firstVisibleItemIndex)"))
        assertTrue(page.contains("listState.scrollToItem(gridState.firstVisibleItemIndex)"))
    }
}
