package com.boxdev.boxwallet.ui.screens.onboarding

import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import java.util.Locale
import org.junit.Test
import org.junit.Assert.assertEquals

class OnboardingCountryTest {
    private val countries = CurrencyCatalog.countries(Locale.US)

    @Test
    fun deviceRegionIsPreselected() {
        val c = initialCountry("GT", countries)
        assertEquals("GT", c.code)
        assertEquals("GTQ", c.currency.currencyCode)
    }

    @Test
    fun regionIsCaseInsensitive() {
        assertEquals("GT", initialCountry("gt", countries).code)
    }

    @Test
    fun nullOrEmptyRegionFallsBackToUs() {
        assertEquals("US", initialCountry(null, countries).code)
        assertEquals("US", initialCountry("", countries).code)
    }

    @Test
    fun unknownRegionFallsBackToUs() {
        assertEquals("US", initialCountry("ZZ", countries).code)
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        val spanish = CurrencyCatalog.countries(Locale("es"))
        val names = filterCountries(spanish, "PANAMA", Locale("es")).map { it.code }
        assertEquals(true, "PA" in names)
    }
}
