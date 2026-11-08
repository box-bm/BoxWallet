package com.boxdev.boxwallet.data.rates

import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

data class RateSnapshot(val date: LocalDate, val unitsPerUsd: Map<String, BigDecimal>)

class ExchangeRateApi(
    private val transport: HttpTransport,
    private val onInvalid: (String) -> Unit = {},
) {

    /** Fetches USD-based rates for [date] (null = latest). Null if unavailable. */
    suspend fun fetch(date: LocalDate? = null): RateSnapshot? = withContext(Dispatchers.IO) {
        if (date != null && date.isBefore(HISTORY_START)) return@withContext null
        val tag = date?.toString() ?: "latest"
        val urls = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$tag/v1/currencies/usd.json",
            "https://$tag.currency-api.pages.dev/v1/currencies/usd.json",
        )
        var reason: String? = null
        for (url in urls) {
            val body = (transport.get(url) as? HttpResult.Ok)?.body ?: continue
            when (val r = parse(body)) {
                is Parsed.Valid -> return@withContext r.snapshot
                is Parsed.Invalid -> reason = reason ?: r.reason
            }
        }
        reason?.let(onInvalid)
        null
    }

    private sealed interface Parsed {
        class Valid(val snapshot: RateSnapshot) : Parsed
        class Invalid(val reason: String) : Parsed
    }

    private fun parse(body: String): Parsed = try {
        val root = JsonParser.parseString(body).asJsonObject
        val date = LocalDate.parse(root.get("date").asString)
        val rates = root.getAsJsonObject("usd").entrySet().mapNotNull { (key, value) ->
            val code = key.uppercase(Locale.ROOT)
            val rate = runCatching { value.asJsonPrimitive.asBigDecimal }.getOrNull()
            if (rate != null && rate.signum() > 0 && CurrencyCatalog.isSupported(code)) code to rate else null
        }.toMap()
        Parsed.Valid(RateSnapshot(date, rates))
    } catch (e: Exception) {
        Parsed.Invalid(e.javaClass.simpleName)
    }

    companion object {
        val HISTORY_START: LocalDate = LocalDate.of(2024, 3, 2)
    }
}
