package com.boxdev.boxwallet.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

val categoryIconOptions: List<Pair<String, ImageVector>> = listOf(
    "Category" to Icons.Default.Category,
    "ShoppingCart" to Icons.Default.ShoppingCart,
    "Restaurant" to Icons.Default.Restaurant,
    "DirectionsCar" to Icons.Default.DirectionsCar,
    "Home" to Icons.Default.Home,
    "LocalHospital" to Icons.Default.LocalHospital,
    "School" to Icons.Default.School,
    "Work" to Icons.Default.Work,
    "SportsEsports" to Icons.Default.SportsEsports,
    "Flight" to Icons.Default.Flight,
    "Savings" to Icons.Default.Savings,
    "AccountBalance" to Icons.Default.AccountBalance,
    "Payments" to Icons.Default.Payments,
    "LocalGroceryStore" to Icons.Default.LocalGroceryStore,
    "Checkroom" to Icons.Default.Checkroom,
    "Wifi" to Icons.Default.Wifi,
    "Bolt" to Icons.Default.Bolt,
    "Pets" to Icons.Default.Pets,
    "FitnessCenter" to Icons.Default.FitnessCenter,
    "Movie" to Icons.Default.Movie
)

fun categoryIconVector(name: String): ImageVector =
    categoryIconOptions.find { it.first == name }?.second ?: Icons.Default.Category
