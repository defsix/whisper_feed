/*
 * This file is part of Neo Feed
 * Copyright (c) 2024   Neo Feed Team
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

package com.saulhdev.feeder.ui.feed

import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.feed.binders.StoryCardBinder

class FeedAdapter : RecyclerView.Adapter<FeedAdapter.FeedViewHolder>() {
    private var list = listOf<FeedItem>()
    private lateinit var layoutInflater: LayoutInflater
    private var theme: SparseIntArray? = null

    fun replace(new: List<FeedItem>) {
        if (new != list) {
            list = new
            notifyDataSetChanged()
        }
    }

    fun setTheme(theme: SparseIntArray) {
        this.theme = theme
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        if (!::layoutInflater.isInitialized) layoutInflater = LayoutInflater.from(parent.context)

        val layoutResource = R.layout.feed_card_story_large

        return FeedViewHolder(viewType, layoutInflater.inflate(layoutResource, parent, false))
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val item = list[position]
        StoryCardBinder.bind(theme, item, holder.itemView)
    }

    inner class FeedViewHolder(val type: Int, itemView: View) : RecyclerView.ViewHolder(itemView)
}