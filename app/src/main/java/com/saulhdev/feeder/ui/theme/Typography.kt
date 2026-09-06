package com.saulhdev.feeder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.saulhdev.feeder.R

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun LinkTextStyle(): TextStyle =
    TextStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )

@Composable
fun FeedListItemTitleStyle(): SpanStyle =
    FeedListItemTitleTextStyle().toSpanStyle()

@Composable
fun FeedListItemTitleTextStyle(): TextStyle =
    MaterialTheme.typography.titleMedium

@Composable
fun FeedListItemStyle(): TextStyle =
    MaterialTheme.typography.bodyLarge

@Composable
fun FeedListItemFeedTitleStyle(): TextStyle =
    FeedListItemDateStyle()

@Composable
fun FeedListItemDateStyle(): TextStyle =
    MaterialTheme.typography.labelMedium

@Composable
fun TTSPlayerStyle(): TextStyle =
    MaterialTheme.typography.titleMedium

@Composable
fun CodeInlineStyle(): SpanStyle =
    SpanStyle(
        background = CodeBlockBackground(),
        fontFamily = FontFamily.Monospace
    )

/**
 * Has no background because it is meant to be put over a Surface which has the proper background.
 */
@Composable
fun CodeBlockStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.merge(
        SpanStyle(
            fontFamily = FontFamily.Monospace
        )
    )

@Composable
fun CodeBlockBackground(): Color =
    MaterialTheme.colorScheme.surfaceVariant

@Composable
fun BlockQuoteStyle(): SpanStyle =
    MaterialTheme.typography.bodyLarge.toSpanStyle().merge(
        SpanStyle(
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
        )
    )

val kingthingsPrintingkit = FontFamily(
    Font(R.font.kingthings_printingkit)
)
