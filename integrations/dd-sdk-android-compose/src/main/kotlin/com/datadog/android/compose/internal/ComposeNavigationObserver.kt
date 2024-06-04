/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.tracking.AcceptAllNavDestinations
import com.datadog.android.rum.tracking.ComponentPredicate

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
            // TODO RUM-503 Bundle#get is deprecated, but there is no replacement for it.
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
