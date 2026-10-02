package com.revyu.app.di

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap the app in CompositionLocalProvider(LocalAppContainer provides ...)")
}

/**
 * Builds a ViewModelProvider.Factory from a plain lambda constructor. Lets every screen
 * write `viewModel(factory = viewModelFactory { MyViewModel(container.someRepo) })`
 * instead of needing a generated Hilt factory per screen.
 */
@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> viewModelFactory(build: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = build() as VM
    }
