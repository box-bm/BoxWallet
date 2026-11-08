package com.boxdev.boxwallet.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/** Number of minor-unit digits for this currency; currencies without a fixed value (-1) use 2. */
fun Currency.minorDigits(): Int = defaultFractionDigits.let { if (it < 0) 2 else it }

/** An amount in minor units (e.g. cents) of [currency]. Never backed by floating point. */
data class Money(val minor: Long, val currency: Currency) {

    fun toMajor(): BigDecimal = BigDecimal.valueOf(minor, currency.minorDigits())

    /** Lossy; only for charts. */
    fun toMajorDouble(): Double = toMajor().toDouble()

    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add ${other.currency.currencyCode} to ${currency.currencyCode}"
        }
        return Money(minor + other.minor, currency)
    }

    fun convert(to: Currency, rate: BigDecimal): Money =
        of(toMajor().multiply(rate), to)

    companion object {
        fun of(major: BigDecimal, currency: Currency): Money =
            Money(
                major.setScale(currency.minorDigits(), RoundingMode.HALF_EVEN).unscaledValue().toLong(),
                currency,
            )
    }
}

private val MAJOR_PATTERN = Regex("""\d+(\.\d+)?""")

/** Parses user input ("," thousands, "." decimal). Null if invalid, <= 0 or more decimals than the currency allows. */
fun parseMajor(input: String, currency: Currency): Money? {
    val cleaned = input.trim().replace(",", "")
    if (!MAJOR_PATTERN.matches(cleaned)) return null
    val value = BigDecimal(cleaned)
    if (value.signum() <= 0 || value.stripTrailingZeros().scale() > currency.minorDigits()) return null
    return try {
        Money(value.setScale(currency.minorDigits()).unscaledValue().longValueExact(), currency)
    } catch (_: ArithmeticException) {
        null
    }
}
