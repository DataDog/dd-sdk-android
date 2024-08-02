/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.Activity
import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.tools.unit.getFieldValue

internal open class LifecycleCallbackTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent = TrackingConsent.PENDING
) : MockServerActivityTestRule<T>(activityClass, keepRequests, trackingConsent) {

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

    internal fun removeCallbacks(callbacksClasses: List<Class<*>>) {
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
        // we need this because the lifecycle callbacks are modified concurrently from other
        // unfinished tests
        // (it can happen that one Espresso test to start while other it is about to start)
        synchronized(lifecycleCallbacks) {
            var pointer = lifecycleCallbacks.size - 1
            while (pointer >= 0) {
                val callback = lifecycleCallbacks[pointer]
                if (callbacksClasses.firstOrNull { it.isAssignableFrom(callback::class.java) } != null) {
                    application.unregisterActivityLifecycleCallbacks(callback)
                    pointer--
                }
                pointer--
            }
        }
    }

    // endregion
}
