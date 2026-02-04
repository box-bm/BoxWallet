package com.boxdev.boxwallet.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object Transaction : NavRoutes("transaction?transactionId={transactionId}") {
        fun createRoute(transactionId: Long? = null): String {
            return if (transactionId != null) {
                "transaction?transactionId=$transactionId"
            } else {
                "transaction"
            }
        }
    }
    data object Analytics : NavRoutes("analytics")
    data object Settings : NavRoutes("settings")
    data object ThemeCustomization : NavRoutes("theme_customization")
    data object Backup : NavRoutes("backup")
    data object Export : NavRoutes("export")
}
