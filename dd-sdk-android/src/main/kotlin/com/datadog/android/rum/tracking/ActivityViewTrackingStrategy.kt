/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import androidx.fragment.app.Fragment
import com.datadog.android.rum.GlobalRum

/**
 * A [ViewTrackingStrategy] that will track [Activity] as RUM views.
 *
 * Each activity's lifecycle will be monitored to start and stop RUM views when relevant.
 * @param trackExtras whether to track Activity Intent extras
 * @param whitelistPredicate whether to track or not a specific Activity
 */
class ActivityViewTrackingStrategy(
    private val trackExtras: Boolean,
    private val whitelistPredicate: WhitelistPredicate<Activity> = ActivityWhitelistAcceptAll()
) :
    ActivityLifecycleTrackingStrategy(),
    ViewTrackingStrategy {

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        validateActivityAndExecute(activity) {
            val javaClass = it.javaClass
            val vieName = javaClass.canonicalName ?: javaClass.simpleName
            val attributes =
                if (trackExtras) convertToRumAttributes(it.intent?.extras) else emptyMap()
            GlobalRum.monitor.startView(
                it,
                vieName,
                attributes
            )
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        validateActivityAndExecute(activity) { GlobalRum.monitor.stopView(it) }
    }

    // endregion

    // region Internal

    private fun validateActivityAndExecute(activity: Activity, operation: (Activity) -> Unit) {
        if (whitelistPredicate.accept(activity)) {
            operation(activity)
        }
    }
    // endregion
}
