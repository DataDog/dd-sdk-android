/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import android.app.Activity
import android.os.Bundle
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.tracking.ActivityLifecycleTrackingStrategy
import com.datadog.android.rum.tracking.UserActionTrackingStrategy

internal class GesturesTrackingStrategy(
    internal val gesturesTracker: GesturesTracker
) : ActivityLifecycleTrackingStrategy(),
    UserActionTrackingStrategy {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        gesturesTracker.startTracking(activity)
        super.onActivityPreCreated(activity, savedInstanceState)
    }
}
