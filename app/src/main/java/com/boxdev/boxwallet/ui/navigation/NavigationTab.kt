@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.zIndex
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.boxdev.boxwallet.ui.components.LoanSheet
import com.boxdev.boxwallet.ui.components.TransactionSheet

@Composable
fun NavigationTab(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabRoutes = Destination.entries.map { it.route }.toSet()
    val showBottomBar = currentRoute in tabRoutes

    var fabExpanded by remember { mutableStateOf(false) }
    var showTransactionSheet by remember { mutableStateOf(false) }
    var showLoanSheet by remember { mutableStateOf(false) }
    var selectedTransactionId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (fabExpanded) fabExpanded = false
            },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == destination.route) destination.activeIcon else destination.icon,
                                    contentDescription = destination.contentDescription
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute === NavRoutes.Settings.route) {
                return@Scaffold
            }

            FloatingActionButtonMenu(
                modifier = Modifier.zIndex(999F),
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        modifier = Modifier.animateFloatingActionButton(
                            visible = true,
                            alignment = Alignment.BottomEnd
                        ),
                        checked = fabExpanded,
                        onCheckedChange = { checked -> fabExpanded = checked },
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add
                            }
                        }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = null,
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        selectedTransactionId = null
                        showTransactionSheet = true
                    },
                    text = { Text("Add Transaction") },
                    icon = {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = "Receipt icon",
                        )
                    }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        showLoanSheet = true
                    },
                    text = { Text("Add Loan") },
                    icon = {
                        Icon(
                            Icons.Default.CreditCard,
                            contentDescription = "Credit Card icon",
                        )
                    }
                )
            }
        }
    ) { contentPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(contentPadding),
            onAddTransaction = {
                selectedTransactionId = null
                showTransactionSheet = true
            },
            onEditTransaction = { id ->
                selectedTransactionId = id
                showTransactionSheet = true
            }
        )
    }


    if (showTransactionSheet) {
        TransactionSheet(
            transactionId = selectedTransactionId,
            onDismiss = {
                showTransactionSheet = false
                selectedTransactionId = null
            }
        )
    }

    if (showLoanSheet) {
        LoanSheet(
            loanId = null,
            onDismiss = { showLoanSheet = false }
        )
    }
}
