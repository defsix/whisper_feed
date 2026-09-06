package com.saulhdev.feeder.ui.feed.binders

import android.util.SparseIntArray
import android.view.View
import com.saulhdev.feeder.data.db.models.FeedItem

interface FeedBinder {
    fun bind(theme: SparseIntArray?, item: FeedItem, view: View)
}