/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("MatchingDeclarationName")

package com.datadog.android.compose

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.AcceptAllNavDestinations
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.v2.api.SdkCore

internal class ComposeNavigationObserver(
    private val trackArguments: Boolean = true,
    private val destinationPredicate: ComponentPredicate<NavDestination> =
        AcceptAllNavDestinations(),
    private val navController: NavController,
    private val rumMonitor: RumMonitor
) : LifecycleEventObserver, NavController.OnDestinationChangedListener {

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) {
            // once listener is added, it will receive current destination if available
            navController.addOnDestinationChangedListener(this)
        } else if (event == Lifecycle.Event.ON_PAUSE) {
            navController.currentDestination?.route?.let {
                rumMonitor.stopView(it)
            }

            navController.removeOnDestinationChangedListener(this)
        }
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        if (destinationPredicate.accept(destination)) {
            destination.route?.let {
                startView(destination, it, arguments)
            }
        }
    }

    internal fun onDispose() {
        // just a safe-guard if ON_PAUSE wasn't called
        navController.removeOnDestinationChangedListener(this)
    }

    private fun startView(
        navDestination: NavDestination,
        route: String,
        arguments: Bundle?
    ) {
        val viewName = destinationPredicate.getViewName(navDestination) ?: route
        rumMonitor.startView(
            key = route,
            name = viewName,
            attributes = if (trackArguments) {
                convertToRumAttributes(arguments)
            } else {
                emptyMap()
            }
        )
    }

    private fun convertToRumAttributes(bundle: Bundle?): Map<String, Any?> {
        if (bundle == null) return emptyMap()

        val attributes = mutableMapOf<String, Any?>()

        bundle.keySet().forEach {
            // TODO RUMM-2717 Bundle#get is deprecated, but there is no replacement for it.
            // Issue is opened in the Google Issue Tracker.
            @Suppress("DEPRECATION")
            attributes["$ARGUMENT_TAG.$it"] = bundle.get(it)
        }

        return attributes
    }

    companion object {
        private const val ARGUMENT_TAG: String = "view.arguments"
    }
}

/**
 * A side effect which should be used to track view navigation with the Navigation
 * for Compose setup.
 *
 * @param navController [NavController] to watch
 * @param sdkCore the SDK instance to use. If not provided, default instance will be used.
 * @param trackArguments whether to track navigation arguments
 * @param destinationPredicate to accept the [NavDestination] that will be taken into account as
 * valid RUM View events.
 */
@ExperimentalTrackingApi
@Composable
@NonRestartableComposable
fun NavigationViewTrackingEffect(
    navController: NavController,
    sdkCore: SdkCore = Datadog.getInstance(),
    trackArguments: Boolean = true,
    destinationPredicate: ComponentPredicate<NavDestination> = AcceptAllNavDestinations()
) {
    val currentTrackArguments by rememberUpdatedState(newValue = trackArguments)
    val currentDestinationPredicate by rememberUpdatedState(newValue = destinationPredicate)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, navController) {
        val observer = ComposeNavigationObserver(
            currentTrackArguments,
            currentDestinationPredicate,
            navController,
            GlobalRum.get(sdkCore)
        )

        @Suppress("ThreadSafety") // TODO RUMM-2214 check composable threading rules
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            @Suppress("ThreadSafety") // TODO RUMM-2214 check composable threading rules
            lifecycleOwner.lifecycle.removeObserver(observer)
            observer.onDispose()
        }
    }
}
