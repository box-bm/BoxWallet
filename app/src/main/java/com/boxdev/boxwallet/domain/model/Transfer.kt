package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.Money
import java.math.BigDecimal
import java.time.LocalDateTime

/** A transfer about to be saved: [sent] leaves [fromAccountId] (in its currency) and lands in [toAccountId]. */
data class TransferDraft(
    val fromAccountId: Long,
    val toAccountId: Long,
    val sent: Money,
    val date: LocalDateTime,
    val note: String = "",
    /** Sent → received rate typed through the received amount; ignored when both currencies match. */
    val manualRate: BigDecimal? = null,
)

/** A saved transfer, read from its TRANSFER_OUT ([outLegId]) and TRANSFER_IN ([inLegId]) rows. */
data class Transfer(
    val id: String,
    val fromAccountId: Long,
    val toAccountId: Long,
    val sent: Money,
    val received: Money,
    /** Sent → received rate of the TRANSFER_IN row. */
    val rate: ExchangeRate,
    val date: LocalDateTime,
    val note: String,
    val outLegId: Long,
    val inLegId: Long,
)

/** The two accounts of a transfer, as the lists need them (Room POJO). */
data class TransferPair(val transferId: String, val fromAccountId: Long, val toAccountId: Long)
