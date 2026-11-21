package com.boxdev.boxwallet.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavRoutesTest {

    @Test
    fun transactionFormRoute() {
        assertEquals("transaction_form?id={id}", NavRoutes.TransactionForm.route)
        assertEquals("transaction_form", NavRoutes.TransactionForm.createRoute())
        assertEquals("transaction_form?id=5", NavRoutes.TransactionForm.createRoute(5))
    }

    @Test
    fun loanFormRoute() {
        assertEquals("loan_form?id={id}", NavRoutes.LoanForm.route)
        assertEquals("loan_form", NavRoutes.LoanForm.createRoute())
        assertEquals("loan_form?id=7", NavRoutes.LoanForm.createRoute(7))
    }

    @Test
    fun accountRoutes() {
        assertEquals("accounts", NavRoutes.Accounts.route)
        assertEquals("account_detail/{accountId}", NavRoutes.AccountDetail.route)
        assertEquals("account_detail/7", NavRoutes.AccountDetail.createRoute(7))
        assertEquals("account_form?id={id}", NavRoutes.AccountForm.route)
        assertEquals("account_form", NavRoutes.AccountForm.createRoute())
        assertEquals("account_form?id=3", NavRoutes.AccountForm.createRoute(3))
    }

    @Test
    fun transferFormRoute() {
        assertEquals("transfer_form?id={id}&from={from}&to={to}&amount={amount}", NavRoutes.TransferForm.route)
        assertEquals("transfer_form", NavRoutes.TransferForm.createRoute())
        assertEquals("transfer_form?from=2&amount=10.50", NavRoutes.TransferForm.createRoute(from = 2, amount = "10.50"))
        assertEquals("transfer_form?id=7", NavRoutes.TransferForm.createRoute(id = 7))
    }
}
