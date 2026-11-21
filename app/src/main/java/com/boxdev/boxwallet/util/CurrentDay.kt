package com.boxdev.boxwallet.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Today's local date as a flow: [days] emits on collection and again when the day changes, so screens kept
 * alive across midnight move their "today" cutoffs along. Tests pass their own [days].
 */
class CurrentDay(val days: Flow<LocalDate>) {
    /** The wall clock; its timer runs on [Dispatchers.Default], never on a caller's (test) dispatcher. */
    @Inject constructor() : this(dayTicks(LocalDateTime::now).flowOn(Dispatchers.Default))
}

/** A timer can fire late (device sleep) or the zone can change, so the day is re-read at least this often. */
private const val MAX_WAIT_MS = 60_000L

/** The date from [now], emitted now and at each following local midnight (re-checked every minute at most). */
fun dayTicks(now: () -> LocalDateTime): Flow<LocalDate> = flow {
    while (true) {
        val current = now()
        emit(current.toLocalDate())
        val untilMidnight = Duration.between(current, current.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
        delay(untilMidnight.coerceIn(1L, MAX_WAIT_MS))
    }
}.distinctUntilChanged()
