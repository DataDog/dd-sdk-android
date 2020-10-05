/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.datadog.android.core.internal.utils.devLogger

/**
 * The ActivityLifecycleTrackingStrategy as an [Application.ActivityLifecycleCallbacks]
 * based implementation of the [TrackingStrategy].
 */
abstract class ActivityLifecycleTrackingStrategy :
    Application.ActivityLifecycleCallbacks,
    TrackingStrategy {

    // region TrackingStrategy

    override fun register(context: Context) {
        if (context is Application) {
            context.registerActivityLifecycleCallbacks(this)
        } else {
            devLogger.e(
                "In order to use the RUM automatic tracking feature you will have" +
                    "to use the Application context when initializing the SDK"
            )
        }
    }

    override fun unregister(context: Context?) {
        if (context is Application) {
            context.unregisterActivityLifecycleCallbacks(this)
        }
    }

    // endregion

    // region Application.ActivityLifecycleCallbacks

    override fun onActivityPaused(activity: Activity) {
        // No Op
    }

    override fun onActivityStarted(activity: Activity) {
        // No Op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No Op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No Op
    }

    override fun onActivityStopped(activity: Activity) {
        // No Op
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No Op
    }

    override fun onActivityResumed(activity: Activity) {
        // No Op
    }

    // endregion

    // region Utils

    /**
     * Maps the Bundle key - value properties into compatible attributes for the Rum Events.
     * @param bundle the Bundle we need to transform. Returns an empty Map if this is null.
     */
    protected fun convertToRumAttributes(bundle: Bundle?): Map<String, Any?> {
        if (bundle == null) return emptyMap()

        val attributes = mutableMapOf<String, Any?>()

        bundle.keySet().forEach {
            attributes["$ARGUMENT_TAG.$it"] = bundle.get(it)
        }

        return attributes
    }

    // endregion

    companion object {
        internal const val ARGUMENT_TAG = "view.arguments"
    }
}
