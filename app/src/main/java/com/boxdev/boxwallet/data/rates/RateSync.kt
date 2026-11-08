package com.boxdev.boxwallet.data.rates

import javax.inject.Inject

/**
 * Downloads the latest rates and then upgrades movements saved with an approximate rate, so the
 * daily worker and "Update now" leave the data in the same state.
 */
class RateSync @Inject constructor(
    private val repository: ExchangeRateRepository,
    private val fixer: ApproximateRateFixer,
) {
    /** False when the download failed; approximate rates are only revisited after a successful one. */
    suspend fun syncAndFix(): Boolean {
        if (!repository.syncLatest()) return false
        fixer.fix()
        return true
    }
}
