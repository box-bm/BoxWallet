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
    "Movie" to Icons.Default.Movie,
    // Entertainment & Leisure
    "MusicNote" to Icons.Default.MusicNote,
    "Videocam" to Icons.Default.Videocam,
    "Headphones" to Icons.Default.Headphones,
    "SportsBasketball" to Icons.Default.SportsBasketball,
    "TheaterComedy" to Icons.Default.TheaterComedy,
    "Casino" to Icons.Default.Casino,
    // Food & Dining
    "LocalCafe" to Icons.Default.LocalCafe,
    "LocalBar" to Icons.Default.LocalBar,
    "Cake" to Icons.Default.Cake,
    "LocalPizza" to Icons.Default.LocalPizza,
    "Fastfood" to Icons.Default.Fastfood,
    // Transportation
    "DirectionsBus" to Icons.Default.DirectionsBus,
    "Train" to Icons.Default.Train,
    "DirectionsBike" to Icons.Default.DirectionsBike,
    "LocalTaxi" to Icons.Default.LocalTaxi,
    "LocalGasStation" to Icons.Default.LocalGasStation,
    "TwoWheeler" to Icons.Default.TwoWheeler,
    // Shopping & Personal
    "LocalMall" to Icons.Default.LocalMall,
    "LocalFlorist" to Icons.Default.LocalFlorist,
    "Watch" to Icons.Default.Watch,
    "Diamond" to Icons.Default.Diamond,
    "ShoppingBag" to Icons.Default.ShoppingBag,
    // Health & Wellness
    "Spa" to Icons.Default.Spa,
    "SelfImprovement" to Icons.Default.SelfImprovement,
    "MonitorHeart" to Icons.Default.MonitorHeart,
    "MedicalServices" to Icons.Default.MedicalServices,
    // Technology & Services
    "Smartphone" to Icons.Default.Smartphone,
    "Computer" to Icons.Default.Computer,
    "Tv" to Icons.Default.Tv,
    "PhoneAndroid" to Icons.Default.PhoneAndroid,
    "Devices" to Icons.Default.Devices,
    "Cloud" to Icons.Default.Cloud,
    // Utilities & Bills
    "WaterDrop" to Icons.Default.WaterDrop,
    "LocalLaundryService" to Icons.Default.LocalLaundryService,
    "Whatshot" to Icons.Default.Whatshot,
    "AcUnit" to Icons.Default.AcUnit,
    // Financial
    "CreditCard" to Icons.Default.CreditCard,
    "AttachMoney" to Icons.Default.AttachMoney,
    "TrendingUp" to Icons.Default.TrendingUp,
    "AccountBalanceWallet" to Icons.Default.AccountBalanceWallet,
    "MonetizationOn" to Icons.Default.MonetizationOn,
    // Education & Books
    "MenuBook" to Icons.Default.MenuBook,
    "AutoStories" to Icons.Default.AutoStories,
    "Backpack" to Icons.Default.Backpack,
    // Miscellaneous
    "CardGiftcard" to Icons.Default.CardGiftcard,
    "ChildCare" to Icons.Default.ChildCare,
    "Elderly" to Icons.Default.Elderly,
    "Apartment" to Icons.Default.Apartment,
    "Cottage" to Icons.Default.Cottage,
    "Park" to Icons.Default.Park,
    "Celebration" to Icons.Default.Celebration,
    "Redeem" to Icons.Default.Redeem,
    "Notifications" to Icons.Default.Notifications,
    "Star" to Icons.Default.Star
)

fun categoryIconVector(name: String): ImageVector =
    categoryIconOptions.find { it.first == name }?.second ?: Icons.Default.Category
