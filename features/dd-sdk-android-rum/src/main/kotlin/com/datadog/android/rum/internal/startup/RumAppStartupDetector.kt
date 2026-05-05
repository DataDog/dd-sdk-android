/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Application
import androidx.annotation.UiThread
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.asTimeNs
import com.datadog.android.rum.startup.AppStartupActivityPredicate

internal interface RumAppStartupDetector {
    interface Listener {
        /**
         * Called when a startup scenario is detected.
         */
        fun onAppStartupDetected(scenario: RumStartupScenario)
        fun onTTIDComputed(scenario: RumStartupScenario, durationNs: Long, wasForwarded: Boolean)
    }

    @UiThread
    fun destroy()

    companion object {
        fun create(
            application: Application,
            sdkCore: InternalSdkCore,
            listener: Listener,
            appStartupActivityPredicate: AppStartupActivityPredicate
        ): RumAppStartupDetector {
            val rumFirstDrawTimeReporter = RumFirstDrawTimeReporter.create(sdkCore = sdkCore)

            return RumAppStartupDetectorImpl(
                application = application,
                buildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
                appStartupTimeProvider = { sdkCore.appStartTimeNs.asTimeNs() },
                timeProvider = { Time() },
                listener = listener,
                appStartupActivityPredicate = appStartupActivityPredicate,
                rumFirstDrawTimeReporter = rumFirstDrawTimeReporter
            )
        }
    }
}
