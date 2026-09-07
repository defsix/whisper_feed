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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.components.SaveButton
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ShareNetwork
import com.saulhdev.feeder.utils.formatArticleAge

/**
 * One article in the feed, drawn in whichever shape the rhythm asks for.
 *
 * Both surfaces — the launcher overlay and the app — call this, so the feed
 * looks the same in each and a change to the rhythm lands in both at once.
 * See [feedCardShape] for how the shape is chosen.
 */
@Composable
fun FeedArticleItem(
    item: FeedItem,
    index: Int,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreLikeThis: () -> Unit = {},
    onLessLikeThis: () -> Unit = {},
    onHideSource: () -> Unit = {},
) {
    val hasImage = !item.article.imageUrl.isNullOrBlank()
    val menu: @Composable (Color?) -> Unit = { tint ->
        ArticleOverflowMenu(
            onMoreLikeThis = onMoreLikeThis,
            onLessLikeThis = onLessLikeThis,
            onHideSource = onHideSource,
            onShare = onShare,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    when (feedCardShape(index, hasImage)) {
        FeedCardShape.Hero    -> ArticleHeroCard(item, onClick, onBookmark, onShare, menu, modifier)
        FeedCardShape.Card    -> ArticleCard(item, onClick, onBookmark, onShare, menu, modifier)
        FeedCardShape.Compact -> ArticleCompactRow(item, onClick, onBookmark, menu, modifier)
    }
}

/**
 * The anchor shape: a tall image with the headline laid over it.
 *
 * The scrim is a gradient rather than a flat overlay because a flat one has to
 * be dark enough for the worst image, which then greys out every good one.
 */
@Composable
fun ArticleHeroCard(
    item: FeedItem,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    onShare: () -> Unit,
    menu: @Composable (Color?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        ) {
            AsyncImage(
                model = item.article.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f),
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                val category = item.feedTag
                if (category.isNotBlank()) {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = item.contentTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArticleMeta(
                        source = item.feedTitle,
                        age = item.relativeAge(context),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                        iconUrl = item.feedIconUrl,
                        onImage = true,
                    )
                    SaveButton(
                        saved = item.bookmarked,
                        onSavedChange = onBookmark,
                        onImage = true,
                    )
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Phosphor.ShareNetwork,
                            contentDescription = stringResource(R.string.share),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    menu(Color.White)
                }
            }
        }
    }
}

/**
 * The middle weight: image above, headline and summary below. This is the shape
 * the Cards layout was built around and it stays the relaxed-browsing view.
 */
@Composable
fun ArticleCard(
    item: FeedItem,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    onShare: () -> Unit,
    menu: @Composable (Color?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val image = item.article.imageUrl
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(MaterialTheme.shapes.medium),
                )
            }

            Text(
                text = item.contentTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = if (image.isNullOrBlank()) 0.dp else 12.dp),
            )

            val summary = item.article.description
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                ArticleMeta(
                    source = item.feedTitle,
                    age = item.relativeAge(context),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    iconUrl = item.feedIconUrl,
                )

                SaveButton(saved = item.bookmarked, onSavedChange = onBookmark)
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Phosphor.ShareNetwork,
                        contentDescription = stringResource(R.string.share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                menu(null)
            }
        }
        ArticleDivider()
    }
}

/**
 * The dense shape: headline left, thumbnail right. Most of the feed is this,
 * which is what makes the occasional hero read as an accent rather than noise.
 */
@Composable
fun ArticleCompactRow(
    item: FeedItem,
    onClick: () -> Unit,
    onBookmark: (Boolean) -> Unit,
    menu: @Composable (Color?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val category = item.feedTag
                if (category.isNotBlank()) {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = item.contentTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                ArticleMeta(
                    source = item.feedTitle,
                    age = item.relativeAge(context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconUrl = item.feedIconUrl,
                )
            }

            val image = item.article.imageUrl
            if (!image.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            } else {
                Spacer(Modifier.width(4.dp))
                SaveButton(
                    saved = item.bookmarked,
                    onSavedChange = onBookmark,
                    size = 20.dp,
                )
            }
            // Compact is most of the feed, so leaving the actions off it left
            // "hide source" and the rest reachable on barely a quarter of the
            // articles. Top-aligned rather than centred, so it does not drift
            // down beside a three-line headline.
            Box(modifier = Modifier.align(Alignment.Top)) { menu(null) }
        }
        ArticleDivider()
    }
}

/**
 * "SFGATE · 10h" — the line under every headline.
 *
 * Two texts rather than one string, because a single one puts the ellipsis at
 * the end and the age is what gets eaten: some feeds title themselves with a
 * whole sentence ("io9 - We come from the future."), which overruns a compact
 * row's 231dp on its own. The age is given its intrinsic width first, so only
 * the source name shortens and the age is always readable.
 */
@Composable
private fun ArticleMeta(
    source: String,
    age: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    onImage: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SourceMark(iconUrl = iconUrl, sourceName = source, onImage = onImage)
        Spacer(Modifier.width(6.dp))
        Text(
            text = source,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = " · $age",
            style = style,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * The hairline between articles.
 *
 * Replaces the rounded container each article used to sit in. A column of
 * filled boxes puts a border around every headline and reads as a stack of
 * separate objects; a divider says "next item" with one line and lets the
 * content own the width. Inset from the left so it starts under the text
 * rather than cutting across the whole page.
 */
@Composable
private fun ArticleDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * The article's age, in the compact form the feed uses: "5h", "3d", "2w".
 *
 * The field is primarySortTime rather than pubDate, because the list is ordered
 * by primarySortTime and showing the other meant the date on a card could
 * disagree with the position it appeared in.
 */
private fun FeedItem.relativeAge(context: android.content.Context): String =
    formatArticleAge(context, article.primarySortTime.toEpochMilliseconds())
