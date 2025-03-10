/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.core.internal.attributes.enrichWithConstantAttribute
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.utils.resolveViewName
import com.datadog.android.rum.internal.utils.runIfValid
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
        sdkCore.createScheduledExecutorService(
            "rum-activity-tracking"
        )
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

    /**
     * Maps the Bundle key - value properties into compatible attributes for the Rum Events.
     * @param intent the [Intent] we need to transform. Returns an empty Map if this is null.
     */
    private fun convertToRumAttributes(intent: Intent?): Map<String, Any?> {
        if (intent == null) return mutableMapOf()

        val attributes = mutableMapOf<String, Any?>()

        intent.action?.let {
            attributes[INTENT_ACTION_TAG] = it
        }
        intent.dataString?.let {
            attributes[INTENT_URI_TAG] = it
        }

        attributes.putAll(intent.safeExtras.convertToRumViewAttributes())
        attributes.enrichWithConstantAttribute(ViewScopeInstrumentationType.ACTIVITY)

        return attributes
    }

    private val Intent.safeExtras: Bundle?
        get() = try {
            // old Androids can throw different exceptions here making native calls
            extras
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Error getting Intent extras, ignoring it." },
                e
            )
            null
        }

    // endregion

    internal companion object {
        private const val STOP_VIEW_DELAY_MS = 200L

        internal const val INTENT_ACTION_TAG = "view.intent.action"
        internal const val INTENT_URI_TAG = "view.intent.uri"
    }
}
