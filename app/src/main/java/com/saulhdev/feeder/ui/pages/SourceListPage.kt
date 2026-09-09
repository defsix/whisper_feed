/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team <saulhdev@hotmail.com>
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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.models.exportBookmarks
import com.saulhdev.feeder.manager.models.exportOpml
import com.saulhdev.feeder.manager.models.importBookmarks
import com.saulhdev.feeder.manager.models.importOpml
import com.saulhdev.feeder.ui.components.OverflowMenu
import com.saulhdev.feeder.ui.components.PreferenceGroupHeading
import com.saulhdev.feeder.ui.components.SourceItem
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.BookBookmark
import com.saulhdev.feeder.ui.icons.phosphor.Bookmarks
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowUp
import com.saulhdev.feeder.ui.icons.phosphor.Hash
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import com.saulhdev.feeder.ui.icons.phosphor.SortAscending
import com.saulhdev.feeder.ui.icons.phosphor.SortDescending
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.utils.ApplicationCoroutineScope
import com.saulhdev.feeder.utils.FILE_DATETIME_FORMAT
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.SourceListViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import okhttp3.internal.toLongOrDefault
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Clock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.phosphor.Sort
import com.saulhdev.feeder.ui.icons.phosphor.SubtractSquare
import com.saulhdev.feeder.viewmodels.SourceSort

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SourceListPage(
    viewModel: SourceListViewModel = koinNeoViewModel(),
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val localTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        .format(FILE_DATETIME_FORMAT)
    // TODO reconsider
    val coroutineScope: ApplicationCoroutineScope by inject(ApplicationCoroutineScope::class.java)
    val state by viewModel.state.collectAsState()
    val paneNavigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val sourceId = remember { mutableLongStateOf(-1L) }

    // Removing a source is confirmed on the editor screen, which then closes —
    // so the offer to undo has to be made here, on the screen the user lands
    // back on. The repository holds the removed row until this is answered.
    val snackbarHostState = remember { SnackbarHostState() }
    val recentlyDeleted by viewModel.recentlyDeleted.collectAsState()
    val recentlyDeletedMany by viewModel.recentlyDeletedMany.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val ascending by viewModel.ascending.collectAsState()
    var tagAction by remember { mutableStateOf<TagAction?>(null) }

    // "Not updating" needs a fixed instant to compare against. Computed once
    // per composition of the screen rather than inside each row, where each
    // row would capture its own slightly different "now".
    val staleSince = remember {
        Clock.System.now().toEpochMilliseconds() - STALE_AFTER_MS
    }

    // Leaving the screen with a selection still live would mean coming back to
    // a bulk action armed against a list that may have changed underneath it.
    DisposableEffect(Unit) { onDispose { viewModel.clearSelection() } }
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(recentlyDeleted) {
        val feed = recentlyDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.source_removed, feed.title),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        else viewModel.forgetDeleted()
    }
    LaunchedEffect(recentlyDeletedMany) {
        if (recentlyDeletedMany.isEmpty()) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = context.getString(
                R.string.sources_deleted,
                recentlyDeletedMany.size,
            ),
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteSources()
        else viewModel.forgetDeletedSources()
    }

    val opmlExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/opml")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                context.contentResolver.exportOpml(
                    uri,
                    state.tagsSourcesMap
                )
            }
        }
    }

    val opmlImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                context.contentResolver.importOpml(uri)
            }
        }
    }

    val bookmarksExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/opml")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                context.contentResolver.exportBookmarks(
                    context,
                    uri,
                    state.bookmarked
                )
            }
        }
    }

    val bookmarksImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                context.contentResolver.importBookmarks(
                    context,
                    uri,
                )
            }
        }
    }

    tagAction?.let { action ->
        TagPickerDialog(
            action = action,
            allTags = state.allTags,
            onDismiss = { tagAction = null },
            onConfirm = { tags ->
                when (action) {
                    TagAction.Add -> viewModel.editSelectedTags(add = tags)
                    TagAction.Remove -> viewModel.editSelectedTags(remove = tags)
                    TagAction.Replace -> viewModel.editSelectedTags(replaceWith = tags)
                }
                tagAction = null
            },
        )
    }

    NavigableListDetailPaneScaffold(
        navigator = paneNavigator,
        listPane = {
            AnimatedPane {
                ViewWithActionBar(
                    title = stringResource(id = R.string.title_sources),
                    largeTitle = true,
                    showBackButton = false,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {
                                navController.navigate(NavRoute.SourceAdd)
                            },
                            modifier = Modifier.padding(16.dp),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Icon(
                                imageVector = Phosphor.Plus,
                                contentDescription = stringResource(id = R.string.add_feed),
                            )
                        }
                    },
                    actions = {
                        OverflowMenu {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.Hash,
                                        contentDescription = stringResource(R.string.manage_categories),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    navController.navigate(NavRoute.Categories)
                                },
                                text = { Text(stringResource(R.string.manage_categories)) },
                            )
                            HorizontalDivider()
                            // Only the chosen row carries an arrow, and the
                            // arrow is the direction rather than a tick. Every
                            // row used to show a sort glyph whether or not it
                            // was in use, which made four identical-looking
                            // icons and told the reader nothing about which
                            // order they were actually in.
                            SourceSort.entries.forEach { option ->
                                val chosen = option == sort
                                DropdownMenuItem(
                                    leadingIcon = {
                                        if (chosen) {
                                            Icon(
                                                imageVector = if (ascending) {
                                                    Phosphor.SortAscending
                                                } else {
                                                    Phosphor.SortDescending
                                                },
                                                contentDescription = stringResource(
                                                    if (ascending) R.string.sort_ascending
                                                    else R.string.sort_descending
                                                ),
                                            )
                                        }
                                    },
                                    // Left open on the chosen row: tapping it
                                    // reverses the order, and closing the menu
                                    // would hide the arrow that just changed —
                                    // the only feedback the action has.
                                    onClick = {
                                        if (!chosen) hideMenu()
                                        viewModel.setSort(option)
                                    },
                                    text = {
                                        Text(
                                            text = stringResource(option.labelId),
                                            color = if (chosen) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                    },
                                )
                            }
                            HorizontalDivider()
                            // Mastodon is taken out of the menu rather than
                            // out of the app: the screens, the auth flow and
                            // the sync all still exist and are reachable by
                            // route. It sat here carrying the same # icon as
                            // "Manage categories", which made two unrelated
                            // things look like a pair, and it is not finished
                            // enough to earn a permanent slot next to OPML.
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.CloudArrowDown,
                                        contentDescription = stringResource(id = R.string.sources_import_opml),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    opmlImporter.launch(
                                        arrayOf(
                                            "text/plain",
                                            "text/xml",
                                            "text/opml",
                                            "*/*"
                                        )
                                    )
                                },
                                text = { Text(text = stringResource(id = R.string.sources_import_opml)) }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.CloudArrowUp,
                                        contentDescription = stringResource(id = R.string.sources_export_opml),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    opmlExporter.launch("NF-${localTime}.opml")
                                },
                                text = { Text(text = stringResource(id = R.string.sources_export_opml)) }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.Bookmarks,
                                        contentDescription = stringResource(id = R.string.sources_import_bookmarks),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    bookmarksImporter.launch(
                                        arrayOf(
                                            "text/plain",
                                            "text/xml",
                                            "text/bkm",
                                            "*/*"
                                        )
                                    )
                                },
                                text = { Text(text = stringResource(id = R.string.sources_import_bookmarks)) }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Phosphor.BookBookmark,
                                        contentDescription = stringResource(id = R.string.sources_export_bookmarks),
                                    )
                                },
                                onClick = {
                                    hideMenu()
                                    bookmarksExporter.launch("NF-${localTime}.bkm")
                                },
                                text = { Text(text = stringResource(id = R.string.sources_export_bookmarks)) }
                            )
                        }
                    }
                ) { paddingValues ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = paddingValues.calculateTopPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            if (selection.isEmpty()) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = viewModel::setQuery,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    label = { Text(stringResource(R.string.sources_search)) },
                                    trailingIcon = {
                                        if (query.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setQuery("") }) {
                                                Icon(Phosphor.SubtractSquare, null)
                                            }
                                        }
                                    },
                                )
                            } else {
                                SelectionBar(
                                    count = selection.size,
                                    onSelectAll = {
                                        viewModel.selectAll(state.allSources.map(Feed::id))
                                    },
                                    onClear = viewModel::clearSelection,
                                    onEnable = { viewModel.setSelectedEnabled(true) },
                                    onDisable = { viewModel.setSelectedEnabled(false) },
                                    onTag = { tagAction = it },
                                    onDelete = viewModel::deleteSelected,
                                )
                            }
                        }
                        item {
                            PreferenceGroupHeading(heading = stringResource(id = R.string.enabled))
                        }
                        items(state.enabledSources, key = { it.id }) { item ->
                            SourceItem(
                                modifier = Modifier.animateItem(),
                                source = item,
                                selectionMode = selection.isNotEmpty(),
                                selected = item.id in selection,
                                onLongClick = { viewModel.toggleSelected(it.id) },
                                staleSince = staleSince,
                                onClick = {
                                    scope.launch {
                                        paneNavigator.navigateTo(
                                            ListDetailPaneScaffoldRole.Detail,
                                            item.id
                                        )
                                    }
                                },
                                onSwitch = {
                                    viewModel.updateFeed(
                                        it.copy(isEnabled = false),
                                        false
                                    )
                                }
                            )
                        }
                        item {
                            PreferenceGroupHeading(heading = stringResource(id = R.string.disabled))
                        }
                        items(state.disabledSources, key = { it.id }) { item ->
                            SourceItem(
                                modifier = Modifier.animateItem(),
                                source = item,
                                selectionMode = selection.isNotEmpty(),
                                selected = item.id in selection,
                                onLongClick = { viewModel.toggleSelected(it.id) },
                                staleSince = staleSince,
                                onClick = {
                                    scope.launch {
                                        paneNavigator.navigateTo(
                                            ListDetailPaneScaffoldRole.Detail,
                                            item.id
                                        )
                                    }
                                },
                                onSwitch = {
                                    viewModel.updateFeed(
                                        it.copy(isEnabled = true),
                                        true,
                                    )
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(64.dp))
                        }
                    }
                }
            }
        },
        detailPane = {
            sourceId.longValue = paneNavigator.currentDestination
                ?.takeIf { it.pane == this.paneRole }?.contentKey
                .toString().toLongOrDefault(-1L)

            sourceId.longValue.takeIf { it != -1L }?.let { id ->
                AnimatedPane {
                    SourceEditPage(id) {
                        scope.launch {
                            paneNavigator.navigateBack()
                        }
                    }
                }
            }
        }
    )
}

