/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.services

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.nightly.exceptions.RumDisabledException
import com.datadog.android.nightly.exceptions.RumEnabledException
import com.datadog.android.privacy.TrackingConsent

internal open class JvmCrashService : CrashService() {

    // region Service

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            CRASH_HANDLER_DISABLED_SCENARIO -> {
                startSdk(crashReportsEnabled = false)
                initRum(intent.extras)
                scheduleJvmCrash(RumEnabledException())
            }
            RUM_DISABLED_SCENARIO -> {
                startSdk(rumEnabled = false)
                scheduleJvmCrash(RumDisabledException())
            }
            else -> {
                startSdk()
                initRum(intent.extras)
                scheduleJvmCrash(RumEnabledException())
            }
        }
        return START_NOT_STICKY
    }

    // endregion

    // region Internal

    @SuppressWarnings("ThrowingInternalException")
    private fun scheduleJvmCrash(exception: Exception) {
        // we will give time to the RUM view event to persist before crashing the process
        Handler(Looper.getMainLooper()).postDelayed({ throw exception }, 1000)
    }

    private fun startSdk(
        crashReportsEnabled: Boolean = true,
        rumEnabled: Boolean = true
    ) {
        Datadog.setVerbosity(Log.VERBOSE)
        val configBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = crashReportsEnabled,
            rumEnabled = rumEnabled,
            sessionReplayEnabled = true
        ).sampleTelemetry(100f)
        Datadog.initialize(
            this,
            getCredentials(),
            configBuilder.build(),
            TrackingConsent.GRANTED
        )
    }

    // endregion
}
