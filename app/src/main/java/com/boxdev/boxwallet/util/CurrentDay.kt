package com.boxdev.boxwallet.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Today's local date as a flow: [days] emits on collection and again when the day changes, so screens kept
 * alive across midnight move their "today" cutoffs along. Tests pass their own [days].
 */
@Singleton
class CurrentDay(val days: Flow<LocalDate>) {
    /**
     * The wall clock, shared: one timer serves every collector while any is subscribed. It runs on
     * [Dispatchers.Default], never on a caller's (test) dispatcher.
     */
    @Inject constructor() : this(
        sharedDays(dayTicks(LocalDateTime::now), CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    )
}

/** A timer can fire late (device sleep) or the zone can change, so the day is re-read at least this often. */
private const val MAX_WAIT_MS = 60_000L

/** Collectors that leave and come back within this window keep the same timer. */
private const val STOP_TIMEOUT_MS = 5_000L

/** The date from [now], emitted now and at each following local midnight (re-checked every minute at most). */
fun dayTicks(now: () -> LocalDateTime): Flow<LocalDate> = flow {
    while (true) {
        val current = now()
        emit(current.toLocalDate())
        val untilMidnight = Duration.between(current, current.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
        delay(untilMidnight.coerceIn(1L, MAX_WAIT_MS))
    }
}.distinctUntilChanged()

/**
 * [ticks] run once in [scope] for all collectors, started by the first and stopped [STOP_TIMEOUT_MS] after
 * the last leaves. The replayed day expires on stop, so a later collector never starts on a stale date.
 */
fun sharedDays(ticks: Flow<LocalDate>, scope: CoroutineScope): Flow<LocalDate> = ticks.shareIn(
    scope,
    SharingStarted.WhileSubscribed(stopTimeoutMillis = STOP_TIMEOUT_MS, replayExpirationMillis = 0),
    replay = 1,
)
