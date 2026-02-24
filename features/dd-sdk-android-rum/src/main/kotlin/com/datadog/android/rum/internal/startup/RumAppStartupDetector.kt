/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.app.Application
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.asTimeNs
import com.datadog.android.rum.startup.AppStartupActivityPredicate

internal interface RumAppStartupDetector {
    interface Listener {
        /**
         * Called when a startup scenario is detected.
         * @return true if the listener successfully subscribed to the first frame draw,
         * false if it was unable to (e.g. activity already GC'd, RUM monitor unavailable).
         * The detector uses this to decide whether to keep or discard pendingScenario.
         */
        fun onAppStartupDetected(scenario: RumStartupScenario): Boolean
        fun onNextActivityCreated(pendingScenario: RumStartupScenario, activity: Activity)
    }

    fun destroy()
    fun getPendingScenario(): RumStartupScenario?
    fun clearPendingScenario()

    companion object {
        fun create(
            application: Application,
            sdkCore: InternalSdkCore,
            listener: Listener,
            appStartupActivityPredicate: AppStartupActivityPredicate
        ): RumAppStartupDetector {
            return RumAppStartupDetectorImpl(
                application = application,
                buildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
                appStartupTimeProvider = { sdkCore.appStartTimeNs.asTimeNs() },
                timeProvider = { Time() },
                listener = listener,
                appStartupActivityPredicate = appStartupActivityPredicate
            )
        }
    }
}
