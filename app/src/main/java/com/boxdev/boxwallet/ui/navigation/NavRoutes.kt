package com.boxdev.boxwallet.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object TransactionsList : NavRoutes("transactions_list")
    data object Loans : NavRoutes("loans")
    data object LoanDetail : NavRoutes("loan_detail/{loanId}") {
        fun createRoute(loanId: Long) = "loan_detail/$loanId"
    }
    data object Analytics : NavRoutes("analytics")
    data object Settings : NavRoutes("settings")
    data object GeneralSettings : NavRoutes("general_settings")
    data object Categories : NavRoutes("categories")
    data object ThemeCustomization : NavRoutes("theme_customization")
    data object Backup : NavRoutes("backup")
    data object DataManagement : NavRoutes("data_management")
    data object Onboarding : NavRoutes("onboarding")
}
