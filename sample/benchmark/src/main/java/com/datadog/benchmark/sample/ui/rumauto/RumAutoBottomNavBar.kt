/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun RumAutoBottomNavBar(
    currentTab: RumAutoScenarioTab,
    onTabClicked: (RumAutoScenarioTab) -> Unit
) {
    @Composable
    fun RowScope.BottomBarItem(
        icon: ImageVector,
        label: String,
        tab: RumAutoScenarioTab
    ) {
        IconButton(
            onClick = {
                onTabClicked(tab)
            },
            modifier = Modifier
                .weight(1f)
                .background(backgroundColor(currentTab == tab))
        ) {
            Column {
                Icon(icon, contentDescription = null, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(text = label)
            }
        }
    }

    BottomAppBar(
        actions = {
            BottomBarItem(icon = Icons.Filled.Person, label = "Characters", tab = RumAutoScenarioTab.CHARACTERS)
            BottomBarItem(icon = Icons.Filled.LocationOn, label = "Locations", tab = RumAutoScenarioTab.LOCATIONS)
            BottomBarItem(icon = Icons.Filled.DateRange, label = "Episodes", tab = RumAutoScenarioTab.EPISODES)
        }
    )
}

private fun backgroundColor(isSelected: Boolean): Color {
    return if (isSelected) Color.LightGray else Color.Transparent
}

@Preview(showBackground = true)
@Composable
internal fun RumAutoBottomNavBarPreview() {
    RumAutoBottomNavBar(RumAutoScenarioTab.EPISODES) {}
}
