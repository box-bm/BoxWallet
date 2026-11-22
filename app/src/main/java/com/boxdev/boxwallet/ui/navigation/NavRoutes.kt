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
    /**
     * [id] is either leg's transaction id; [amount] is plain major units in the source currency, [receive]
     * plain major units in the destination currency (what must arrive, e.g. a card's statement).
     */
    data object TransferForm : NavRoutes("transfer_form?id={id}&from={from}&to={to}&amount={amount}&receive={receive}") {
        fun createRoute(
            id: Long? = null,
            from: Long? = null,
            to: Long? = null,
            amount: String? = null,
            receive: String? = null,
        ): String {
            val args = listOfNotNull(
                id?.let { "id=$it" },
                from?.let { "from=$it" },
                to?.let { "to=$it" },
                amount?.let { "amount=$it" },
                receive?.let { "receive=$it" },
            )
            return if (args.isEmpty()) "transfer_form" else "transfer_form?" + args.joinToString("&")
        }
    }
    /** [accountId] preselects the account a new loan goes in. */
    data object LoanForm : NavRoutes("loan_form?id={id}&accountId={accountId}") {
        fun createRoute(id: Long? = null, accountId: Long? = null): String {
            val args = listOfNotNull(id?.let { "id=$it" }, accountId?.let { "accountId=$it" })
            return if (args.isEmpty()) "loan_form" else "loan_form?" + args.joinToString("&")
        }
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
    data object Accounts : NavRoutes("accounts")
    data object AccountDetail : NavRoutes("account_detail/{accountId}") {
        fun createRoute(accountId: Long) = "account_detail/$accountId"
    }
    data object InstallmentPlanDetail : NavRoutes("installment_plan/{planId}") {
        fun createRoute(planId: Long) = "installment_plan/$planId"
    }
    data object AccountForm : NavRoutes("account_form?id={id}") {
        fun createRoute(id: Long? = null) = if (id == null) "account_form" else "account_form?id=$id"
    }
    data object Categories : NavRoutes("categories")
    data object ThemeCustomization : NavRoutes("theme_customization")
    data object Backup : NavRoutes("backup")
    data object DataManagement : NavRoutes("data_management")
    data object Onboarding : NavRoutes("onboarding")
}
