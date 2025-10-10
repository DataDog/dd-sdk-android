/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("MatchingDeclarationName", "PackageNameVisibility")

package com.datadog.android.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.compose.internal.ComposeNavigationObserver
import com.datadog.android.compose.internal.InstrumentationType
import com.datadog.android.compose.internal.SupportLibrary
import com.datadog.android.compose.internal.sendTelemetry
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.tracking.AcceptAllNavDestinations
import com.datadog.android.rum.tracking.ComponentPredicate

/**
 * A side effect which should be used to track view navigation with the Navigation
 * for Compose setup.
 *
 * @param navController [NavController] to watch
 * @param trackArguments whether to track navigation arguments
 * @param destinationPredicate to accept the [NavDestination] that will be taken into account as
 * valid RUM View events.
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 */
@Composable
@NonRestartableComposable
fun NavigationViewTrackingEffect(
    navController: NavController,
    trackArguments: Boolean = true,
    destinationPredicate: ComponentPredicate<NavDestination> = AcceptAllNavDestinations(),
    sdkCore: SdkCore = Datadog.getInstance()
) {
    LaunchedEffect(Unit) {
        sendTelemetry(
            autoInstrumented = false,
            instrumentationType = InstrumentationType.ViewTracking,
            supportLibrary = SupportLibrary.Navigation,
            sdkCore = sdkCore
        )
    }
    InternalNavigationViewTrackingStrategy(
        navController = navController,
        trackArguments = trackArguments,
        destinationPredicate = destinationPredicate,
        sdkCore = sdkCore
    )
}

/**
 * This is the internal function reserved to Datadog Kotlin Compiler Plugin for auto-instrumentation,
 * with telemetry to indicate that the auto-instrumentation is used instead of manual instrumentation.
 *
 * @param navController [NavController] to watch
 * @param trackArguments whether to track navigation arguments
 * @param destinationPredicate to accept the [NavDestination] that will be taken into account as
 * valid RUM View events.
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 */
@Composable
@NonRestartableComposable
internal fun InstrumentedNavigationViewTrackingEffect(
    navController: NavController,
    trackArguments: Boolean = true,
    destinationPredicate: ComponentPredicate<NavDestination> = AcceptAllNavDestinations(),
    sdkCore: SdkCore = Datadog.getInstance()
) {
    LaunchedEffect(Unit) {
        sendTelemetry(
            autoInstrumented = true,
            instrumentationType = InstrumentationType.ViewTracking,
            supportLibrary = SupportLibrary.Navigation,
            sdkCore = sdkCore
        )
    }
    InternalNavigationViewTrackingStrategy(
        navController = navController,
        trackArguments = trackArguments,
        destinationPredicate = destinationPredicate,
        sdkCore = sdkCore
    )
}

@Composable
@NonRestartableComposable
private fun InternalNavigationViewTrackingStrategy(
    navController: NavController,
    trackArguments: Boolean = true,
    destinationPredicate: ComponentPredicate<NavDestination> = AcceptAllNavDestinations(),
    sdkCore: SdkCore = Datadog.getInstance()
) {
    val currentTrackArguments by rememberUpdatedState(newValue = trackArguments)
    val currentDestinationPredicate by rememberUpdatedState(newValue = destinationPredicate)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
        val observer = ComposeNavigationObserver(
            currentTrackArguments,
            currentDestinationPredicate,
            navController,
            GlobalRumMonitor.get(sdkCore)
        )

        @Suppress("ThreadSafety") // TODO RUM-525 check composable threading rules
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            @Suppress("ThreadSafety") // TODO RUM-525 check composable threading rules
            lifecycleOwner.lifecycle.removeObserver(observer)
            observer.onDispose()
        }
    }
}
