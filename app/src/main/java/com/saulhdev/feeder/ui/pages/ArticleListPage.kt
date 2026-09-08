/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Saul Henriquez <henriquez.saul@gmail.com>
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

package com.saulhdev.feeder.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import com.saulhdev.feeder.NeoApp
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.components.HeaderToggleAction
import com.saulhdev.feeder.ui.components.OverflowMenu
import com.saulhdev.feeder.ui.components.PullToRefreshStaggeredGrid
import com.saulhdev.feeder.ui.components.PullToRefreshLazyColumn
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.Filter
import com.saulhdev.feeder.ui.icons.phosphor.Filtered
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.Graph
import com.saulhdev.feeder.ui.icons.phosphor.Power
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.extensions.safeShareIntent
import androidx.compose.foundation.gestures.animateScrollBy
import com.saulhdev.feeder.ui.overlay.feedLayoutIsGrid
import com.saulhdev.feeder.utils.VolumeScroll
import com.saulhdev.feeder.ui.overlay.FeedEmphasis
import com.saulhdev.feeder.ui.overlay.rememberFeedEmphasis
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.ui.overlay.FeedArticleItem
import com.saulhdev.feeder.ui.overlay.GlanceRow
import com.saulhdev.feeder.ui.overlay.CategoryChipRow
import kotlinx.coroutines.Dispatchers
import org.koin.compose.koinInject
import com.saulhdev.feeder.ui.components.FeedSearchBar
import com.saulhdev.feeder.ui.components.HeaderAction
import com.saulhdev.feeder.ui.icons.phosphor.MagnifyingGlass
import com.saulhdev.feeder.ui.components.SearchEmptyState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun ArticleListPage(
    prefs: FeedPreferences = koinInject(),
    syncClient: SyncRestClient = koinInject(),
    sourcesRepo: SourcesRepository = koinInject(),
    glanceHolder: com.saulhdev.feeder.manager.glance.GlanceStateHolder = koinInject(),
    viewModel: ArticleListViewModel = koinNeoViewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { glanceHolder.refreshIfStale() }
    val scaffoldState = rememberBottomSheetScaffoldState()
    val paneNavigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val articleId = remember { mutableStateOf("") }

    val state by viewModel.articleListState.collectAsState()
    // Collected once rather than read inside onClick. getValue() is a
    // runBlocking DataStore read, so reading it there blocked the main thread
    // on every article tap — the one moment the UI most has to stay responsive.
    val openMode by prefs.articleOpenMode.get()
        .collectAsState(initial = prefs.articleOpenMode.getValue())
    val bookmarked by viewModel.bookmarksState.collectAsState()
    val layout by prefs.feedLayout.get().collectAsState(initial = LAYOUT_CARDS)
    val searchQuery by viewModel.searchQuery.collectAsState()
    var searching by remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()

    // The same offer the overlay feed makes: hiding a source is one tap from a
    // menu, so the way back is shown where the article disappeared from.
    val snackbarHostState = remember { SnackbarHostState() }
    val hiddenSource by viewModel.recentlyHidden.collectAsState()
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(hiddenSource) {
        val item = hiddenSource ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.source_hidden, item.feedTitle),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoHideSource()
        else viewModel.forgetHiddenSource()
    }

    var showBookmarks by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val showFAB by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }

    // Volume keys, when the reader has turned them on. Whichever container the
    // layout is using is the one that moves; asking both would fight.
    val isGridLayout = feedLayoutIsGrid(layout)
    LaunchedEffect(isGridLayout) {
        VolumeScroll.events.collect { direction ->
            val viewport = if (isGridLayout) gridState.layoutInfo.viewportSize.height
            else listState.layoutInfo.viewportSize.height
            val distance = viewport * VolumeScroll.PAGE_FRACTION * direction
            if (isGridLayout) gridState.animateScrollBy(distance)
            else listState.animateScrollBy(distance)
        }
    }

    BackHandler(scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    NavigableListDetailPaneScaffold(
        navigator = paneNavigator,
        listPane = {
            AnimatedPane {
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    containerColor = Color.Transparent,
                    sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    sheetShape = MaterialTheme.shapes.extraSmall,
                    sheetContent = {
                        if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
                            SortFilterSheet {
                                scope.launch {
                                    scaffoldState.bottomSheetState.partialExpand()
                                }
                            }
                        } else Spacer(modifier = Modifier.height(8.dp))
                    },
                ) {
                    Scaffold(
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            if (searching) FeedSearchBar(
                                query = searchQuery,
                                onQueryChange = viewModel::setSearchQuery,
                                onClose = { searching = false },
                            ) else TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                                ),
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_brand_mark),
                                            contentDescription = null,
                                            modifier = Modifier.height(32.dp),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(text = stringResource(id = R.string.app_name))
                                    }
                                },
                                scrollBehavior = scrollBehavior,
                                actions = {
                                    // The same three actions the overlay's
                                    // header carries, drawn by the same
                                    // component so the two rows sit on one
                                    // rhythm. The bookmarks toggle used to be
                                    // a Surface with its own padding between
                                    // two IconButtons, which is what made the
                                    // spacing look off.
                                    HeaderAction(
                                        icon = Phosphor.MagnifyingGlass,
                                        description = stringResource(R.string.action_search),
                                        onClick = { searching = true },
                                    )

                                    HeaderToggleAction(
                                        icon = if (state.isFilterModified) Phosphor.Filtered
                                        else Phosphor.Filter,
                                        description = stringResource(id = R.string.sorting_order),
                                        active = state.isFilterModified,
                                        onClick = {
                                            scope.launch {
                                                scaffoldState.bottomSheetState.expand()
                                            }
                                        },
                                    )

                                    HeaderToggleAction(
                                        painter = painterResource(R.drawable.ic_whisper_save),
                                        description = stringResource(id = R.string.title_bookmarks),
                                        active = showBookmarks,
                                        onClick = { showBookmarks = !showBookmarks },
                                    )

                                    OverflowMenu {
                                        DropdownMenuItem(
                                            text = {
                                                Text(text = stringResource(id = R.string.title_sources))
                                            },
                                            onClick = {
                                                hideMenu()
                                                navController.navigate(NavRoute.Sources)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Phosphor.Graph,
                                                    contentDescription = null,
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(text = stringResource(id = R.string.title_settings))
                                            },
                                            onClick = {
                                                hideMenu()
                                                navController.navigate(NavRoute.Settings)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Phosphor.GearSix,
                                                    contentDescription = null,
                                                )
                                            }
                                        )
                                        HorizontalDivider()

                                        DropdownMenuItem(
                                            text = {
                                                Text(text = stringResource(id = R.string.action_reload))
                                            },
                                            onClick = {
                                                hideMenu()
                                                scope.launch {
                                                    syncClient.syncAllFeeds()
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Phosphor.ArrowCounterClockwise,
                                                    contentDescription = null,
                                                )
                                            }
                                        )
                                        HorizontalDivider()

                                        DropdownMenuItem(
                                            text = {
                                                Text(text = stringResource(id = R.string.action_restart))
                                            },
                                            onClick = {
                                                hideMenu()
                                                NeoApp.instance!!.restart(false)
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Phosphor.Power,
                                                    contentDescription = null,
                                                )
                                            }
                                        )
                                    }
                                }
                            )
                        },
                        floatingActionButton = {
                            AnimatedVisibility(
                                visible = showFAB,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                FloatingActionButton(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    onClick = {
                                        scope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Phosphor.CaretUp,
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    ) { paddingValues ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            // Same strip as the launcher surface, reading the same
                            // preference, so a category chosen in one is reflected
                            // in the other.
                            val categories by sourcesRepo.getAllTagsFlow()
                                .collectAsState(initial = emptyList())
                            val selectedCategories by prefs.categoryFilter.get()
                                .collectAsState(initial = emptySet())

                            val glance by glanceHolder.state.collectAsState()
                            GlanceRow(
                                state = glance,
                                onSetLocation = { navController.navigate(NavRoute.Settings) },
                            )

                            CategoryChipRow(
                                categories = categories,
                                selected = selectedCategories,
                                onSelectedChange = { picked ->
                                    scope.launch(Dispatchers.IO) {
                                        prefs.categoryFilter.setValue(picked)
                                    }
                                },
                            )

                            when {
                                showBookmarks -> LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(
                                        bookmarked.bookmarkedArticles,
                                        key = { _, item -> item.id },
                                    ) { index, item ->
                                        FeedArticleItem(
                                            item = item,
                                            index = index,
                                            onClick = {
                                                viewModel.markRead(item.id)
                                                if (openMode == FeedPreferences.OPEN_MODE_BROWSER) {
                                                    context.launchView(item.link)
                                                } else {
                                                    scope.launch {
                                                        paneNavigator.navigateTo(
                                                            ListDetailPaneScaffoldRole.Detail,
                                                            item.id
                                                        )
                                                    }
                                                }
                                                scope.launch { viewModel.unpinArticle(item.id) }
                                            },
                                            onBookmark = { viewModel.bookmarkArticle(item.id, it) },
                                            onShare = {
                                                context.safeShareIntent(item.link, item.contentTitle)
                                            },
                                            onMoreLikeThis = {
                                                viewModel.recordAffinity(item.sourceId, 1)
                                            },
                                            onLessLikeThis = {
                                                viewModel.recordAffinity(item.sourceId, -1)
                                            },
                                            onHideSource = {
                                                viewModel.hideSource(item)
                                            },
                                        )
                                    }
                                }

                                // The same shapes and the same rhythm the launcher feed
                                // draws, so the two surfaces are not mistaken for
                                // different apps — and the same container, which for
                                // Mosaic is a staggered grid rather than a column.
                                else          -> {
                                    val article: @Composable (Int, FeedItem, FeedEmphasis) -> Unit =
                                        { index, item, emphasis ->
                                        FeedArticleItem(
                                            item = item,
                                            index = index,
                                            layout = layout,
                                            emphasis = emphasis,
                                            onClick = {
                                                viewModel.markRead(item.id)
                                                if (openMode == FeedPreferences.OPEN_MODE_BROWSER) {
                                                    context.launchView(item.link)
                                                } else {
                                                    scope.launch {
                                                        paneNavigator.navigateTo(
                                                            ListDetailPaneScaffoldRole.Detail,
                                                            item.id
                                                        )
                                                    }
                                                }
                                            },
                                            onBookmark = { viewModel.bookmarkArticle(item.id, it) },
                                            onShare = {
                                                context.safeShareIntent(item.link, item.contentTitle)
                                            },
                                            onMoreLikeThis = { viewModel.recordAffinity(item.sourceId, 1) },
                                            onLessLikeThis = { viewModel.recordAffinity(item.sourceId, -1) },
                                            onHideSource = { viewModel.hideSource(item) },
                                        )
                                    }

                                    // One decision for every layout, made once.
                                    val emphasis = rememberFeedEmphasis(state.articles)
                                    if (searching && searchQuery.isNotBlank() &&
                                        state.articles.isEmpty()
                                    ) {
                                        SearchEmptyState(searchQuery)
                                    } else if (feedLayoutIsGrid(layout)) {
                                        PullToRefreshStaggeredGrid(
                                            isRefreshing = state.isSyncing,
                                            onRefresh = { syncClient.syncAllFeeds() },
                                            gridState = gridState,
                                            content = {
                                                itemsIndexed(
                                                    state.articles,
                                                    key = { _, item -> item.id },
                                                    span = { index, _ ->
                                                        if (emphasis.getOrNull(index) ==
                                                            FeedEmphasis.Large
                                                        ) StaggeredGridItemSpan.FullLine
                                                        else StaggeredGridItemSpan.SingleLane
                                                    },
                                                ) { index, item ->
                                                    article(
                                                        index,
                                                        item,
                                                        emphasis.getOrNull(index)
                                                            ?: FeedEmphasis.Medium,
                                                    )
                                                }
                                            },
                                        )
                                    } else {
                                        PullToRefreshLazyColumn(
                                            isRefreshing = state.isSyncing,
                                            onRefresh = { syncClient.syncAllFeeds() },
                                            listState = listState,
                                            content = {
                                                itemsIndexed(
                                                    state.articles,
                                                    key = { _, item -> item.id },
                                                ) { index, item ->
                                                    article(
                                                        index,
                                                        item,
                                                        emphasis.getOrNull(index)
                                                            ?: FeedEmphasis.Medium,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        detailPane = {
            articleId.value = paneNavigator.currentDestination
                ?.takeIf { it.pane == this.paneRole }?.contentKey
                ?.toString().orEmpty()

            articleId.value.takeIf { it.isNotEmpty() }?.let { id ->
                AnimatedPane {
                    ArticlePage(id) {
                        scope.launch {
                            paneNavigator.navigateBack()
                        }
                    }
                }
            }
        }
    )
}
