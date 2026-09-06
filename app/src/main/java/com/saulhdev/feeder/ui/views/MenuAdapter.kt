/*
 * This file is part of Neo Feed
 * Copyright (c) 2024   Neo Applications Team
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

package com.saulhdev.feeder.ui.views

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.entity.ActionStyle
import com.saulhdev.feeder.data.entity.MenuItem

class MenuAdapter(context: Context, private val actionStyle: ActionStyle) :
    RecyclerView.Adapter<MenuAdapter.Holder>() {
    private val inflater = LayoutInflater.from(context)
    private var adapterItems: List<MenuItem> = listOf()
    private var clickListener: ((MenuItem) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(inflater.inflate(R.layout.menu_item, parent, false), actionStyle)
    }

    override fun getItemCount(): Int {
        return this.adapterItems.size
    }

    fun getAdapterItem(i: Int): MenuItem {
        return this.adapterItems[i]
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(this.adapterItems[position])
        holder.setOnClickListener(clickListener!!)
    }

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        holder.setOnClickListener(null)
    }

    fun setOnClickListener(clickListener: (MenuItem) -> Unit) {
        this.clickListener = clickListener
    }

    fun setAdapterItems(list: List<MenuItem>) {
        this.adapterItems = list
        notifyDataSetChanged()
    }

    inner class Holder(itemView: View, private val g: ActionStyle) :
        RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = this.itemView.findViewById(R.id.icon)
        private val label: TextView = this.itemView.findViewById(R.id.label)
        private var menuItem: MenuItem? = null
        private var touchListener: ((MenuItem) -> Unit)? = null

        init {
            this.itemView.setPaddingRelative(this.g.paddingStart, 0, this.g.paddingEnd, 0)
            this.itemView.setOnClickListener {
                if (menuItem != null && touchListener != null) {
                    touchListener?.invoke(menuItem!!)
                }
            }
            if (this.g.iconTint != null) {
                this.iconView.colorFilter =
                    PorterDuffColorFilter(this.g.iconTint!!, PorterDuff.Mode.SRC_IN)
            }
            this.label.setTextSize(0, this.g.textSize.toFloat())
            this.label.setTextColor(this.g.textColor)
            this.label.layoutParams =
                (this.label.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    marginStart = g.iconSpace
                }
        }

        fun setOnClickListener(touchListener: ((MenuItem) -> Unit)?) {
            this.touchListener = touchListener
        }

        fun bind(menuItem: MenuItem) {
            this.menuItem = menuItem
            this.iconView.setImageResource(menuItem.icon)
            this.label.setText(menuItem.title)
        }
    }
}