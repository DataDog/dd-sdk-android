/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.datadog.android.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.utils.addExtras

internal open class RumMockServerActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent = TrackingConsent.PENDING,
    private val intentExtras: Map<String, Any?> = emptyMap()
) : LifecycleCallbackTestRule<T>(activityClass, keepRequests, trackingConsent) {

    // region ActivityTestRule

    private val callbackClasses: List<Class<out Application.ActivityLifecycleCallbacks>> = listOf(
        ActivityViewTrackingStrategy::class.java,
        ProcessLifecycleMonitor::class.java
    )

    override fun beforeActivityLaunched() {
        removeCallbacks(callbackClasses)
        super.beforeActivityLaunched()
    }

    override fun afterActivityFinished() {
        removeCallbacks(callbackClasses)
        super.afterActivityFinished()
    }

    override fun getActivityIntent(): Intent {
        return super.getActivityIntent().apply { addExtras(intentExtras) }
    }

    // endregion
}
