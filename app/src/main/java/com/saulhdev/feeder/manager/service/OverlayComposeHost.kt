/*
 * This file is part of 076 Feed
 * Copyright (c) 2026   076 Feed contributors
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
package com.saulhdev.feeder.manager.service

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Makes it possible to host Compose inside the launcher overlay.
 *
 * The overlay is not an Activity: its window is attached to the *launcher's*
 * window token (see UPSTREAM_NOTES.md 3b), and the upstream `OverlayController`
 * it descends from is a plain `ContextThemeWrapper`. `ComposeView` refuses to
 * compose unless it can find a lifecycle owner, a saved-state registry owner and
 * a view-model store owner on its view tree, so this supplies all three and
 * pins them to the overlay's own lifetime.
 *
 * Upstream's controller only exposes `onCreate`, `onResume` and `onDestroy` as
 * overridable — `onPause` and `onStop` are private — so the lifecycle this
 * drives is deliberately coarse: it goes to RESUMED once the overlay is created
 * and stays there until the overlay is destroyed. That is sufficient for
 * Compose, which needs a registry that is restored before attach and a
 * lifecycle that reaches at least STARTED to run recomposition. The cost is
 * that composables cannot observe the panel being swiped away; anything needing
 * that should use the overlay's own callbacks instead.
 */
class OverlayComposeHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /**
     * Restores the saved-state registry and brings the lifecycle up to RESUMED.
     * Must run before any [ComposeView] in the tree is attached, because the
     * registry has to be restored while the lifecycle is still below STARTED.
     */
    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    /**
     * Publishes this host on each of [views] so a `ComposeView` in the tree can
     * find the owners it needs. Call after [onCreate], before anything attaches
     * to a window.
     *
     * Which view this goes on is not a free choice, and getting it wrong throws
     * "ViewTreeLifecycleOwner not found" at attach time. Compose does not search
     * upward from the `ComposeView` itself: `View.windowRecomposer` first walks
     * up to the window's *content child* — the view whose parent carries
     * `android.R.id.content` — and only then calls `findViewTreeLifecycleOwner()`
     * starting at that node. Owners set anywhere below it are invisible to that
     * lookup. Here the content child is the overlay's `slidingPanelLayout`, so
     * that is the node that must carry them.
     */
    fun attachTo(vararg views: View?) {
        views.filterNotNull().forEach { view ->
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }
    }
}
