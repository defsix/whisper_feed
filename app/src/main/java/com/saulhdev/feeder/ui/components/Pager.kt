package com.saulhdev.feeder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.navigation.NavItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

@Composable
fun NonSlidePager(
    modifier: Modifier = Modifier,
    pageItems: ImmutableList<NavItem>,
    pagerState: PagerState,
) {
    HorizontalPager(modifier = modifier, state = pagerState, userScrollEnabled = false) { page ->
        pageItems[page].content()
    }
}

@Composable
fun SlidePager(
    modifier: Modifier = Modifier,
    pageItems: ImmutableList<NavItem>,
    pagerState: PagerState,
) {
    HorizontalPager(modifier = modifier, state = pagerState, beyondViewportPageCount = 0) { page ->
        pageItems[page].content()
    }
}

@Composable
fun PagerNavBar(pageItems: List<NavItem>, pagerState: PagerState) {
    val scope = rememberCoroutineScope()

    NavigationBar(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        windowInsets = NavigationBarDefaults.windowInsets
            .add(WindowInsets(left = 8.dp, right = 8.dp))
    ) {
        pageItems.forEachIndexed { index, item ->
            val selected by derivedStateOf { pagerState.currentPage == index }

            NavBarItem(
                modifier = Modifier.weight(if (selected) 2f else 1f),
                icon = item.icon,
                labelId = item.title,
                selected = selected,
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            )
        }
    }
}

@Composable
fun RowScope.NavBarItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    labelId: Int,
    selected: Boolean,
    onClick: () -> Unit = {},
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainer
        else Color.Transparent, label = "backgroundColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onBackground,
        label = "iconColor",
    )

    Row(
        modifier = modifier
            .padding(vertical = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable { onClick() }
            .background(
                background,
                MaterialTheme.shapes.extraLarge
            )
            .padding(8.dp)
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(id = labelId),
            modifier = Modifier.size(24.dp),
            tint = iconColor,
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
        ) {
            Text(
                text = stringResource(id = labelId),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = iconColor,
            )
        }
    }
}