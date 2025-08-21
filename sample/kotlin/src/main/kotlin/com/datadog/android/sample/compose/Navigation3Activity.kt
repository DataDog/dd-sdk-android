/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

internal class Navigation3Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NavDisplaySample()
        }
    }

    @Composable
    private fun NavDisplaySample() {
        val backStack = remember {
            mutableStateListOf<Nav3Page>(Nav3Page.Home)
        }
        val navigateTo: (Nav3Page) -> Unit = { dest ->
            backStack.add(dest)
        }
        val onBack: () -> Unit = {
            backStack.removeLastOrNull()
        }
        NavDisplay(
            backStack = backStack,
            onBack = { onBack.invoke() },
            entryProvider = { key ->
                when (key) {
                    is Nav3Page.Home -> NavEntry(key) {
                        HomePage(navigateTo, onBack)
                    }

                    is Nav3Page.Discovery -> NavEntry(key) {
                        DiscoveryPage(key.userId, navigateTo, onBack)
                    }

                    is Nav3Page.Settings -> NavEntry(key) {
                        Settings(key.userId, navigateTo, onBack)
                    }
                }
            }
        )
    }
}
