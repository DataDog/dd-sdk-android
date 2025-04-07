/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.datadog.android.compose.ExperimentalTrackingApi
import com.datadog.android.compose.NavigationViewTrackingEffect
import com.datadog.android.rum.tracking.AcceptAllNavDestinations
import com.datadog.android.insights.LocalInsightOverlay

/**
 * An activity to showcase Jetpack Compose instrumentation.
 */
class JetpackComposeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colors = if (isSystemInDarkTheme()) {
                    darkColors()
                } else {
                    lightColors()
                }
            ) {
                AppScaffold()
            }
        }
        LocalInsightOverlay().attach(this)
    }

    @Composable
    private fun AppScaffold() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title =
                    {
                        Text("Jetpack compose top bar")
                    }
                )
            }
        ) {
            AppContent(modifier = Modifier.padding(it))
        }
    }

    @Composable
    @OptIn(ExperimentalTrackingApi::class)
    private fun AppContent(modifier: Modifier = Modifier) {
        val navController = rememberNavController()
        NavigationViewTrackingEffect(
            navController = navController,
            trackArguments = true,
            destinationPredicate = AcceptAllNavDestinations()
        )
        NavHost(
            navController = navController,
            startDestination = SampleScreen.Root.navigationRoute,
            modifier = modifier
        ) {
            selectionNavigation(
                navController = navController
            )
        }
    }
}
