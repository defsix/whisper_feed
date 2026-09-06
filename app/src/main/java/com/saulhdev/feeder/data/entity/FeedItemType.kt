package com.saulhdev.feeder.data.entity

// Feed types which are available in HomeFeeder listing
enum class FeedItemType {
    // Text card (like the old notification card). Contains a title, subtitle and a description.
    // Added in API 1.
    TEXT_CARD,

    // TEXT_CARD, but with larger header and actions to click.
    // Added in API 1.
    TEXT_CARD_ACTIONS,

    // Story card - a large card adapted for news.
    // Added in API 2.
    STORY_CARD
}