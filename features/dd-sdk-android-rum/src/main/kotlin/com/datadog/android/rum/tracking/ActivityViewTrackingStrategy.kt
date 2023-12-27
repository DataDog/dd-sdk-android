/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import androidx.annotation.MainThread
import com.datadog.android.core.internal.thread.LoggingScheduledThreadPoolExecutor
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.utils.resolveViewName
import com.datadog.android.rum.utils.runIfValid
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A [ViewTrackingStrategy] that will track [Activity] as RUM Views.
 *
 * Each activity's lifecycle will be monitored to start and stop RUM Views when relevant.
 * @param trackExtras whether to track the Activity's Intent information (extra attributes,
 * action, data URI)
 * @param componentPredicate to accept the Activities that will be taken into account as
 * valid RUM View events.
 */
class ActivityViewTrackingStrategy
@JvmOverloads
constructor(
    internal val trackExtras: Boolean,
    internal val componentPredicate: ComponentPredicate<Activity> = AcceptAllActivities()
) :
    ActivityLifecycleTrackingStrategy(),
    ViewTrackingStrategy {

    private val executor: ScheduledExecutorService by lazy {
        LoggingScheduledThreadPoolExecutor(1, internalLogger)
    }

    // region ActivityLifecycleTrackingStrategy

    @MainThread
    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        componentPredicate.runIfValid(activity, internalLogger) {
            val viewName = componentPredicate.resolveViewName(activity)
            val attributes = if (trackExtras) {
                convertToRumAttributes(it.intent)
            } else {
                emptyMap()
            }
            getRumMonitor()?.startView(it, viewName, attributes)
        }
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        super.onActivityStopped(activity)
        executor.scheduleSafe(
            "Delayed view stop",
            STOP_VIEW_DELAY_MS,
            TimeUnit.MILLISECONDS,
            internalLogger
        ) {
            componentPredicate.runIfValid(activity, internalLogger) {
                getRumMonitor()?.stopView(it)
            }
        }
    }

    // endregion

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ActivityViewTrackingStrategy

        if (trackExtras != other.trackExtras) return false
        if (componentPredicate != other.componentPredicate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = trackExtras.hashCode()
        result = 31 * result + componentPredicate.hashCode()
        return result
    }

    // endregion

    // region Internal

    private fun getRumMonitor(): RumMonitor? {
        return withSdkCore { GlobalRumMonitor.get(it) }
    }

    // endregion

    internal companion object {
        private const val STOP_VIEW_DELAY_MS = 200L
    }
}
