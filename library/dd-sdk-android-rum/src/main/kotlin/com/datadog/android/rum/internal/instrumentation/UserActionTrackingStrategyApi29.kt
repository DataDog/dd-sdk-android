/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import android.app.Activity
import android.os.Bundle
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.internal.tracking.UserActionTrackingStrategy
import com.datadog.android.rum.tracking.ActivityLifecycleTrackingStrategy

internal class UserActionTrackingStrategyApi29(
    internal val gesturesTracker: GesturesTracker
) : ActivityLifecycleTrackingStrategy(),
    UserActionTrackingStrategy {

    // region UserActionTrackingStrategy

    override fun getGesturesTracker(): GesturesTracker {
        return gesturesTracker
    }

    // endregion

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        withSdkCore {
            gesturesTracker.startTracking(activity.window, activity, it)
        }
        super.onActivityPreCreated(activity, savedInstanceState)
    }

    // endregion

    // region Object

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserActionTrackingStrategyApi29

        if (gesturesTracker != other.gesturesTracker) return false

        return true
    }

    override fun hashCode(): Int {
        return gesturesTracker.hashCode()
    }

    override fun toString(): String {
        return "UserActionTrackingStrategyApi29($gesturesTracker)"
    }

    // endregion
}
