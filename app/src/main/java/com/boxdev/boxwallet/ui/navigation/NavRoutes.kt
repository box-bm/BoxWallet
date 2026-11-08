package com.boxdev.boxwallet.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object TransactionsList : NavRoutes("transactions_list")
    data object Loans : NavRoutes("loans")
    data object LoanDetail : NavRoutes("loan_detail/{loanId}") {
        fun createRoute(loanId: Long) = "loan_detail/$loanId"
    }
    data object TransactionForm : NavRoutes("transaction_form?id={id}") {
        fun createRoute(id: Long? = null) = if (id == null) "transaction_form" else "transaction_form?id=$id"
    }
    data object LoanForm : NavRoutes("loan_form?id={id}") {
        fun createRoute(id: Long? = null) = if (id == null) "loan_form" else "loan_form?id=$id"
    }
    data object Analytics : NavRoutes("analytics")
    data object Search : NavRoutes("search")
    data object Settings : NavRoutes("settings")
    data object GeneralSettings : NavRoutes("general_settings")
    data object Currencies : NavRoutes("currencies")
    data object CurrencyDetail : NavRoutes("currency_detail/{code}") {
        fun createRoute(code: String) = "currency_detail/$code"
    }
    data object MainCurrencyChange : NavRoutes("main_currency_change/{code}") {
        fun createRoute(code: String) = "main_currency_change/$code"
    }
    data object Categories : NavRoutes("categories")
    data object ThemeCustomization : NavRoutes("theme_customization")
    data object Backup : NavRoutes("backup")
    data object DataManagement : NavRoutes("data_management")
    data object Onboarding : NavRoutes("onboarding")
}
