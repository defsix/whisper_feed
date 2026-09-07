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
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.DotsThreeVertical
import com.saulhdev.feeder.ui.icons.phosphor.FunnelSimple
import kotlinx.coroutines.launch

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
    onFilterClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val appBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)

    // Matches the old scroll listener's threshold, which only offered the jump
    // back once a few articles had gone by.
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 5 }
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
            TopAppBar(
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
                    ToggleAction(
                        icon = Phosphor.FunnelSimple,
                        description = stringResource(R.string.pref_cat_filters),
                        active = isFilterActive,
                        onClick = onFilterClick,
                    )
                    ToggleAction(
                        painter = painterResource(R.drawable.ic_whisper_save),
                        description = stringResource(R.string.title_bookmarks),
                        active = isShowingBookmarks,
                        onClick = onBookmarksClick,
                    )
                    IconButton(onClick = onOverflowClick) {
                        Icon(Phosphor.DotsThreeVertical, stringResource(R.string.title_settings))
                    }
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
                modifier = Modifier.padding(top = topInset),
            )

            GlanceRow(
                state = glanceState,
                onSetLocation = onOverflowClick,
            )

            CategoryChipRow(
                categories = categories,
                selected = selectedCategories,
                onSelectedChange = onCategoriesChange,
            )

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = bottomInset + 16.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(articles, key = { _, item -> item.id }) { index, item ->
                        FeedArticleItem(
                            item = item,
                            index = index,
                            onClick = { onArticleClick(item) },
                            onBookmark = { onBookmark(item, it) },
                            onShare = { onShare(item) },
                            onMoreLikeThis = { onMoreLikeThis(item) },
                            onLessLikeThis = { onLessLikeThis(item) },
                            onHideSource = { onHideSource(item) },
                        )
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

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = bottomInset + 24.dp),
        ) {
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(Phosphor.CaretUp, stringResource(R.string.back_to_top))
            }
        }
    }
}

/**
 * A header action that is a toggle, not a command: filled while it is on so the
 * state of the feed is legible from the header alone.
 */
@Composable
private fun ToggleAction(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) = ToggleAction(description, active, onClick) { Icon(icon, description) }

/**
 * The same toggle for a drawable rather than an ImageVector — the save mark is
 * traced artwork, so it ships as a vector resource rather than Kotlin.
 */
@Composable
private fun ToggleAction(
    painter: Painter,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) = ToggleAction(description, active, onClick) {
    Icon(painter, description, modifier = Modifier.size(22.dp))
}

@Composable
private fun ToggleAction(
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (active) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) { content() }
    } else {
        IconButton(onClick = onClick) { content() }
    }
}
