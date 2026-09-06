/*
 * This file is part of Neo Feed
 * Copyright (c) 2023   Neo Feed Team
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

package com.saulhdev.feeder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.HeartStraight
import com.saulhdev.feeder.ui.icons.phosphor.HeartStraightFill
import com.saulhdev.feeder.ui.icons.phosphor.ShareNetwork
import kotlinx.coroutines.launch

@Composable
fun FavoriteButton(bookmarked: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val favoriteIcon by remember(bookmarked) {
        mutableStateOf(
            if (bookmarked) Phosphor.HeartStraightFill
            else Phosphor.HeartStraight
        )
    }
    val favoriteColor by animateColorAsState(
        targetValue = if (bookmarked) Color.Red else MaterialTheme.colorScheme.onSurface,
        label = "favoriteColor",
    )

    IconButton(
        modifier = Modifier
            .size(size = 36.dp)
            .clip(CircleShape),
        onClick = {
            coroutineScope.launch {
                scale.animateTo(
                    0.8f,
                    animationSpec = tween(100),
                )
                scale.animateTo(
                    1f,
                    animationSpec = tween(100),
                )
                onClick()
            }
        }
    ) {
        Icon(
            imageVector = favoriteIcon,
            contentDescription = " ",
            tint = favoriteColor,
            modifier = Modifier
                .scale(scale = scale.value)
                .size(size = 28.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    coroutineScope.launch {
                        scale.animateTo(
                            0.8f,
                            animationSpec = tween(100),
                        )
                        scale.animateTo(
                            1f,
                            animationSpec = tween(100),
                        )
                        onClick()
                    }
                }
        )
    }
}

@Composable
fun ShareButton(onClick: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember {
        Animatable(1f)
    }
    IconButton(
        modifier = Modifier
            .size(size = 36.dp)
            .clip(CircleShape),
        onClick = {
            coroutineScope.launch {
                scale.animateTo(
                    0.8f,
                    animationSpec = tween(100),
                )
                scale.animateTo(
                    1f,
                    animationSpec = tween(100),
                )
                onClick()
            }
        }
    ) {
        Icon(
            imageVector = Phosphor.ShareNetwork,
            contentDescription = "Share feed",
            modifier = Modifier.size(size = 28.dp)
        )
    }
}


@Preview
@Composable
fun FavoriteButtonPreview() {
    FavoriteButton(bookmarked = false, onClick = {})
}

@Preview
@Composable
fun ShareButtonPreview() {
    ShareButton(onClick = {})
}
