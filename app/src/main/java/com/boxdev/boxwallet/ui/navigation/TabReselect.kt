@file:OptIn(ExperimentalMaterial3Api::class)

package com.boxdev.boxwallet.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter

/**
 * Emits a tab's route when the user taps the tab that is already selected. Tab screens listen with
 * [TabReselectEffect] and scroll back to the top, the standard Material reselect behavior.
 */
val LocalTabReselect = staticCompositionLocalOf<SharedFlow<String>> { MutableSharedFlow() }

/** Runs [onReselect] each time the tab for [route] is tapped while already showing. */
@Composable
fun TabReselectEffect(route: String, onReselect: suspend () -> Unit) {
    val events = LocalTabReselect.current
    val action = rememberUpdatedState(onReselect)
    LaunchedEffect(events, route) {
        events.filter { it == route }.collect { action.value() }
    }
}

/** Expands a collapsed flexible top app bar again, used together with scroll-to-top. */
fun TopAppBarScrollBehavior.expand() {
    state.heightOffset = 0f
    state.contentOffset = 0f
}
