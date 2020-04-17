/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.ActivityNavigator
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator
import com.datadog.android.rum.GlobalRum
import java.lang.IllegalStateException

/**
 * A [ViewTrackingStrategy] that will track [Fragment]s within a NavigationHost
 * as RUM views.
 *
 * @param navigationViewId the id of the NavHost view within the hosting [Activity].
 * @param trackArguments whether to track navigation arguments
 */
class NavigationViewTrackingStrategy(
    @IdRes private val navigationViewId: Int,
    private val trackArguments: Boolean
) :
    ActivityLifecycleTrackingStrategy(),
    ViewTrackingStrategy,
    NavController.OnDestinationChangedListener {

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        val navController = activity.findNavControllerOrNull(navigationViewId)
        navController?.addOnDestinationChangedListener(this)
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        val navController = activity.findNavControllerOrNull(navigationViewId)
        navController?.currentDestination?.let {
            GlobalRum.get().stopView(it)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        val navController = activity.findNavControllerOrNull(navigationViewId)
        navController?.removeOnDestinationChangedListener(this)
    }

    // endregion

    // region OnDestinationChangedListener

    override fun `onDestinationChanged`(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val attributes = if (trackArguments) convertToRumAttributes(arguments) else emptyMap()
        val name = destination.getRumViewName()
        GlobalRum.get().startView(destination, name, attributes)
    }

    // endregion

    // region Internal

    @Suppress("SwallowedException")
    private fun Activity.findNavControllerOrNull(@IdRes viewId: Int): NavController? {
        return try {
            findNavController(viewId)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    private fun NavDestination.getRumViewName(): String {
        return when (this) {
            is FragmentNavigator.Destination -> className
            is DialogFragmentNavigator.Destination -> className
            is ActivityNavigator.Destination -> {
                component?.className ?: UNKNOWN_DESTINATION_NAME
            }
            else -> UNKNOWN_DESTINATION_NAME
        }
    }

    // endregion

    companion object {
        internal const val UNKNOWN_DESTINATION_NAME = "Unknown"
    }
}
