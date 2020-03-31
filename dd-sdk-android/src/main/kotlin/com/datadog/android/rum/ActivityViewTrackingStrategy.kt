/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity

/**
 * A [ViewTrackingStrategy] that will track [Activity] as RUM views.
 *
 * Each activity's lifecycle will be monitored to start and stop RUM views when relevant.
 * @param trackExtras whether to track Activity Intent extras
 */
class ActivityViewTrackingStrategy(private val trackExtras: Boolean) :
    ActivityLifecycleTrackingStrategy(), ViewTrackingStrategy {

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        val javaClass = activity.javaClass
        val vieName = javaClass.canonicalName ?: javaClass.simpleName
        val attributes =
            if (trackExtras) convertToRumAttributes(activity.intent?.extras) else emptyMap()
        GlobalRum.monitor.startView(
            activity,
            vieName,
            attributes
        )
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        GlobalRum.monitor.stopView(activity)
    }

    // endregion
}
