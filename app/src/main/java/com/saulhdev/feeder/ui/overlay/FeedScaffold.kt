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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.manager.glance.GlanceState
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.DotsThreeVertical
import com.saulhdev.feeder.ui.components.HeaderToggleAction
import com.saulhdev.feeder.ui.components.HeaderAction
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.FunnelSimple
import kotlinx.coroutines.launch
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import com.saulhdev.feeder.ui.pages.SortFilterSheet
import com.saulhdev.feeder.ui.components.FeedSearchBar
import com.saulhdev.feeder.ui.icons.phosphor.MagnifyingGlass
import com.saulhdev.feeder.ui.components.SearchEmptyState

/**
 * The whole minus-one surface: header, category strip and article list.
 *
 * These are one composable rather than three converted pieces because they are
 * joined by scrolling. The View version coupled them through CoordinatorLayout
 * — the app bar collapsed in response to the RecyclerView's nested scroll, a
 * SwipeRefreshLayout wrapped the list, and a scroll listener drove the
 * scroll-to-top button. Converting the header or the list alone would have
 * meant building View/Compose nested-scroll interop to preserve that, only to
 * delete it when the other half followed. Here the coupling is native.
 *
 * Insets are passed in rather than read from `WindowInsets`. This window is
 * created against the launcher's token with an unusual flag set, so the
 * overlay's own inset listener is the value already known to be correct.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScaffold(
    articles: List<FeedItem>,
    categories: List<String>,
    selectedCategories: Set<String>,
    isRefreshing: Boolean,
    isFilterActive: Boolean,
    isShowingBookmarks: Boolean,
    glanceState: GlanceState,
    topInset: Dp,
    bottomInset: Dp,
    onCategoriesChange: (Set<String>) -> Unit,
    onRefresh: () -> Unit,
    onArticleClick: (FeedItem) -> Unit,
    onBookmark: (FeedItem, Boolean) -> Unit,
    onShare: (FeedItem) -> Unit,
    onMoreLikeThis: (FeedItem) -> Unit,
    onLessLikeThis: (FeedItem) -> Unit,
    onHideSource: (FeedItem) -> Unit,
    hiddenSource: FeedItem?,
    onUndoHideSource: () -> Unit,
    onDismissHideSource: () -> Unit,
    layout: String,
    isFilterSheetOpen: Boolean,
    onFilterSheetOpenChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    onSearchingChange: (Boolean) -> Unit,
    onBookmarksClick: () -> Unit,
    onSettings: () -> Unit,
    onArticleSeen: (FeedItem) -> Unit = {},
    onPin: (FeedItem, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)

    // Matches the old scroll listener's threshold, which only offered the jump
    // back once a few articles had gone by.
    val isGrid = feedLayoutIsGrid(layout)
    val showScrollToTop by remember(isGrid) {
        derivedStateOf {
            if (isGrid) gridState.firstVisibleItemIndex > 5
            else listState.firstVisibleItemIndex > 5
        }
    }

    // Hiding a source is one tap with no confirmation, so the way back is
    // offered here rather than left to the sources screen.
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(hiddenSource) {
        val item = hiddenSource ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.source_hidden, item.feedTitle),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoHideSource()
        else onDismissHideSource()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSearching) {
                FeedSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = { onSearchingChange(false) },
                    modifier = Modifier.padding(top = topInset),
                )
            } else TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // ic_launcher_foreground is an adaptive-icon layer:
                        // the mark fills only half its canvas, so a 34dp box
                        // drew a 17dp mark — the same height as the word beside
                        // it. ic_brand_mark is the same artwork cropped to its
                        // own bounds, so the size here is the size drawn.
                        Image(
                            painter = painterResource(R.drawable.ic_brand_mark),
                            contentDescription = null,
                            modifier = Modifier.height(36.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    // Both of these are modes rather than one-shot actions, so
                    // they have to show when they are on — the View header only
                    // ever tinted the bookmark toggle, and the filter gave no
                    // indication at all that it was narrowing the feed.
                    HeaderAction(
                        icon = Phosphor.MagnifyingGlass,
                        description = stringResource(R.string.action_search),
                        onClick = { onSearchingChange(true) },
                    )
                    HeaderToggleAction(
                        icon = Phosphor.FunnelSimple,
                        description = stringResource(R.string.pref_cat_filters),
                        active = isFilterActive,
                        onClick = { onFilterSheetOpenChange(true) },
                    )
                    HeaderToggleAction(
                        painter = painterResource(R.drawable.ic_whisper_save),
                        description = stringResource(R.string.title_bookmarks),
                        active = isShowingBookmarks,
                        onClick = onBookmarksClick,
                    )
                    // One action, not a menu: reload duplicates pull-to-refresh
                    // and restart was a development leftover, which left
                    // Settings as the only real entry. It keeps the three dots
                    // rather than taking a gear, so the two headers read the
                    // same way.
                    HeaderAction(
                        icon = Phosphor.DotsThreeVertical,
                        description = stringResource(R.string.title_settings),
                        onClick = onSettings,
                    )
                },
                // The overlay draws its own background, so the bar stays
                // transparent and the feed shows through behind it.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                scrollBehavior = scrollBehavior,
                // The bar insets for the status bar itself by default, and
                // this window is already given the inset explicitly — see
                // topInset, which is measured by the overlay's own listener
                // because this window's own insets are not to be trusted.
                // Both applied means two status bars of empty space above the
                // glance row once the bar scrolls away.
                windowInsets = WindowInsets(0),
                modifier = Modifier.padding(top = topInset),
            )

            GlanceRow(
                state = glanceState,
                // Straight to settings. It used to open the overflow menu, so
                // "set a location" meant opening a menu and then finding
                // settings in it.
                onSetLocation = onSettings,
            )

            // Hidden while searching: a search deliberately ignores the
            // selected category, so leaving the chips up — one of them
            // highlighted — would claim a narrowing that is not happening.
            if (!isSearching) {
                CategoryChipRow(
                    categories = categories,
                    selected = selectedCategories,
                    onSelectedChange = onCategoriesChange,
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val padding = PaddingValues(
                    top = 4.dp,
                    bottom = bottomInset + 16.dp,
                )
                // Mosaic is a staggered grid rather than a column, so the
                // container changes with the layout and not only the shapes
                // inside it. Everything else is a single column.
                // Which article gets the big shape is one decision for every
                // layout, so it is made once here rather than per container.
                val emphasis = rememberFeedEmphasis(articles)
                val dimRead = rememberDimRead()
                val clusters = rememberStoryClusters(articles)
                val held = rememberHeldArticle(articles, clusters)
                MarkReadWhileScrolling(
                    articles = articles,
                    isGrid = isGrid,
                    listState = listState,
                    gridState = gridState,
                    onRead = onArticleSeen,
                )
                if (isSearching && searchQuery.isNotBlank() && articles.isEmpty()) {
                    SearchEmptyState(searchQuery)
                } else if (feedLayoutIsGrid(layout)) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        state = gridState,
                        contentPadding = padding,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                    ) {
                        itemsIndexed(
                            articles,
                            key = { _, item -> item.id },
                            // The grid needs the span before it composes the
                            // item, so the sizes are worked out for the whole
                            // list up front rather than asked per tile.
                            span = { index, _ ->
                                if (emphasis.getOrNull(index) == FeedEmphasis.Large)
                                    StaggeredGridItemSpan.FullLine
                                else StaggeredGridItemSpan.SingleLane
                            },
                        ) { index, item ->
                            FeedArticleItem(
                                item = item,
                                index = index,
                                onClick = { onArticleClick(item) },
                                onBookmark = { onBookmark(item, it) },
                                onShare = { onShare(item) },
                                onMoreLikeThis = { onMoreLikeThis(item) },
                                onLessLikeThis = { onLessLikeThis(item) },
                                onHideSource = { onHideSource(item) },
                                layout = layout,
                                emphasis = emphasis.getOrNull(index)
                                    ?: FeedEmphasis.Medium,
                                dimRead = dimRead,
                                cluster = clusters[item.id],
                                onPin = { onPin(item, it) },
                            )
                        }
                    }
                } else {
                    val article: @Composable (Int, FeedItem) -> Unit = { index, item ->
                        FeedArticleItem(
                            item = item,
                            index = index,
                            onClick = { onArticleClick(item) },
                            onBookmark = { onBookmark(item, it) },
                            onShare = { onShare(item) },
                            onMoreLikeThis = { onMoreLikeThis(item) },
                            onLessLikeThis = { onLessLikeThis(item) },
                            onHideSource = { onHideSource(item) },
                            layout = layout,
                            emphasis = emphasis.getOrNull(index) ?: FeedEmphasis.Medium,
                            dimRead = dimRead,
                            cluster = clusters[item.id],
                            onPin = { onPin(item, it) },
                        )
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = padding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        heldFeed(articles, held, article)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 16.dp),
        )

        // The same sort-and-filter screen the app shows, rather than the
        // View-based bottom sheet this used to open — which was the last
        // screen in the overlay still built out of XML, and looked it.
        //
        // Drawn inside this composition rather than as a ModalBottomSheet: a
        // modal sheet is its own window, and this window is created against
        // the launcher's token with an unusual flag set. Everything already
        // here is laid out by hand for that reason, so the sheet is too.
        AnimatedVisibility(
            visible = isFilterSheetOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onFilterSheetOpenChange(false) }
            )
        }

        AnimatedVisibility(
            visible = isFilterSheetOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge.copy(
                    bottomStart = CornerSize(0.dp),
                    bottomEnd = CornerSize(0.dp),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = bottomInset),
                ) {
                    // A grab handle, because without a modal sheet's own
                    // chrome there is nothing else saying this is dismissible.
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    SortFilterSheet(onDismiss = { onFilterSheetOpenChange(false) })
                }
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = bottomInset + 24.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        if (isGrid) gridState.animateScrollToItem(0)
                        else listState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Phosphor.CaretUp, stringResource(R.string.back_to_top))
            }
        }
    }
}

