/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import com.datadog.android.api.SdkCore
import com.datadog.android.compose.enableComposeActionTracking
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.ComposeExtensionSupport
import com.datadog.android.sessionreplay.material.MaterialExtensionSupport
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.di.activity.BenchmarkActivityScope
import com.datadog.benchmark.sample.navigation.BenchmarkNavigationPredicate
import com.datadog.sample.benchmark.BuildConfig
import com.datadog.sample.benchmark.R
import javax.inject.Inject

@BenchmarkActivityScope
@Suppress("TooManyFunctions")
internal class DatadogFeaturesInitializer @Inject constructor(
    private val sdkCore: SdkCore,
    private val config: BenchmarkConfig
) {
    fun initialize() {
        if (needToEnableRum()) {
            enableRum()
        }

        if (needToEnableLogs()) {
            enableLogs()
        }

        if (needToEnableSessionReplay()) {
            enableSessionReplay()
        }
    }

    private fun needToEnableSessionReplay(): Boolean {
        return isInstrumentedRun() && isSessionReplayScenario()
    }

    @Suppress("DEPRECATION")
    private fun enableSessionReplay() {
        val sessionReplayConfig = SessionReplayConfiguration
            .Builder(SAMPLE_IN_ALL_SESSIONS)
            .setPrivacy(SessionReplayPrivacy.ALLOW)
            .addExtensionSupport(MaterialExtensionSupport())
            .addExtensionSupport(ComposeExtensionSupport())
            .build()

        SessionReplay.enable(sessionReplayConfig, sdkCore)
    }

    private fun needToEnableLogs(): Boolean {
        return isInstrumentedRun() && isLogsScenario()
    }

    private fun enableLogs() {
        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig, sdkCore)
    }

    private fun needToEnableRum(): Boolean {
        return when (isInstrumentedRun()) {
            true -> isSessionReplayScenario() || isRumScenario()
            false -> isSessionReplayScenario()
        }
    }

    private fun createRumConfiguration(): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.BENCHMARK_RUM_APPLICATION_ID)
            .useViewTrackingStrategy(
                NavigationViewTrackingStrategy(
                    R.id.nav_host_fragment,
                    true,
                    BenchmarkNavigationPredicate()
                )
            )
            .setTelemetrySampleRate(SAMPLE_RATE_TELEMETRY)
            .trackUserInteractions()
            .trackLongTasks(THRESHOLD_LONG_TASK_INTERVAL)
            .trackNonFatalAnrs(true)
            .setViewEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setActionEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setResourceEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setErrorEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .setLongTaskEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            .enableComposeActionTracking()
            .build()
    }

    private fun enableRum() {
        val rumConfig = createRumConfiguration()
        Rum.enable(rumConfig, sdkCore = sdkCore)
    }

    private fun isSessionReplayScenario() = when (config.scenario) {
        SyntheticsScenario.SessionReplayCompose,
        SyntheticsScenario.SessionReplay -> true
        else -> false
    }

    private fun isRumScenario() = when (config.scenario) {
        SyntheticsScenario.Rum -> true
        else -> false
    }

    private fun isLogsScenario() = when (config.scenario) {
        SyntheticsScenario.LogsCustom,
        SyntheticsScenario.LogsHeavyTraffic -> true
        else -> false
    }

    private fun isInstrumentedRun() = config.run == SyntheticsRun.Instrumented

    companion object {
        private const val SAMPLE_IN_ALL_SESSIONS = 100f
        private const val ATTR_IS_MAPPED = "is_mapped"
        private const val SAMPLE_RATE_TELEMETRY = 100f
        private const val THRESHOLD_LONG_TASK_INTERVAL = 250L
    }
}
