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
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.ndk.NdkCrashReports
import com.datadog.android.nightly.activities.CRASH_DELAY_MS
import com.datadog.android.nightly.activities.HUNDRED_PERCENT
import com.datadog.android.nightly.utils.NeverUseThatEncryption
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration

internal open class NdkCrashService : CrashService() {

    // region Service

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            CRASH_HANDLER_DISABLED_SCENARIO -> {
                val sdkCore = startSdk(ndkCrashReportsEnabled = false)
                initRum(sdkCore, intent.extras)
                scheduleNdkCrash()
            }
            RUM_DISABLED_SCENARIO -> {
                // TODO RUMM-1554
                // NoOp for now as we could not find a way yet to assert the NDK error log
                // in the monitors
            }
            ENCRYPTION_ENABLED_SCENARIO -> {
                val sdkCore = startSdk(encryptionEnabled = true)
                initRum(sdkCore, intent.extras)
                scheduleNdkCrash()
            }
            else -> {
                val sdkCore = startSdk()
                initRum(sdkCore, intent.extras)
                scheduleNdkCrash()
            }
        }
        return START_NOT_STICKY
    }

    // endregion

    // region Internal

    private fun scheduleNdkCrash() {
        // we will give time to the RUM view event to persist before crashing the process
        Handler(Looper.getMainLooper()).postDelayed({ simulateNdkCrash() }, CRASH_DELAY_MS)
    }

    @Suppress("CheckInternal")
    private fun startSdk(
        ndkCrashReportsEnabled: Boolean = true,
        encryptionEnabled: Boolean = false,
        rumEnabled: Boolean = true
    ): SdkCore {
        val configBuilder = Configuration.Builder(
            crashReportsEnabled = true
        )
        if (encryptionEnabled) {
            configBuilder.setEncryption(NeverUseThatEncryption())
        }
        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = Datadog.initialize(
            this,
            getCredentials(),
            configBuilder.build(),
            TrackingConsent.GRANTED
        )
        checkNotNull(sdkCore)
        if (rumEnabled) {
            val rumConfig = RumConfiguration.Builder(rumApplicationId)
                .setTelemetrySampleRate(HUNDRED_PERCENT)
                .build()
            Rum.enable(rumConfig, sdkCore)
        }
        Logs.enable(LogsConfiguration.Builder().build(), sdkCore)
        Trace.enable(TraceConfiguration.Builder().build(), sdkCore)
        if (ndkCrashReportsEnabled) {
            NdkCrashReports.enable(sdkCore)
        }
        return sdkCore
    }

    // endregion

    // region Native

    private external fun simulateNdkCrash()

    // endregion

    companion object {
        init {
            System.loadLibrary("datadog-nightly-lib")
        }
    }
}
