/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Activity
import android.app.Application
import androidx.annotation.UiThread
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.internal.domain.Time

interface RumAppStartupDetector {
    interface Listener {
        /**
         * Called when a startup scenario is detected.
         */
        fun onAppStartupDetected(scenario: RumStartupScenario)
        fun onTTIDComputed(scenario: RumStartupScenario, durationNs: Long, wasForwarded: Boolean)
    }

    fun interface ActivityPredicate {
        fun shouldTrackStartup(activity: Activity): Boolean
    }

    fun interface WarningLogger {
        fun logWarning(message: String, throwable: Throwable?)
    }

    @UiThread
    fun destroy()

    companion object {
        fun create(
            application: Application,
            appStartTimeNs: Long,
            timeProvider: TimeProvider,
            listener: Listener,
            activityPredicate: ActivityPredicate,
            warningLogger: WarningLogger
        ): RumAppStartupDetector {
            val rumFirstDrawTimeReporter = RumFirstDrawTimeReporter.create(
                timeProvider = timeProvider,
                warningLogger = warningLogger
            )

            return RumAppStartupDetectorImpl(
                application = application,
                buildSdkVersionProvider = BuildSdkVersionProvider.DEFAULT,
                appStartupTime = { Time.fromNanoTime(appStartTimeNs, timeProvider) },
                currentTime = { Time.now(timeProvider) },
                listener = listener,
                appStartupActivityPredicate = activityPredicate,
                rumFirstDrawTimeReporter = rumFirstDrawTimeReporter
            )
        }
    }
}
