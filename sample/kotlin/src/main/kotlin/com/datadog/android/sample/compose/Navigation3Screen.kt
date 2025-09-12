/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomePage(
    navigateTo: (Nav3Page) -> Unit,
    onBack: () -> Unit
) {
    val userId = remember {
        mutableStateOf("user123")
    }
    PageScaffold(
        title = "Home",
        onBack = onBack,
        content = {
            Button(onClick = { navigateTo(Nav3Page.Discovery(userId.value)) }) { Text("Go to Discovery") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { navigateTo(Nav3Page.Settings(userId.value)) }) { Text("Go to Settings") }
        }
    )
}

@Composable
internal fun DiscoveryPage(
    userId: String,
    navigateTo: (Nav3Page) -> Unit,
    onBack: () -> Unit
) {
    PageScaffold(
        title = "Discovery",
        onBack = onBack,
        content = {
            Button(onClick = { navigateTo(Nav3Page.Home) }) { Text("Go to Home") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { navigateTo(Nav3Page.Settings(userId)) }) { Text("Go to Settings") }
        }
    )
}

@Composable
internal fun Settings(
    userId: String,
    navigateTo: (Nav3Page) -> Unit,
    onBack: () -> Unit
) {
    PageScaffold(
        title = "Settings",
        onBack = onBack,
        content = {
            Button(onClick = { navigateTo(Nav3Page.Home) }) { Text("Go to Home") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { navigateTo(Nav3Page.Discovery(userId)) }) { Text("Go to Discovery") }
        }
    )
}

@Composable
private fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(text = title, fontSize = 24.sp)
        content()
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}

internal sealed class Nav3Page {
    object Home : Nav3Page()
    data class Discovery(val userId: String) : Nav3Page()
    data class Settings(val userId: String) : Nav3Page()
}
