/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import com.datadog.android.api.SdkCore

/**
 * A [ViewTrackingStrategy] that will track [Activity] and [Fragment] as RUM View Events.
 * This strategy will apply both the [ActivityViewTrackingStrategy]
 * and the [FragmentViewTrackingStrategy] and will remain for you to decide whether to exclude
 * some activities or fragments from tracking by providing an implementation for the right
 * predicate in the constructor arguments.
 * @see ActivityViewTrackingStrategy
 * @see FragmentViewTrackingStrategy
 * **Note**: This version of the [MixedViewTrackingStrategy] is compatible with
 * the AndroidX Compat Library.
 * @param activityViewTrackingStrategy Strategy to track Activities.
 * @param fragmentViewTrackingStrategy Strategy to track Fragments.
 */
@Suppress("DEPRECATION")
class MixedViewTrackingStrategy internal constructor(
    internal val activityViewTrackingStrategy: ActivityViewTrackingStrategy,
    internal val fragmentViewTrackingStrategy: FragmentViewTrackingStrategy
) : ActivityLifecycleTrackingStrategy(),
    ViewTrackingStrategy {

    @JvmOverloads
    constructor(
        trackExtras: Boolean,
        componentPredicate: ComponentPredicate<Activity> = AcceptAllActivities(),
        supportFragmentComponentPredicate: ComponentPredicate<Fragment> =
            AcceptAllSupportFragments(),
        defaultFragmentComponentPredicate: ComponentPredicate<android.app.Fragment> =
            AcceptAllDefaultFragment()
    ) : this(
        ActivityViewTrackingStrategy(trackExtras, componentPredicate),
        FragmentViewTrackingStrategy(
            trackExtras,
            supportFragmentComponentPredicate,
            defaultFragmentComponentPredicate
        )
    )

    override fun register(sdkCore: SdkCore, context: Context) {
        super.register(sdkCore, context)
        activityViewTrackingStrategy.register(sdkCore, context)
        fragmentViewTrackingStrategy.register(sdkCore, context)
    }

    override fun unregister(context: Context?) {
        activityViewTrackingStrategy.unregister(context)
        fragmentViewTrackingStrategy.unregister(context)
        super.unregister(context)
    }

    // region ActivityLifecycleTrackingStrategy

    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        super.onActivityCreated(activity, savedInstanceState)
        activityViewTrackingStrategy.onActivityCreated(activity, savedInstanceState)
        fragmentViewTrackingStrategy.onActivityCreated(activity, savedInstanceState)
    }

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        activityViewTrackingStrategy.onActivityStarted(activity)
        fragmentViewTrackingStrategy.onActivityStarted(activity)
    }

    @MainThread
    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        activityViewTrackingStrategy.onActivityResumed(activity)
        fragmentViewTrackingStrategy.onActivityResumed(activity)
    }

    @MainThread
    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        activityViewTrackingStrategy.onActivityPaused(activity)
        fragmentViewTrackingStrategy.onActivityPaused(activity)
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        activityViewTrackingStrategy.onActivityStopped(activity)
        fragmentViewTrackingStrategy.onActivityStopped(activity)
    }

    @MainThread
    override fun onActivityDestroyed(activity: Activity) {
        super.onActivityDestroyed(activity)
        activityViewTrackingStrategy.onActivityDestroyed(activity)
        fragmentViewTrackingStrategy.onActivityDestroyed(activity)
    }

    // endregion

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MixedViewTrackingStrategy

        if (activityViewTrackingStrategy != other.activityViewTrackingStrategy) return false
        if (fragmentViewTrackingStrategy != other.fragmentViewTrackingStrategy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = activityViewTrackingStrategy.hashCode()
        result = 31 * result + fragmentViewTrackingStrategy.hashCode()
        return result
    }

    // endregion
}
