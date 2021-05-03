/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.runner

import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.nightly.BuildConfig
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.tracing.AndroidTracer
import com.datadog.tools.unit.invokeMethod
import io.opentracing.util.GlobalTracer
import java.util.concurrent.TimeUnit

class DatadogTestsRunner() : AndroidJUnitRunner() {

    // region Runner

    override fun start() {
        initializeDatadog()
        super.start()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        Thread.sleep(DEFAULT_TIMEOUT_IN_MS)
        Datadog.invokeMethod("stop")
        cleanStorageFiles()
        super.finish(resultCode, results)
    }

    // endregion

    // region Internal

    private fun cleanStorageFiles() {
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .filesDir.deleteRecursively()
    }

    private fun initializeDatadog() {
        Datadog.initialize(
            targetContext,
            createDatadogCredentials(),
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )
        Datadog.setVerbosity(Log.VERBOSE)
        GlobalTracer.registerIfAbsent(AndroidTracer.Builder().build())
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }

    private fun createDatadogCredentials(): Credentials {
        return Credentials(
            clientToken = BuildConfig.NIGHTLY_TESTS_TOKEN,
            envName = ENV_NAME,
            variant = "",
            rumApplicationId = BuildConfig.NIGHTLY_TESTS_RUM_APP_ID
        )
    }

    private fun createDatadogConfiguration(): Configuration {
        val configBuilder = Configuration.Builder(
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true,
            rumEnabled = true
        )
        return configBuilder.build()
    }

    // endregion

    companion object {
        const val ENV_NAME = "instrumentation"
        val DEFAULT_TIMEOUT_IN_MS = TimeUnit.MINUTES.toMillis(1)
    }
}
