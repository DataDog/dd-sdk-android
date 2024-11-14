/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.datadog.android.rum.GlobalRumMonitor
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
internal fun TabsSample() {
    Scaffold(
        bottomBar = {
            NavigationBar()
        }
    ) {
        AppContent(modifier = Modifier.padding(it))
    }
}

@Composable
@OptIn(ExperimentalPagerApi::class)
@Suppress("LongMethod")
private fun AppContent(modifier: Modifier = Modifier) {
    Column(modifier) {
        val pages = remember {
            listOf(Page.Navigation, Page.Interactions)
        }
        val pagerState = rememberPagerState()
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                val rumMonitor = GlobalRumMonitor.get()
                val screen = pages[pagerState.currentPage].trackingName
                if (event == Lifecycle.Event.ON_RESUME) {
                    rumMonitor.startView(screen, screen)
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    rumMonitor.stopView(screen)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }
                // drop 1st, because it will be tracked by the lifecycle
                .drop(1)
                .collect { page ->
                    val screen = pages[page].trackingName
                    GlobalRumMonitor.get().startView(screen, screen)
                }
        }

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.pagerTabIndicatorOffset(
                        pagerState,
                        tabPositions
                    ),
                    height = TabRowDefaults.IndicatorHeight * 2
                )
            }
        ) {
            val coroutineScope = rememberCoroutineScope()
            pages.forEachIndexed { index, page ->
                Tab(
                    text = { Text(page.name) },
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }
        }
        HorizontalPager(
            count = pages.size,
            state = pagerState
        ) { page ->
            when (page) {
                0 -> NavigationSampleView()
                else -> InteractionSampleView()
            }
        }
    }
}

@Composable
private fun NavigationBar() {
    val selectedIndex = remember { mutableIntStateOf(1) }
    BottomNavigation {
        BottomNavigationItem(
            selected = selectedIndex.intValue == 1,
            onClick = {
                selectedIndex.intValue = 1
            },
            icon = {
                Icon(imageVector = Icons.Filled.Edit, contentDescription = "edit")
            },
            label = {
                Text("label 1")
            }
        )

        BottomNavigationItem(
            selected = selectedIndex.intValue == 2,
            onClick = {
                selectedIndex.intValue = 2
            },
            icon = {
                Icon(imageVector = Icons.Filled.Email, contentDescription = "mail")
            },
            label = {
                Text("label 2")
            }
        )
    }
}
