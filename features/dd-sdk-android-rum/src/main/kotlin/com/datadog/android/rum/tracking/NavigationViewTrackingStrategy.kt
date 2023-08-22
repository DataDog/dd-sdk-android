/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.datadog.android.api.feature.Feature
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.tracking.AndroidXFragmentLifecycleCallbacks
import com.datadog.android.rum.utils.resolveViewName
import com.datadog.android.rum.utils.runIfValid
import java.lang.IllegalStateException
import java.util.WeakHashMap

/**
 * A [ViewTrackingStrategy] that will track [Fragment]s within a NavigationHost
 * as RUM Views.
 *
 * @param navigationViewId the id of the NavHost view within the hosting [Activity].
 * @param trackArguments whether to track navigation arguments
 * @param componentPredicate the predicate to keep/discard/rename the tracked [NavDestination]s
 */
class NavigationViewTrackingStrategy(
    @IdRes private val navigationViewId: Int,
    private val trackArguments: Boolean,
    private val componentPredicate: ComponentPredicate<NavDestination> = AcceptAllNavDestinations()
) :
    ActivityLifecycleTrackingStrategy(),
    ViewTrackingStrategy,
    NavController.OnDestinationChangedListener {

    internal data class NavigationKey(
        val controller: NavController,
        val destination: NavDestination
    )

    private var startedActivity: Activity? = null

    private var lifecycleCallbackRefs =
        WeakHashMap<Activity, NavControllerFragmentLifecycleCallbacks>()

    private val predicate: ComponentPredicate<Fragment> = object : ComponentPredicate<Fragment> {
        override fun accept(component: Fragment): Boolean {
            return !NavHostFragment::class.java.isAssignableFrom(component.javaClass)
        }

        override fun getViewName(component: Fragment): String? {
            return null
        }
    }

    // region ActivityLifecycleTrackingStrategy

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        startedActivity = activity
        startTracking()
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        stopTracking()
        startedActivity = null
    }

    @MainThread
    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        val rumMonitor = withSdkCore { GlobalRumMonitor.get(it) }
        activity.findNavControllerOrNull(navigationViewId)?.currentDestination?.let {
            rumMonitor?.stopView(it)
        }
    }

    // endregion

    // region OnDestinationChangedListener

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val rumMonitor = withSdkCore { GlobalRumMonitor.get(it) }
        componentPredicate.runIfValid(destination, internalLogger) {
            val attributes = if (trackArguments) convertToRumAttributes(arguments) else emptyMap()
            val viewName = componentPredicate.resolveViewName(destination)
            rumMonitor?.startView(NavigationKey(controller, destination), viewName, attributes)
        }
    }

    // endregion

    // region Setup

    /**
     * Starts tracking on current activity.
     *
     * This is automatically called when activity starts. If using static navigation setup, with
     * navigation container in XML layout, there's no need to call it manually. However if using
     * dynamic navigation setup where navigation controller is created programmatically, then this
     * function must be called after navigation controller is injected into view hierarchy.
     * Regardless of the usage, the function always relies on view ID provided with the constructor.
     *
     * If activity is stopped, the function will return immediately without starting tracking.
     */
    fun startTracking() {
        val activity = startedActivity ?: return

        withSdkCore { sdkCore ->
            val rumFeature = sdkCore
                .getFeature(Feature.RUM_FEATURE_NAME)
                ?.unwrap<RumFeature>()
            val fragmentActivity = activity as? FragmentActivity
            val navController = activity.findNavControllerOrNull(navigationViewId)
            if (fragmentActivity != null && navController != null && rumFeature != null) {
                val navControllerFragmentCallbacks = NavControllerFragmentLifecycleCallbacks(
                    navController,
                    argumentsProvider = { emptyMap() },
                    componentPredicate = predicate,
                    rumFeature = rumFeature
                )
                navControllerFragmentCallbacks.register(
                    startedActivity as FragmentActivity,
                    sdkCore
                )
                lifecycleCallbackRefs[startedActivity] = navControllerFragmentCallbacks
                navController.addOnDestinationChangedListener(this)
            }
        }
    }

    /**
     * Stops tracking current activity.
     *
     * This is automatically called when activity stops. If using static navigation setup, with
     * navigation container in XML layout, there's no need to call it manually. Even with dynamic
     * navigation setup where navigation controller is created programmatically, default behavior
     * should be enough. But the function is here in case tracking needs to be managed outside of
     * the default activity lifecycle. In this case note that it's possible to
     * call [startTracking] and [stopTracking] multiple times in succession.
     *
     * If activity is stopped, the function will return immediately.
     */
    fun stopTracking() {
        val activity = startedActivity ?: return
        activity.findNavControllerOrNull(navigationViewId)?.let {
            it.removeOnDestinationChangedListener(this)
            if (FragmentActivity::class.java.isAssignableFrom(activity::class.java)) {
                lifecycleCallbackRefs.remove(activity)?.unregister(activity as FragmentActivity)
            }
        }
    }

    // endregion

    // region Internal

    @Suppress("SwallowedException")
    private fun Activity.findNavControllerOrNull(@IdRes viewId: Int): NavController? {
        return try {
            val navController = if (this is FragmentActivity) {
                findNavControllerFromNavHostFragmentOrNull(viewId)
            } else {
                null
            }
            navController ?: Navigation.findNavController(this, viewId)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    private fun FragmentActivity.findNavControllerFromNavHostFragmentOrNull(
        @IdRes viewId: Int
    ): NavController? {
        val navHostFragment = supportFragmentManager.findFragmentById(viewId) as? NavHostFragment
        return navHostFragment?.navController
    }

    // endregion

    // region Internal

    // endregion

    internal class NavControllerFragmentLifecycleCallbacks(
        private val navController: NavController,
        argumentsProvider: (Fragment) -> Map<String, Any?>,
        componentPredicate: ComponentPredicate<Fragment>,
        rumFeature: RumFeature
    ) : AndroidXFragmentLifecycleCallbacks(
        argumentsProvider,
        componentPredicate,
        rumMonitor = NoOpRumMonitor(),
        rumFeature = rumFeature
    ) {
        override fun resolveKey(fragment: Fragment): Any {
            return navController.currentDestination ?: NO_DESTINATION_FOUND
        }

        companion object {
            val NO_DESTINATION_FOUND = Any()
        }
    }
}
