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
}
