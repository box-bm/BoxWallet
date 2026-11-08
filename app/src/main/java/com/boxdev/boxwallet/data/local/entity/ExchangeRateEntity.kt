package com.boxdev.boxwallet.data.local.entity

import androidx.room.Entity
import java.math.BigDecimal

/** Units of [currencyCode] per 1 USD, as published on [date]. */
@Entity(tableName = "exchange_rates", primaryKeys = ["date", "currencyCode"])
data class ExchangeRateEntity(
    /** Epoch day (UTC) of the publication. */
    val date: Long,
    /** Upper-case ISO 4217 code. */
    val currencyCode: String,
    val unitsPerUsd: BigDecimal,
)
