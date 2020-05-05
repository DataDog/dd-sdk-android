/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.os.Bundle
import androidx.fragment.app.Fragment

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
 * @param trackExtras whether to track Activity Intent extras or the Fragment arguments.
 * @param componentPredicate to accept the Activities that will be taken into account as
 * valid RUM View events.
 * @param supportFragmentComponentPredicate to accept the Androidx Fragments
 * that will be taken into account as valid RUM View events.
 * @param defaultFragmentComponentPredicate to accept the default Android Fragments
 * that will be taken into account as valid RUM View events.
 */
class MixedViewTrackingStrategy internal constructor(
    private val activityViewTrackingStrategy: ActivityViewTrackingStrategy,
    private val fragmentViewTrackingStrategy: FragmentViewTrackingStrategy
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

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        super.onActivityCreated(activity, savedInstanceState)
        activityViewTrackingStrategy.onActivityCreated(activity, savedInstanceState)
        fragmentViewTrackingStrategy.onActivityCreated(activity, savedInstanceState)
    }

    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        activityViewTrackingStrategy.onActivityStarted(activity)
        fragmentViewTrackingStrategy.onActivityStarted(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        activityViewTrackingStrategy.onActivityResumed(activity)
        fragmentViewTrackingStrategy.onActivityResumed(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        activityViewTrackingStrategy.onActivityPaused(activity)
        fragmentViewTrackingStrategy.onActivityPaused(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        activityViewTrackingStrategy.onActivityStopped(activity)
        fragmentViewTrackingStrategy.onActivityStopped(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        super.onActivityDestroyed(activity)
        activityViewTrackingStrategy.onActivityDestroyed(activity)
        fragmentViewTrackingStrategy.onActivityDestroyed(activity)
    }

    // endregion
}
