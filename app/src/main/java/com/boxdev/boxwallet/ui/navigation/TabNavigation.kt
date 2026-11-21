package com.boxdev.boxwallet.ui.navigation

import androidx.navigation.NavController

sealed interface TabAction {
    /** Leave everything above [popTo] (unsaved), then switch to [route] as a tab. popTo == null: already at a tab root. */
    data class Switch(val route: String, val popTo: String?) : TabAction
    data class PopToRoot(val route: String) : TabAction
    data class ScrollToTop(val route: String) : TabAction
}

/**
 * Decides what tapping the [target] tab does. [tabsInBackStack]: tab routes currently on the back
 * stack (always contains home). A secondary destination (settings, detail, search...) belongs to the
 * non-home tab on the stack if there is one, else to home.
 */
fun resolveTabClick(target: String, currentRoute: String?, tabsInBackStack: Set<String>): TabAction {
    if (currentRoute == null) return TabAction.Switch(target, popTo = null)
    val home = NavRoutes.Home.route
    val tabRoutes = Destination.entries.map { it.route }
    if (currentRoute in tabRoutes) {
        return if (currentRoute == target) TabAction.ScrollToTop(target) else TabAction.Switch(target, popTo = null)
    }
    val root = tabsInBackStack.firstOrNull { it != home && it in tabRoutes } ?: home
    return if (root == target) TabAction.PopToRoot(target) else TabAction.Switch(target, popTo = root)
}

/** True for any route that is not a tab root (settings, details, search, forms): navigation hides there. */
fun isSecondary(route: String?): Boolean =
    route != null && route !in Destination.entries.map { it.route }

fun NavController.performTabAction(action: TabAction, onScrollToTop: (String) -> Unit) {
    when (action) {
        is TabAction.Switch -> {
            action.popTo?.let { popBackStack(it, inclusive = false, saveState = false) }
            navigateToTab(action.route)
        }
        is TabAction.PopToRoot -> popBackStack(action.route, inclusive = false)
        is TabAction.ScrollToTop -> onScrollToTop(action.route)
    }
}

/** Tab routes currently on the back stack. */
fun NavController.tabsInBackStack(): Set<String> = Destination.entries.map { it.route }
    .filter { runCatching { getBackStackEntry(it) }.isSuccess }
    .toSet()

/**
 * Switches to the [route] tab the way tapping it does. From a secondary destination (an account
 * opened from Settings, say) that stack is left first; a bare [navigateToTab] would save it as the
 * state of the tab underneath, and tapping that tab later would bring it back.
 */
fun NavController.switchToTab(route: String) =
    performTabAction(resolveTabClick(route, currentDestination?.route, tabsInBackStack()), onScrollToTop = {})