/** How long a source can go without a successful sync before it is flagged. */
private const val STALE_AFTER_MS = 3L * 24 * 60 * 60 * 1000

/** Which of the three tag operations a dialog is open for. */
enum class TagAction(val labelId: Int) {
    Add(R.string.sources_add_tag),
    Remove(R.string.sources_remove_tag),
    Replace(R.string.sources_replace_tags),
}

/**
 * What replaces the search field once sources are picked out.
 *
 * It stands in the list rather than in the app bar so that the count and the
 * actions scroll with the thing they act on: a bar pinned to the top, with the
 * selection somewhere below the fold, is how people act on a selection they
 * have forgotten the contents of.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onTag: (TagAction) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.sources_selected, count),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.sources_select_all))
                }
                IconButton(onClick = onClear) {
                    Icon(Phosphor.SubtractSquare, stringResource(android.R.string.cancel))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
            ) {
                TagAction.entries.forEach { action ->
                    AssistChip(
                        onClick = { onTag(action) },
                        label = { Text(stringResource(action.labelId)) },
                    )
                }
                AssistChip(
                    onClick = onEnable,
                    label = { Text(stringResource(R.string.sources_enable)) },
                )
                AssistChip(
                    onClick = onDisable,
                    label = { Text(stringResource(R.string.sources_disable)) },
                )
                AssistChip(
                    onClick = onDelete,
                    label = { Text(stringResource(R.string.remove_title)) },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
    }
}

/**
 * Picking categories for a bulk edit.
 *
 * Existing categories are offered as chips and a new one can be typed, which is
 * the whole point: a category should be selected from what exists, so that
 * "Tech" and "tech" do not both end up in the list because two sources were
 * tagged on different days.
 */
@Composable
private fun TagPickerDialog(
    action: TagAction,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(action.labelId)) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = tag in picked,
                            onClick = {
                                picked = if (tag in picked) picked - tag else picked + tag
                            },
                            label = { Text(tag) },
                        )
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    label = { Text(stringResource(R.string.source_tags)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            val result = picked + typed.split(",").map(String::trim).filter(String::isNotEmpty)
            TextButton(
                // Replace with nothing is a legitimate action — it clears every
                // category off the selection — so only Add and Remove need a
                // non-empty result.
                enabled = result.isNotEmpty() || action == TagAction.Replace,
                onClick = { onConfirm(result) },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
