package com.saulhdev.feeder.ui.components

import android.content.Intent
import android.text.Html
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.utils.RelativeTimeHelper
import kotlinx.coroutines.launch

@Composable
fun ArticleItem(
    article: FeedItem,
    onBookmark: suspend (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val content = article.toStoryCardContent()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        onClick = {
            onClick()
        }
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp),
        ) {
            if (content.backgroundUrl.isNotEmpty()
                && !content.backgroundUrl.contains(".rss")
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(content.backgroundUrl)
                        .crossfade(true)
                        .crossfade(500)
                        .build(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                    contentDescription = ""
                )
            }

            Text(
                text = content.title,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 5,
            )

            if (content.text.isNotEmpty()) {
                Text(
                    text = Html.fromHtml(content.text, 0).toString(),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(2f)
                ) {
                    Text(
                        text = content.source.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = RelativeTimeHelper.getDateFormattedRelative(
                            LocalContext.current,
                            (article.timeMillis / 1000) - 1000
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    var bookmarked by remember(article.bookmarked) {
                        mutableStateOf(article.bookmarked)
                    }

                    FavoriteButton(bookmarked = bookmarked) {
                        scope.launch {
                            onBookmark(!bookmarked)
                            bookmarked = !bookmarked
                        }
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    ShareButton {
                        val intent = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, content.link)
                                putExtra(Intent.EXTRA_TITLE, content.title)
                                type = "text/plain"
                            },
                            null,
                        )
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}
