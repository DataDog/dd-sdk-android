/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.os.Bundle
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.tracking.ViewLoadingTimer
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.resolveViewName
import com.datadog.android.rum.utils.runIfValid

/**
 * A [ViewTrackingStrategy] that will track [Activity] as RUM Views.
 *
 * Each activity's lifecycle will be monitored to start and stop RUM Views when relevant.
 * @param trackExtras whether to track the Activity's Intent information (extra attributes,
 * action, data URI)
 * @param componentPredicate to accept the Activities that will be taken into account as
 * valid RUM View events.
 */
class ActivityViewTrackingStrategy @JvmOverloads constructor(
    internal val trackExtras: Boolean,
    internal val componentPredicate: ComponentPredicate<Activity> = AcceptAllActivities()
) :
    ActivityLifecycleTrackingStrategy(),
    ViewTrackingStrategy {

    internal var viewLoadingTimer = ViewLoadingTimer()

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        super.onActivityCreated(activity, savedInstanceState)
        componentPredicate.runIfValid(activity, internalLogger) {
            viewLoadingTimer.onCreated(it)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        componentPredicate.runIfValid(activity, internalLogger) {
            viewLoadingTimer.onStartLoading(it)
        }
    }

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
            // we still need to call onFinishedLoading here for API bellow 29 as the
            // onPostResumed is not available on these devices.
            viewLoadingTimer.onFinishedLoading(it)
        }
    }

    override fun onActivityPostResumed(activity: Activity) {
        // this method doesn't call super, because having super call creates a crash
        // during DD SDK initialization on KitKat with ProGuard enabled, default super is
        // empty anyway
        // this method is only available from API 29 and above
        componentPredicate.runIfValid(activity, internalLogger) {
            viewLoadingTimer.onFinishedLoading(it)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        componentPredicate.runIfValid(activity, internalLogger) {
            updateLoadingTime(activity)
            getRumMonitor()?.stopView(it)
            viewLoadingTimer.onPaused(activity)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        super.onActivityDestroyed(activity)
        componentPredicate.runIfValid(activity, internalLogger) {
            viewLoadingTimer.onDestroyed(it)
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
        return withSdkCore { GlobalRum.get(it) }
    }

    private fun getAdvancedRumMonitor(): AdvancedRumMonitor? {
        return getRumMonitor() as? AdvancedRumMonitor
    }

    private fun updateLoadingTime(activity: Activity) {
        viewLoadingTimer.getLoadingTime(activity)?.let { loadingTime ->
            getAdvancedRumMonitor()?.let { monitor ->
                val loadingType = resolveLoadingType(viewLoadingTimer.isFirstTimeLoading(activity))
                monitor.updateViewLoadingTime(
                    activity,
                    loadingTime,
                    loadingType
                )
            }
        }
    }

    private fun resolveLoadingType(firstTimeLoading: Boolean): ViewEvent.LoadingType {
        return if (firstTimeLoading) {
            ViewEvent.LoadingType.ACTIVITY_DISPLAY
        } else {
            ViewEvent.LoadingType.ACTIVITY_REDISPLAY
        }
    }

    // endregion
}
