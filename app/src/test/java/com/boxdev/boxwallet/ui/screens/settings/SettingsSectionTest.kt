package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.ui.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsSectionTest {

    @Test
    fun `each section maps to its settings route`() {
        assertEquals(NavRoutes.GeneralSettings.route, SettingsSection.GENERAL.route)
        assertEquals(NavRoutes.Currencies.route, SettingsSection.CURRENCIES.route)
        assertEquals(NavRoutes.Categories.route, SettingsSection.CATEGORIES.route)
        assertEquals(NavRoutes.ThemeCustomization.route, SettingsSection.THEME.route)
        assertEquals(NavRoutes.Backup.route, SettingsSection.BACKUP.route)
        assertEquals(NavRoutes.DataManagement.route, SettingsSection.DATA.route)
    }

    @Test
    fun `fromRoute round-trips every section`() {
        SettingsSection.entries.forEach { section ->
            assertEquals(section, SettingsSection.fromRoute(section.route))
        }
    }

    @Test
    fun `fromRoute returns null for the settings list and unknown routes`() {
        assertNull(SettingsSection.fromRoute(NavRoutes.Settings.route))
        assertNull(SettingsSection.fromRoute(null))
        assertNull(SettingsSection.fromRoute("currency_detail/{code}"))
    }
}
