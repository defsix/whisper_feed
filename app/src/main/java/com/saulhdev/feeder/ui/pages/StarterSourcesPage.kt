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
package com.saulhdev.feeder.ui.pages

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.StarterSources
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.starterSources
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * The same short list, reachable afterwards.
 *
 * Somebody who skipped it during onboarding, or who took most of it off and
 * later wanted one back, has nowhere else to find these — they are not
 * searchable by name in a way that helps if you do not already know the
 * address. One screen, and it disappears once everything on it is subscribed.
 */
@Composable
fun StarterSourcesPage(
    sourcesRepo: SourcesRepository = koinInject(),
    syncClient: SyncRestClient = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Anything already subscribed is dropped from the list rather than shown
    // ticked and inert. A row that cannot do anything is worse than no row.
    val existing by sourcesRepo.getAllSourcesFlow().collectAsState(initial = emptyList())
    val available = remember(existing) {
        val urls = existing.map { it.url.toString().trimEnd('/') }.toSet()
        StarterSources.ALL.filterNot { it.url.trimEnd('/') in urls }
    }

    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val chosen = available.filter { selected[it.url] == true }

    ViewWithActionBar(
        title = stringResource(R.string.pref_starter_sources),
        largeTitle = true,
        showBackButton = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = stringResource(
                        if (available.isEmpty()) R.string.starter_all_added
                        else R.string.starter_body
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            starterSources(
                sources = available,
                selected = selected.filterValues { it }.keys,
                onToggle = { source -> selected[source.url] = selected[source.url] != true },
            )

            if (chosen.isNotEmpty()) {
                item {
                    ActionButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        text = if (chosen.size == 1) stringResource(R.string.starter_add_one)
                        else stringResource(R.string.starter_add, chosen.size),
                        icon = Phosphor.Plus,
                        onClick = {
                            val picked = chosen
                            scope.launch(Dispatchers.IO) {
                                val added = StarterSources.subscribe(
                                    sourcesRepo, picked
                                ) { context.getString(it) }
                                withContext(Dispatchers.Main) {
                                    selected.clear()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.starter_added, added),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                syncClient.syncAllFeeds()
                            }
                        },
                    )
                }
            }
        }
    }
}
