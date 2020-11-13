/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.utils.addExtras
import com.datadog.tools.unit.getFieldValue
import kotlin.collections.ArrayList

internal open class RumMockServerActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent = TrackingConsent.PENDING,
    private val intentExtras: Map<String, Any?> = emptyMap()
) : MockServerActivityTestRule<T>(activityClass, keepRequests, trackingConsent) {

    // region ActivityTestRule

    override fun beforeActivityLaunched() {
        removeRumCallbacks()
        super.beforeActivityLaunched()
    }

    override fun afterActivityFinished() {
        removeRumCallbacks()
        super.afterActivityFinished()
    }

    override fun getActivityIntent(): Intent {
        return super.getActivityIntent().apply { addExtras(intentExtras) }
    }

    // endregion

    // region utils

    fun performOnLifecycleCallbacks(action: (Application.ActivityLifecycleCallbacks) -> Unit) {
        val application =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .applicationContext as Application
        val lifecycleCallbacks: ArrayList<Application.ActivityLifecycleCallbacks> =
            application.getFieldValue(
                "mActivityLifecycleCallbacks",
                Application::class.java
            )

        lifecycleCallbacks.forEach(action)
    }

    // endregion

    // region Internal

    private fun removeRumCallbacks() {
        val application =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .applicationContext as Application
        val lifecycleCallbacks: ArrayList<Application.ActivityLifecycleCallbacks> =
            application.getFieldValue(
                "mActivityLifecycleCallbacks",
                Application::class.java
            )
        val activityViewTrackingStrategyClass = ActivityViewTrackingStrategy::class.java
        // we need this because the lifecycle callbacks are modified concurrently from other
        // unfinished tests
        // (it can happen that one Espresso test to start while other it is about to start)
        synchronized(lifecycleCallbacks) {
            var pointer = lifecycleCallbacks.size - 1
            while (pointer >= 0) {
                val callback = lifecycleCallbacks[pointer]
                if (activityViewTrackingStrategyClass.isAssignableFrom(callback::class.java)) {
                    application.unregisterActivityLifecycleCallbacks(callback)
                    pointer--
                }
                pointer--
            }
        }
    }

    // endregion
}
