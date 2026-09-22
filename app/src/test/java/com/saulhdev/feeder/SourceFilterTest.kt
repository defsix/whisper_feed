package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tapping a source narrows the feed to it, and the way back is obvious.
 *
 * The idea arrived as "make it act like the search" — same bar, same back
 * arrow and X — and the chrome is exactly right. Reusing the *search* for it
 * would not have been, for two reasons that are invisible until they bite:
 *
 *  - `matchesSearch` compares substrings across the headline, the feed title
 *    and the body, so "slate" also returns "tran*slate*", "*slate*d for
 *    release" and every article that merely mentions Slate;
 *  - a search deliberately widens the query from FEED_WINDOW to every article
 *    ever stored, which is the cost a day of work had just removed from the
 *    feed.
 *
 * So the bar is the search bar's sibling and the filter is not the search.
 */
class SourceFilterTest {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    private val viewModel = read("viewmodels/ArticleListViewModel.kt")
    private val page = read("ui/pages/ArticleListPage.kt")
    private val card = read("ui/overlay/ArticleCard.kt")

    @Test
    fun `the filter matches a source's id, not its name`() {
        assertTrue(
            "the source filter is gone, or matches on something other than the id",
            viewModel.contains("focusedSource != null && item.sourceId != focusedSource"),
        )
    }

    @Test
    fun `focusing a source does not widen the query`() {
        // Only `searching` may raise the limit. If the focused source ever
        // joins that condition, tapping a name loads every article stored.
        val limit = viewModel.substringAfter("val limit =").substringBefore('\n')
        assertTrue("the window is no longer chosen by whether a search is running", "searching" in limit)
        assertTrue("focusing a source now widens the query to everything", "focused" !in limit)
    }

    @Test
    fun `a focused source is newest first, whatever the feed is sorted by`() {
        val at = viewModel.indexOf("if (focusedSource != null) {")
        assertTrue("the ordering override is gone", at > 0)
        val body = viewModel.substring(at, at + 200)
        assertTrue(
            "a focused source no longer sorts by time",
            body.contains("sortedByDescending { it.timeMillis }"),
        )
        // Before the comparator, or the sort settings would win.
        assertTrue("the override comes after the feed's own sort", at < viewModel.indexOf("val comparator ="))
    }

    @Test
    fun `focusing a source ignores the selected category`() {
        // The tap named a source, not a source within whichever chip happens
        // to be selected. Left in force, tapping a Tech source while the News
        // chip was active opens an empty screen that has obediently done what
        // both instructions said.
        assertTrue(
            "a category selection still narrows a focused source",
            viewModel.contains("(if (searching || focused != null) emptySet() else categories)"),
        )
    }

    @Test
    fun `the narrowing is not persisted`() {
        // The filter sheet's own settings are preferences and survive a
        // restart. This must not: a feed that came back still narrowed, with
        // nothing on screen explaining why, is a feed that has lost most of
        // itself.
        val at = viewModel.indexOf("private val _focusedSource")
        assertTrue("the focused source is gone", at > 0)
        val declaration = viewModel.substring(at, viewModel.indexOf('\n', at))
        assertTrue("the focused source is stored in a preference", "prefs" !in declaration)
        assertTrue(declaration.contains("MutableStateFlow<String?>(null)"))
    }

    @Test
    fun `there are two ways out, and the gesture is one of them`() {
        assertTrue(
            "the back gesture no longer clears the filter",
            page.contains("BackHandler(enabled = focusedSource != null)"),
        )
        assertTrue("the bar is gone", page.contains("SourceFilterBar("))
        assertTrue(
            "the bar no longer clears the filter",
            page.contains("onClear = viewModel::clearFocusedSource"),
        )
    }

    @Test
    fun `the tap target is the source's identity, not the whole row`() {
        // This row sits directly under the headline. A target covering it
        // would take taps meant for the article, and the age and the buttons
        // beside it belong to nobody.
        val at = card.indexOf("val identity =")
        assertTrue("the tappable identity is gone", at > 0)
        assertTrue(
            "the identity is no longer clickable",
            card.substring(at, at + 200).contains("Modifier.clickable"),
        )

        // The span of the clickable Row, from the modifier that makes it
        // tappable to the line that closes it. The mark and the name belong
        // inside; the age is the first thing that must not be.
        val opens = card.indexOf("modifier = identity.weight", at)
        assertTrue("the identity Row is gone", opens > 0)
        val closes = card.indexOf("\n        }", opens)
        assertTrue("the identity Row never closes", closes > opens)
        val target = card.substring(opens, closes)

        assertTrue("the mark left the target", target.contains("SourceMark("))
        assertTrue("the source name left the target", target.contains("text = source,"))
        // Matched on the interpolation rather than the word: `onImage` in the
        // same block contains "age", which is the kind of near-miss that makes
        // a test pass for the wrong reason and then fail for the wrong one.
        assertTrue("the age was pulled into the target", !target.contains("\$age"))
        assertTrue(
            "the age is no longer drawn outside the target",
            card.indexOf("\" · \$age\"", closes) > closes,
        )
    }

    @Test
    fun `a surface that cannot undo the filter is not offered it`() {
        // The launcher panel's back gesture belongs to the launcher, so a
        // filter opened there would have no way out.
        assertTrue(
            "the default is no longer 'cannot focus'",
            card.contains("compositionLocalOf<((String) -> Unit)?> { null }"),
        )
        assertTrue("the app no longer offers it", page.contains("LocalFocusSource provides"))
        val scaffold = read("ui/overlay/FeedScaffold.kt")
        assertTrue(
            "the launcher panel now offers a filter it cannot undo",
            !scaffold.contains("LocalFocusSource"),
        )
    }

    @Test
    fun `the pipeline still takes five flows`() {
        // The sixth silently selects the Array<Any?> overload, where the
        // positions are checked by nobody — which is how the feed once
        // crashed on every launch. The new state travels with the sort model.
        assertTrue(
            "the focused source no longer travels with the sort settings",
            viewModel.contains("combine(sortFilterState, _focusedSource, ::FeedFilter)"),
        )
    }
}
