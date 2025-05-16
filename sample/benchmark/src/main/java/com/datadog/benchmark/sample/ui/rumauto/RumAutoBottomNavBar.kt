/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.rumauto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun RumAutoBottomNavBar() {
    BottomAppBar(
        actions = {
            BottomBarItem(icon = Icons.Filled.Person, label ="Characters", onClick = {})
            BottomBarItem(icon = Icons.Filled.LocationOn, label ="Locations", onClick = {})
            BottomBarItem(icon = Icons.Filled.DateRange, label ="Episodes", onClick = {})
            BottomBarItem(icon = Icons.Filled.Menu, label ="Docs", onClick = {})
        }
    )
}

@Composable
private fun RowScope.BottomBarItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Column {
            Icon(icon, contentDescription = null, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(text = label)
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun RumAutoBottomNavBarPreview() {
    RumAutoBottomNavBar()
}
