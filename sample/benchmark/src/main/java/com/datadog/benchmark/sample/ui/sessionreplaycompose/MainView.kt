/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.sessionreplaycompose

import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.datadog.android.BuildConfig

@Composable
internal fun MainView() {
    AppScaffold()
}

@Composable
internal fun AppScaffold() {
    val navController = rememberNavController()
    Scaffold(
        topBar = {
            TopAppBar {
                Text(
                    modifier = Modifier.padding(start = 16.dp),
                    text = "Benchmark application (${BuildConfig.BUILD_TYPE})",
                    style = MaterialTheme.typography.h6
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(navHostController = navController)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Text.route,
            Modifier.padding(padding)
        ) {
            composable(Screen.Text.route) { TextScreen() }
            composable(Screen.Image.route) { ImageScreen() }
            composable(Screen.Other.route) { OtherScreen() }
        }
    }
}

@Composable
internal fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    navHostController: NavHostController
) {
    val currentBackStackEntry by navHostController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val screens = listOf(Screen.Text, Screen.Image, Screen.Other)
    BottomNavigation(modifier) {
        screens.forEach { screen ->
            BottomNavigationItem(
                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                onClick = {
                    navHostController.navigate(screen.route) {
                        // Avoid multiple copies of the same destination when reselecting
                        popUpTo(navHostController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

internal sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Text : Screen("text", "Text", Icons.Default.Edit)
    object Image : Screen("image", "Image", Icons.Default.Info)
    object Other : Screen("other", "Other", Icons.Default.Done)
}
