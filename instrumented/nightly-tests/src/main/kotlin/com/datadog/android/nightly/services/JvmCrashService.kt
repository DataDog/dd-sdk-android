/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.services

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.nightly.BuildConfig
import com.datadog.android.nightly.exceptions.RumDisabledException
import com.datadog.android.nightly.exceptions.RumEnabledException
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor

open class JvmCrashService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (intent.action) {
            CRASH_REPORTS_DISABLED_SCENARIO -> {
                startSdk(crashReportsEnabled = false)
                initRum(intent.extras)
                sendException(RumEnabledException())
            }
            RUM_DISABLED_SCENARIO -> {
                startSdk(rumEnabled = false)
                sendException(RumDisabledException())
            }
            else -> {
                startSdk()
                initRum(intent.extras)
                sendException(RumEnabledException())
            }
        }
        return START_NOT_STICKY
    }

    private fun sendException(exception: Exception) {
        // we will give time to the RUM view event to persist before crashing the process
        Handler(Looper.getMainLooper()).postDelayed(
            {
                throw exception
            },
            1000
        )
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
            rumEnabled = rumEnabled
        )
        Datadog.initialize(
            this,
            Credentials(
                clientToken = BuildConfig.NIGHTLY_TESTS_TOKEN,
                envName = "instrumentation",
                variant = "",
                rumApplicationId = BuildConfig.NIGHTLY_TESTS_RUM_APP_ID
            ),
            configBuilder.build(),
            TrackingConsent.GRANTED
        )
    }

    private fun initRum(extras: Bundle?) {
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
        extras?.let { bundle ->
            bundle.keySet().forEach {
                GlobalRum.addAttribute(it, bundle[it])
            }
        }
        GlobalRum.get().startView(this, this.javaClass.simpleName.toString())
    }

    companion object {
        const val CRASH_REPORTS_DISABLED_SCENARIO = "crash_reports_disabled_scenario"
        const val RUM_DISABLED_SCENARIO = "rum_disabled_scenario"
        const val RUM_ENABLED_SCENARIO = "rum_enabled_scenario"
    }
}
