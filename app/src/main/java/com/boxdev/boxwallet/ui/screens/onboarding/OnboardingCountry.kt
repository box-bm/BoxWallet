package com.boxdev.boxwallet.ui.screens.onboarding

import com.boxdev.boxwallet.domain.money.Country
import com.boxdev.boxwallet.domain.money.foldForSearch
import java.util.Locale

private const val FALLBACK_REGION = "US"

/** The country to preselect: the phone's region when listed, otherwise the United States. */
fun initialCountry(deviceRegion: String?, countries: List<Country>): Country {
    val region = deviceRegion?.trim()?.uppercase(Locale.ROOT).orEmpty()
    return countries.firstOrNull { it.code == region }
        ?: countries.first { it.code == FALLBACK_REGION }
}

/** Countries whose name or code matches [query], ignoring case and accents; blank returns all. */
fun filterCountries(countries: List<Country>, query: String, locale: Locale): List<Country> {
    val q = foldForSearch(query.trim(), locale)
    if (q.isEmpty()) return countries
    return countries.filter {
        foldForSearch(it.name, locale).contains(q) || foldForSearch(it.code, locale).contains(q)
    }
}
