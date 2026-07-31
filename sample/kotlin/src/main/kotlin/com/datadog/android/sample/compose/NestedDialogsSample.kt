/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController

/**
 * Reproduces a Compose NavHost `dialog {}` destination opened on top of another `dialog {}`
 * destination (e.g. a wizard flow across multiple dialogs, or a confirmation sheet opened
 * from within a bottom sheet). Each dialog destination opens its own Android [android.view.Window],
 * which Session Replay must discover and record independently of the Activity window.
 */
@Composable
internal fun NestedDialogsSample() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_BASE) {
        composable(ROUTE_BASE) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "This screen opens a dialog {} NavHost destination, which itself opens " +
                        "a second dialog {} destination on top of it."
                )
                Button(onClick = { navController.navigate(ROUTE_FIRST_DIALOG) }) {
                    Text("Open first dialog")
                }
            }
        }
        dialog(ROUTE_FIRST_DIALOG) {
            Column(modifier = Modifier.background(Color.Cyan).padding(24.dp)) {
                Text("First dialog destination")
                Button(onClick = { navController.navigate(ROUTE_SECOND_DIALOG) }) {
                    Text("Open second dialog")
                }
            }
        }
        dialog(ROUTE_SECOND_DIALOG) {
            Column(modifier = Modifier.background(Color.Yellow).padding(24.dp)) {
                Text("Second dialog destination — this must be recorded by Session Replay too")
                Button(onClick = { navController.popBackStack() }) {
                    Text("Close")
                }
            }
        }
    }
}

private const val ROUTE_BASE = "nested_dialogs_base"
private const val ROUTE_FIRST_DIALOG = "nested_dialogs_first"
private const val ROUTE_SECOND_DIALOG = "nested_dialogs_second"

@Composable
@Preview(showBackground = true)
internal fun PreviewNestedDialogsSample() {
    NestedDialogsSample()
}
