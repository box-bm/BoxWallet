package com.boxdev.boxwallet.domain.money

import java.math.BigDecimal

enum class RateSource { AUTO, MANUAL, APPROXIMATE }

data class ExchangeRate(val value: BigDecimal, val source: RateSource) {
    companion object {
        val IDENTITY = ExchangeRate(BigDecimal.ONE, RateSource.AUTO)
    }
}
