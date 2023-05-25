/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.os.Build
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.datadog.android.Datadog
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.NoOpAdvancedRumMonitor
import com.datadog.android.rum.internal.tracking.AndroidXFragmentLifecycleCallbacks
import com.datadog.android.rum.internal.tracking.FragmentLifecycleCallbacks
import com.datadog.android.rum.internal.tracking.NoOpFragmentLifecycleCallbacks
import com.datadog.android.rum.internal.tracking.OreoFragmentLifecycleCallbacks
import com.datadog.android.v2.core.DatadogCore

/**
 * A [ViewTrackingStrategy] that will track [Fragment]s as RUM Views.
 *
 * Each fragment's lifecycle will be monitored to start and stop RUM Views when relevant.
 *
 * **Note**: This version of the [FragmentViewTrackingStrategy] is compatible with
 * the AndroidX Compat Library.
 * @param trackArguments whether we track Fragment arguments
 * @param supportFragmentComponentPredicate to accept the Androidx Fragments
 * that will be taken into account as valid RUM View events.
 * @param defaultFragmentComponentPredicate to accept the default Android Fragments
 * that will be taken into account as valid RUM View events.
 */
@Suppress("DEPRECATION")
class FragmentViewTrackingStrategy @JvmOverloads constructor(
    internal val trackArguments: Boolean,
    internal val supportFragmentComponentPredicate: ComponentPredicate<Fragment> =
        AcceptAllSupportFragments(),
    internal val defaultFragmentComponentPredicate: ComponentPredicate<android.app.Fragment> =
        AcceptAllDefaultFragment()
) :
    ActivityLifecycleTrackingStrategy(),
    ViewTrackingStrategy {

    private val androidXLifecycleCallbacks: FragmentLifecycleCallbacks<FragmentActivity>
        by lazy {
            val rumFeature = (Datadog.globalSdkCore as? DatadogCore)?.rumFeature
            if (rumFeature != null) {
                AndroidXFragmentLifecycleCallbacks(
                    argumentsProvider = {
                        if (trackArguments) convertToRumAttributes(it.arguments) else emptyMap()
                    },
                    componentPredicate = supportFragmentComponentPredicate,
                    rumMonitor = GlobalRum.get(),
                    advancedRumMonitor = GlobalRum.get() as? AdvancedRumMonitor
                        ?: NoOpAdvancedRumMonitor(),
                    rumFeature = rumFeature
                )
            } else {
                NoOpFragmentLifecycleCallbacks()
            }
        }
    private val oreoLifecycleCallbacks: FragmentLifecycleCallbacks<Activity>
        by lazy {
            val rumFeature = (Datadog.globalSdkCore as? DatadogCore)?.rumFeature
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && rumFeature != null) {
                OreoFragmentLifecycleCallbacks(
                    argumentsProvider = {
                        if (trackArguments) convertToRumAttributes(it.arguments) else emptyMap()
                    },
                    componentPredicate = defaultFragmentComponentPredicate,
                    rumMonitor = GlobalRum.get(),
                    advancedRumMonitor = GlobalRum.get() as? AdvancedRumMonitor
                        ?: NoOpAdvancedRumMonitor(),
                    rumFeature = rumFeature
                )
            } else {
                NoOpFragmentLifecycleCallbacks()
            }
        }

    // region ActivityLifecycleTrackingStrategy

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        if (FragmentActivity::class.java.isAssignableFrom(activity::class.java)) {
            androidXLifecycleCallbacks.register(activity as FragmentActivity)
        } else {
            // old deprecated way
            oreoLifecycleCallbacks.register(activity)
        }
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        if (FragmentActivity::class.java.isAssignableFrom(activity::class.java)) {
            androidXLifecycleCallbacks.unregister(activity as FragmentActivity)
        } else {
            // old deprecated way
            oreoLifecycleCallbacks.unregister(activity)
        }
    }

    // endregion

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FragmentViewTrackingStrategy

        if (trackArguments != other.trackArguments) return false
        if (supportFragmentComponentPredicate != other.supportFragmentComponentPredicate) {
            return false
        }
        if (defaultFragmentComponentPredicate != other.defaultFragmentComponentPredicate) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = trackArguments.hashCode()
        result = 31 * result + supportFragmentComponentPredicate.hashCode()
        result = 31 * result + defaultFragmentComponentPredicate.hashCode()
        return result
    }

    // endregion
}
