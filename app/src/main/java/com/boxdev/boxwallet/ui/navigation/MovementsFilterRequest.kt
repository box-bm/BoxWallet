package com.boxdev.boxwallet.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets another screen (Reports) open the Movements tab already filtered to one category and date range. The
 * sender calls [request] and switches tabs; the Movements view model applies the category and
 * calls [consume], whether its screen was just created or restored.
 */
@Singleton
class MovementsFilterRequest @Inject constructor() {
    data class Filter(val categoryId: Long, val start: LocalDate, val end: LocalDate)

    private val pending = MutableStateFlow<Filter?>(null)
    val filter: StateFlow<Filter?> = pending.asStateFlow()

    fun request(categoryId: Long, start: LocalDate, end: LocalDate) {
        pending.value = Filter(categoryId, start, end)
    }

    fun consume() {
        pending.value = null
    }
}
