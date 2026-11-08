package com.boxdev.boxwallet.ui.screens.settings

import com.boxdev.boxwallet.ui.navigation.NavRoutes

/** A settings page that opens from the Settings list: its own route on phones, the detail pane on wide windows. */
enum class SettingsSection(val route: String) {
    GENERAL(NavRoutes.GeneralSettings.route),
    CURRENCIES(NavRoutes.Currencies.route),
    CATEGORIES(NavRoutes.Categories.route),
    THEME(NavRoutes.ThemeCustomization.route),
    BACKUP(NavRoutes.Backup.route),
    DATA(NavRoutes.DataManagement.route);

    companion object {
        /** The section a route opens, or null for the Settings list itself and any other route. */
        fun fromRoute(route: String?): SettingsSection? = entries.firstOrNull { it.route == route }

        /**
         * With two panes a section route on top belongs in the Settings detail pane (an unfold or
         * rotation while it was open): the section to move there, or null to leave [route] alone.
         */
        fun toMoveIntoPane(twoPane: Boolean, route: String?): SettingsSection? =
            if (twoPane) fromRoute(route) else null

        /** Key on the settings back-stack entry for the section the list-detail opens once ([name]). */
        const val PRESELECT_KEY = "settings_preselect"
    }
}
