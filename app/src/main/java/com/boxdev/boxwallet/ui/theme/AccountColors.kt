package com.boxdev.boxwallet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.boxdev.boxwallet.domain.model.AccountColor

// Tonal pairs (container, onContainer) as 0xRRGGBB. Light: pale container / deep ink.
// Dark: deep container / pale ink. Every pair clears 4.5:1 (enforced by AccountColorsTest).
// DEFAULT is not listed: it follows the theme's secondaryContainer pair.
internal val lightPairs: Map<AccountColor, Pair<Long, Long>> = mapOf(
    AccountColor.RED to (0xF9DEDCL to 0x410E0BL),
    AccountColor.ORANGE to (0xFFDBC9L to 0x3A1300L),
    AccountColor.AMBER to (0xFFE08BL to 0x3B2F00L),
    AccountColor.LIME to (0xDDEFA0L to 0x253200L),
    AccountColor.GREEN to (0xC4EFC9L to 0x0A3A1AL),
    AccountColor.TEAL to (0xB2EBE2L to 0x00332EL),
    AccountColor.CYAN to (0xB8EAF6L to 0x003640L),
    AccountColor.BLUE to (0xD3E3FFL to 0x0B2E66L),
    AccountColor.INDIGO to (0xE0E0FFL to 0x1A1F6BL),
    AccountColor.PURPLE to (0xEEDCFFL to 0x3A0F5EL),
    AccountColor.PINK to (0xFFD9E6L to 0x4D0A2AL),
)

internal val darkPairs: Map<AccountColor, Pair<Long, Long>> = mapOf(
    AccountColor.RED to (0x8C1D18L to 0xFFDAD6L),
    AccountColor.ORANGE to (0x7A2E00L to 0xFFDBC9L),
    AccountColor.AMBER to (0x5C4600L to 0xFFE08BL),
    AccountColor.LIME to (0x3F5200L to 0xDDEFA0L),
    AccountColor.GREEN to (0x1B5E32L to 0xC4EFC9L),
    AccountColor.TEAL to (0x00504AL to 0xB2EBE2L),
    AccountColor.CYAN to (0x004E5EL to 0xB8EAF6L),
    AccountColor.BLUE to (0x1F4A8FL to 0xD3E3FFL),
    AccountColor.INDIGO to (0x3A3F9EL to 0xE0E0FFL),
    AccountColor.PURPLE to (0x5E2E8AL to 0xEEDCFFL),
    AccountColor.PINK to (0x7D1F4AL to 0xFFD9E6L),
)

@Composable
private fun AccountColor.pair(): Pair<Long, Long> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return (if (dark) darkPairs else lightPairs).getValue(this)
}

@Composable
fun AccountColor.container(): Color =
    if (this == AccountColor.DEFAULT) MaterialTheme.colorScheme.secondaryContainer
    else Color(0xFF000000L or pair().first)

@Composable
fun AccountColor.onContainer(): Color =
    if (this == AccountColor.DEFAULT) MaterialTheme.colorScheme.onSecondaryContainer
    else Color(0xFF000000L or pair().second)
