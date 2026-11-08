package com.boxdev.boxwallet.data.rates

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes the jobs that rewrite stored conversions (main currency change, approximate-rate
 * fixes), so one never writes rows computed against a main currency the other just replaced.
 */
@Singleton
class CurrencyChangeLock @Inject constructor() {
    val mutex = Mutex()
}
