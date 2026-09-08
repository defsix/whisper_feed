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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import kotlinx.coroutines.launch

/**
 * The first screen anybody sees.
 *
 * Three panes and a button. It says what the app is for and gets out of the
 * way — the controls are taught by the tour, standing on the real screen,
 * because a picture of a button somebody has not got to yet teaches nothing.
 */
@Composable
fun OnboardingPage(onDone: () -> Unit) {
    val panes = listOf(
        OnboardingPane(
            titleId = R.string.onboarding_welcome_title,
            bodyId = R.string.onboarding_welcome_body,
            showMark = true,
        ),
        OnboardingPane(
            titleId = R.string.onboarding_sources_title,
            bodyId = R.string.onboarding_sources_body,
        ),
        OnboardingPane(
            titleId = R.string.onboarding_calm_title,
            bodyId = R.string.onboarding_calm_body,
        ),
    )
    val pagerState = rememberPagerState { panes.size }
    val scope = rememberCoroutineScope()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Getting out of onboarding must never need a forward tap. Back on the
    // first pane leaves it entirely rather than trapping the reader in a
    // three-pane carousel on their first minute with the app.
    BackHandler {
        if (pagerState.currentPage == 0) onDone()
        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Picked by what actually got painted rather than by the theme
        // setting, the same way articlePlaceholder() does: the theme has three
        // values and one of them is pure black.
        Image(
            painter = painterResource(
                if (dark) R.drawable.onboarding_bg_dark else R.drawable.onboarding_bg_light
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // The artwork is calm at the top and busy lower down, and text over the
        // busy part is unreadable at some crops. A wash under the lower half
        // costs nothing and means the copy is legible on every screen shape.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        1f to MaterialTheme.colorScheme.surface,
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp),
        ) {
            Spacer(Modifier.weight(1f))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val pane = panes[page]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (pane.showMark) {
                        Image(
                            painter = painterResource(
                                if (dark) R.drawable.lockup_horizontal_notag_dark
                                else R.drawable.lockup_horizontal_notag_light
                            ),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(72.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                    Text(
                        text = stringResource(pane.titleId),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(pane.bodyId),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                repeat(panes.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val last = pagerState.currentPage == panes.lastIndex
            Button(
                onClick = {
                    if (last) onDone()
                    else scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (last) R.string.onboarding_get_started else R.string.tour_next
                    )
                )
            }

            // Reachable from every pane, not only the last. Somebody who
            // already knows what an RSS reader is should not have to swipe
            // through three panes to be let in.
            TextButton(
                onClick = onDone,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.tour_skip))
            }
        }
    }
}

private data class OnboardingPane(
    val titleId: Int,
    val bodyId: Int,
    val showMark: Boolean = false,
)
