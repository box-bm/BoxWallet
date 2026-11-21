package com.boxdev.boxwallet.ui.theme

import com.boxdev.boxwallet.domain.model.AccountColor
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountColorsTest {

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val tinted = AccountColor.entries - AccountColor.DEFAULT

    @Test fun keysAreUnique() {
        val keys = AccountColor.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun fromKeyRoundTripsEveryEntry() {
        AccountColor.entries.forEach { assertEquals(it, AccountColor.fromKey(it.key)) }
    }

    @Test fun fromKeyFallsBackToDefault() {
        assertEquals(AccountColor.DEFAULT, AccountColor.fromKey("nope"))
        assertEquals(AccountColor.DEFAULT, AccountColor.fromKey(null))
    }

    @Test fun everyTintedColorHasBothPairs() {
        assertEquals(11, tinted.size)
        assertEquals(tinted.toSet(), lightPairs.keys)
        assertEquals(tinted.toSet(), darkPairs.keys)
    }

    @Test fun onContainerContrastIsAtLeast4_5InBothThemes() {
        listOf("light" to lightPairs, "dark" to darkPairs).forEach { (theme, pairs) ->
            pairs.forEach { (color, pair) ->
                val ratio = contrast(pair.first, pair.second)
                assertTrue("$color in $theme has contrast $ratio", ratio >= 4.5)
            }
        }
    }
}
