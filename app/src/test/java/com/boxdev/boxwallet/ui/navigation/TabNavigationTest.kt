package com.boxdev.boxwallet.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNavigationTest {
    private val home = NavRoutes.Home.route
    private val loans = NavRoutes.Loans.route
    private val records = NavRoutes.TransactionsList.route
    private val analytics = NavRoutes.Analytics.route
    private val settings = NavRoutes.Settings.route
    private val loanDetail = NavRoutes.LoanDetail.route
    private val search = NavRoutes.Search.route

    @Test
    fun `tapping the current tab scrolls to top`() {
        assertEquals(TabAction.ScrollToTop(home), resolveTabClick(home, home, setOf(home)))
    }

    @Test
    fun `tapping another tab from a tab root just switches`() {
        assertEquals(TabAction.Switch(loans, popTo = null), resolveTabClick(loans, home, setOf(home)))
    }

    @Test
    fun `leaving settings for a tab pops back to home first`() {
        assertEquals(TabAction.Switch(records, popTo = home), resolveTabClick(records, settings, setOf(home)))
    }

    @Test
    fun `tapping home on settings returns to the home root`() {
        assertEquals(TabAction.PopToRoot(home), resolveTabClick(home, settings, setOf(home)))
    }

    @Test
    fun `loan detail opened from home switches to loans from home`() {
        assertEquals(TabAction.Switch(loans, popTo = home), resolveTabClick(loans, loanDetail, setOf(home)))
    }

    @Test
    fun `loan detail opened from loans pops to the loans root`() {
        assertEquals(TabAction.PopToRoot(loans), resolveTabClick(loans, loanDetail, setOf(home, loans)))
    }

    @Test
    fun `secondary destination over a non-home tab switches home from that tab`() {
        assertEquals(TabAction.Switch(home, popTo = analytics), resolveTabClick(home, search, setOf(home, analytics)))
    }

    @Test
    fun `unknown current route switches without popping`() {
        assertEquals(TabAction.Switch(loans, popTo = null), resolveTabClick(loans, null, setOf(home)))
    }

    @Test
    fun `tab routes are not secondary`() {
        assertFalse(isSecondary("home"))
        assertFalse(isSecondary("loans"))
    }

    @Test
    fun `settings, loan detail and search are secondary`() {
        assertTrue(isSecondary("settings"))
        assertTrue(isSecondary("loan_detail/{loanId}"))
        assertTrue(isSecondary("search"))
    }

    @Test
    fun `unknown route is not secondary`() {
        assertFalse(isSecondary(null))
    }
}
