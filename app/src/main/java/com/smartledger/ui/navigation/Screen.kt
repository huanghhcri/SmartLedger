package com.smartledger.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Outlined.Home)
    data object Record : Screen("record", "记账", Icons.Outlined.Edit)
    data object Statistics : Screen("statistics", "统计", Icons.Outlined.BarChart)
    data object Profile : Screen("profile", "我的", Icons.Outlined.Person)
}

val bottomNavItems = listOf(Screen.Home, Screen.Record, Screen.Statistics, Screen.Profile)
