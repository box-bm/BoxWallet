package com.boxdev.boxwallet.ui.screens.transaction

import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.parseMajor
import java.math.MathContext
import java.util.Currency

/**
 * The two-way link between a typed rate and a typed equivalent, shared by the movement and
 * transfer forms: [original] is the typed amount and [target] the currency it lands in.
 */
internal object ManualConversion {

    /** [original] converted to [target] at the typed [rate]; null when either isn't usable. */
    fun equivalentAt(rate: String, original: Money?, target: Currency?): String? {
        val value = rate.trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        if (original == null || target == null) return null
        return original.convert(target, value).toMajor().toPlainString()
    }

    /** The rate = [equivalent] / [original] (DECIMAL64); null when either isn't a positive amount. */
    fun rateFor(equivalent: String, original: Money?, target: Currency): String? {
        val converted = parseMajor(equivalent, target) ?: return null
        if (original == null) return null
        return converted.toMajor().divide(original.toMajor(), MathContext.DECIMAL64).toPlainString()
    }
}
