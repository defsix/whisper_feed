package com.saulhdev.feeder.utils.extensions

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.component.getScopeId

open class NeoViewModel : ViewModel() {
    init {
        Log.d(this::class.toString(), "neoviewmodel@koinscope: ${getScopeId()}")
    }
}

/**
 * A view model scoped to whatever owns this composition.
 *
 * The activity first, so that screens sharing a view model across navigation
 * get the same instance rather than one per back-stack entry.
 *
 * Falling back matters: the launcher overlay is a window on the launcher's
 * token and has no activity at all, so `LocalActivity.current` is null there
 * and the cast this used to do unconditionally threw. OverlayComposeHost
 * supplies its own ViewModelStoreOwner for exactly that case.
 */
@Composable
inline fun <reified T : ViewModel> koinNeoViewModel(): T {
    val owner = LocalActivity.current as? ComponentActivity
        ?: checkNotNull(LocalViewModelStoreOwner.current) {
            "No activity and no ViewModelStoreOwner in this composition"
        }
    return koinViewModel(viewModelStoreOwner = owner)
}