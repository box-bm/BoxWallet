package com.boxdev.boxwallet.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets another screen (Reports) open the Movements tab already filtered to one category. The
 * sender calls [request] and switches tabs; the Movements view model applies the category and
 * calls [consume], whether its screen was just created or restored.
 */
@Singleton
class MovementsFilterRequest @Inject constructor() {
    private val pending = MutableStateFlow<Long?>(null)
    val categoryId: StateFlow<Long?> = pending.asStateFlow()

    fun request(categoryId: Long) {
        pending.value = categoryId
    }

    fun consume() {
        pending.value = null
    }
}
