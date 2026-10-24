package me.rerere.momoly.di

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer is not available in this composition")
}
