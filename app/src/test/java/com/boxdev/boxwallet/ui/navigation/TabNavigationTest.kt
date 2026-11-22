package com.boxdev.boxwallet.ui.navigation

import android.content.Context
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for the NavController tests at the end; the rest are pure.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TabNavigationTest {
    private val home = NavRoutes.Home.route
    private val loans = NavRoutes.Loans.route
    private val records = NavRoutes.TransactionsList.route
    private val analytics = NavRoutes.Analytics.route
    private val settings = NavRoutes.Settings.route
    private val loanDetail = NavRoutes.LoanDetail.route
    private val search = NavRoutes.Search.route

    @Test
    fun `there are three destinations`() {
        assertEquals(listOf(home, records, analytics), Destination.entries.map { it.route })
    }

    @Test
    fun `tapping the current tab scrolls to top`() {
        assertEquals(TabAction.ScrollToTop(home), resolveTabClick(home, home, setOf(home)))
    }

    @Test
    fun `tapping another tab from a tab root just switches`() {
        assertEquals(TabAction.Switch(records, popTo = null), resolveTabClick(records, home, setOf(home)))
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
    fun `loan detail opened from home switches to movements from home`() {
        assertEquals(TabAction.Switch(records, popTo = home), resolveTabClick(records, loanDetail, setOf(home)))
    }

    @Test
    fun `loan detail opened from movements pops to the movements root`() {
        assertEquals(TabAction.PopToRoot(records), resolveTabClick(records, loanDetail, setOf(home, records)))
    }

    @Test
    fun `secondary destination over a non-home tab switches home from that tab`() {
        assertEquals(TabAction.Switch(home, popTo = analytics), resolveTabClick(home, search, setOf(home, analytics)))
    }

    @Test
    fun `unknown current route switches without popping`() {
        assertEquals(TabAction.Switch(records, popTo = null), resolveTabClick(records, null, setOf(home)))
    }

    @Test
    fun `tab routes are not secondary`() {
        assertFalse(isSecondary("home"))
    }

    @Test
    fun `the loans list is secondary now that it is not a tab`() {
        assertTrue(isSecondary(loans))
    }

    @Test
    fun `settings, loan detail and search are secondary`() {
        assertTrue(isSecondary("settings"))
        assertTrue(isSecondary("loan_detail/{loanId}"))
        assertTrue(isSecondary("search"))
    }

    @Test
    fun `movement and loan forms are secondary`() {
        assertTrue(isSecondary(NavRoutes.TransactionForm.route))
        assertTrue(isSecondary(NavRoutes.LoanForm.route))
    }

    @Test
    fun `unknown route is not secondary`() {
        assertFalse(isSecondary(null))
    }

    /** A real back stack with the routes the account flow goes through. */
    private fun navController(): NavHostController {
        val nav = NavHostController(ApplicationProvider.getApplicationContext<Context>())
        nav.navigatorProvider.addNavigator(ComposeNavigator())
        nav.graph = nav.createGraph(startDestination = home) {
            composable(home) {}
            composable(records) {}
            composable(NavRoutes.Settings.route) {}
            composable(NavRoutes.Accounts.route) {}
            composable(NavRoutes.AccountDetail.route, listOf(navArgument("accountId") { type = NavType.LongType })) {}
        }
        return nav
    }

    private fun NavHostController.routes() = currentBackStack.value.mapNotNull { it.destination.route }.filter { it != graph.route }

    private fun NavHostController.tapTab(route: String) =
        performTabAction(resolveTabClick(route, currentDestination?.route, tabsInBackStack()), onScrollToTop = {})

    @Test
    fun `see in movements from an account then home shows home`() {
        val nav = navController()
        nav.navigate(NavRoutes.Settings.route)
        nav.navigate(NavRoutes.Accounts.route)
        nav.navigate(NavRoutes.AccountDetail.createRoute(7))

        nav.switchToTab(records)
        assertEquals(listOf(home, records), nav.routes())

        nav.tapTab(home)
        assertEquals(listOf(home), nav.routes())
    }

    @Test
    fun `switching from a tab root keeps the plain tab switch`() {
        val nav = navController()
        nav.switchToTab(records)

        assertEquals(listOf(home, records), nav.routes())
    }
}
