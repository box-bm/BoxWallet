package com.boxdev.boxwallet.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets another screen open the Movements tab already filtered: Reports to one category and date
 * range, an account's detail to that account. The sender calls [request] or [requestAccount] and
 * switches tabs; the Movements view model applies the filter and calls [consume], whether its
 * screen was just created or restored.
 */
@Singleton
class MovementsFilterRequest @Inject constructor() {
    data class Filter(
        val categoryId: Long? = null,
        val start: LocalDate? = null,
        val end: LocalDate? = null,
        val accountId: Long? = null,
    )

    private val pending = MutableStateFlow<Filter?>(null)
    val filter: StateFlow<Filter?> = pending.asStateFlow()

    fun request(categoryId: Long, start: LocalDate, end: LocalDate) {
        pending.value = Filter(categoryId = categoryId, start = start, end = end)
    }

    fun requestAccount(accountId: Long) {
        pending.value = Filter(accountId = accountId)
    }

    fun consume() {
        pending.value = null
    }
}
