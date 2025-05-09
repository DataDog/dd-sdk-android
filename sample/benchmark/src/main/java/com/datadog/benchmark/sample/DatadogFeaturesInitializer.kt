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
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.navigation.BenchmarkNavigationPredicate
import com.datadog.sample.benchmark.BuildConfig
import com.datadog.sample.benchmark.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The general recommendation is to initialize all the components at the Application.onCreate
 * to have all the observability as early as possible. However in the Benchmark app we know what features
 * we need only in [MainActivity.onCreate], it depends on the [SyntheticsScenario] which is derived from intent extras.
 */
@Singleton
@Suppress("TooManyFunctions")
internal class DatadogFeaturesInitializer @Inject constructor(
    private val sdkCore: SdkCore
) {
    private var isInitialized = false
    fun initialize(config: BenchmarkConfig) {
        if (isInitialized) {
            return
        }
        isInitialized = true

        if (needToEnableRum(config)) {
            enableRum()
        }

        if (needToEnableLogs(config)) {
            enableLogs()
        }

        if (needToEnableSessionReplay(config)) {
            enableSessionReplay()
        }

        if (needToEnableTracing(config)) {
            enableTracing()
        }
    }

    private fun needToEnableSessionReplay(config: BenchmarkConfig): Boolean {
        return isInstrumentedRun(config) && isSessionReplayScenario(config)
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

    private fun needToEnableLogs(config: BenchmarkConfig): Boolean {
        return isInstrumentedRun(config) && isLogsScenario(config)
    }

    private fun enableLogs() {
        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig, sdkCore)
    }

    private fun needToEnableRum(config: BenchmarkConfig): Boolean {
        return when (isInstrumentedRun(config)) {
            true -> isSessionReplayScenario(config) || isRumScenario(config)
            false -> isSessionReplayScenario(config)
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

    private fun isSessionReplayScenario(config: BenchmarkConfig) = when (config.scenario) {
        SyntheticsScenario.SessionReplayCompose,
        SyntheticsScenario.Upload,
        SyntheticsScenario.SessionReplay -> true
        SyntheticsScenario.Rum,
        SyntheticsScenario.Trace,
        SyntheticsScenario.LogsCustom,
        SyntheticsScenario.LogsHeavyTraffic,
        null -> false
    }

    private fun isRumScenario(config: BenchmarkConfig) = when (config.scenario) {
        SyntheticsScenario.Rum -> true
        SyntheticsScenario.SessionReplay,
        SyntheticsScenario.SessionReplayCompose,
        SyntheticsScenario.Trace,
        SyntheticsScenario.LogsCustom,
        SyntheticsScenario.LogsHeavyTraffic,
        SyntheticsScenario.Upload,
        null -> false
    }

    private fun isLogsScenario(config: BenchmarkConfig) = when (config.scenario) {
        SyntheticsScenario.LogsCustom,
        SyntheticsScenario.LogsHeavyTraffic -> true
        SyntheticsScenario.SessionReplay,
        SyntheticsScenario.SessionReplayCompose,
        SyntheticsScenario.Rum,
        SyntheticsScenario.Trace,
        SyntheticsScenario.Upload,
        null -> false
    }

    private fun isTracingScenario(config: BenchmarkConfig) = when (config.scenario) {
        SyntheticsScenario.Trace -> true
        else -> false
    }

    private fun needToEnableTracing(config: BenchmarkConfig): Boolean {
        return isInstrumentedRun(config) && isTracingScenario(config)
    }

    private fun enableTracing() {
        val tracesConfig = TraceConfiguration.Builder().build()

        Trace.enable(tracesConfig, sdkCore)
    }

    private fun isInstrumentedRun(config: BenchmarkConfig) = config.run == SyntheticsRun.Instrumented

    companion object {
        private const val SAMPLE_IN_ALL_SESSIONS = 100f
        private const val ATTR_IS_MAPPED = "is_mapped"
        private const val SAMPLE_RATE_TELEMETRY = 100f
        private const val THRESHOLD_LONG_TASK_INTERVAL = 250L
    }
}
