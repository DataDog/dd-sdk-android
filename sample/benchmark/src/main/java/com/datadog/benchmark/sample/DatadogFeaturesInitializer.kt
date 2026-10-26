/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.content.Intent
import android.os.Build
import com.datadog.android.api.SdkCore
import com.datadog.android.compose.enableComposeActionTracking
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.Profiling
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.timeseries.TimeseriesConfiguration
import com.datadog.android.rum.tracking.NavigationViewTrackingStrategy
import com.datadog.android.rum.tracking.ViewTrackingStrategy
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.ComposeExtensionSupport
import com.datadog.android.sessionreplay.material.MaterialExtensionSupport
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.benchmark.sample.config.BenchmarkConfig
import com.datadog.benchmark.sample.config.SyntheticsScenario
import com.datadog.benchmark.sample.config.isInstrumentedRun
import com.datadog.benchmark.sample.config.isLogsScenario
import com.datadog.benchmark.sample.config.isProfilingScenario
import com.datadog.benchmark.sample.config.isRumScenario
import com.datadog.benchmark.sample.config.isSessionReplayScenario
import com.datadog.benchmark.sample.config.isTimeseriesScenario
import com.datadog.benchmark.sample.config.isTracingScenario
import com.datadog.benchmark.sample.navigation.BenchmarkNavigationPredicate
import com.datadog.sample.benchmark.BuildConfig
import com.datadog.sample.benchmark.R
import dagger.Lazy
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
    private val sdkCore: Lazy<SdkCore>,
    private val rumMonitor: Lazy<RumMonitor>
) {
    private var isInitialized = false

    fun initialize(config: BenchmarkConfig, intent: Intent) {
        if (isInitialized) {
            return
        }
        isInitialized = true

        if (needToEnableRum(config)) {
            enableRum(config = config, intent = intent)
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

        if (needToEnableProfiling(config)) {
            enableProfiling()
        }
    }

    private fun needToEnableProfiling(config: BenchmarkConfig): Boolean {
        return config.isInstrumentedRun() && config.isProfilingScenario()
    }

    @OptIn(ExperimentalProfilingApi::class)
    private fun enableProfiling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val profilingConfig = ProfilingConfiguration.Builder()
                .setContinuousSampleRate(SAMPLE_IN_ALL_SESSIONS)
                .setApplicationLaunchSampleRate(0f)
                .build()
            Profiling.enable(profilingConfig, sdkCore.get())
        }
    }

    private fun needToEnableSessionReplay(config: BenchmarkConfig): Boolean {
        return config.isInstrumentedRun() && config.isSessionReplayScenario()
    }

    @Suppress("DEPRECATION")
    private fun enableSessionReplay() {
        val sessionReplayConfig = SessionReplayConfiguration
            .Builder(SAMPLE_IN_ALL_SESSIONS)
            .setPrivacy(SessionReplayPrivacy.ALLOW)
            .addExtensionSupport(MaterialExtensionSupport())
            .addExtensionSupport(ComposeExtensionSupport())
            .build()

        SessionReplay.enable(sessionReplayConfig, sdkCore.get())
    }

    private fun needToEnableLogs(config: BenchmarkConfig): Boolean {
        return config.isInstrumentedRun() && config.isLogsScenario()
    }

    private fun enableLogs() {
        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig, sdkCore.get())
    }

    private fun needToEnableRum(config: BenchmarkConfig): Boolean {
        // Session Replay and Timeseries require RUM, so RUM is enabled in both baseline and instrumented
        // for these scenarios — this way the diff isolates Session Replay / Timeseries overhead.
        return config.isSessionReplayScenario() ||
            config.isTimeseriesScenario() ||
            (config.isInstrumentedRun() && config.isRumScenario())
    }

    @OptIn(ExperimentalRumApi::class)
    private fun createRumConfiguration(config: BenchmarkConfig): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.BENCHMARK_RUM_APPLICATION_ID).apply {
            useViewTrackingStrategy(rumViewTrackingStrategy(config))
            setTelemetrySampleRate(SAMPLE_RATE_TELEMETRY)
            trackUserInteractions()
            trackLongTasks(THRESHOLD_LONG_TASK_INTERVAL)
            trackNonFatalAnrs(true)
            setViewEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            setActionEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            setResourceEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            setErrorEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            setLongTaskEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            setVitalEventMapper(
                vitalOperationStepEventMapper = { event ->
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    event
                },
                vitalAppLaunchEventMapper = { event ->
                    event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                    event
                }
            )
            enableComposeActionTracking()

            configureTimeseries(config, ::setTimeseriesConfiguration)
        }.build()
    }

    private fun rumViewTrackingStrategy(config: BenchmarkConfig): ViewTrackingStrategy? {
        return when (config.scenario) {
            SyntheticsScenario.RumManual -> null
            else -> NavigationViewTrackingStrategy(
                R.id.nav_host_fragment,
                true,
                BenchmarkNavigationPredicate()
            )
        }
    }

    private fun enableRum(config: BenchmarkConfig, intent: Intent) {
        val rumConfig = createRumConfiguration(config)
        Rum.enable(rumConfig, sdkCore = sdkCore.get())

        /**
         * We set synthetic test attributes now instead of waiting for them to be automatically
         * extracted by the sdk because the Activity for the test scenario will not have
         * the necessary attributes in the intent.
         */
        rumMonitor.get()._getInternal()?.setSyntheticsAttributeFromIntent(intent)
    }

    private fun needToEnableTracing(config: BenchmarkConfig): Boolean {
        return config.isInstrumentedRun() && config.isTracingScenario()
    }

    private fun enableTracing() {
        val tracesConfig = TraceConfiguration.Builder().build()

        Trace.enable(tracesConfig, sdkCore.get())
    }

    companion object {
        private const val SAMPLE_IN_ALL_SESSIONS = 100f
        private const val ATTR_IS_MAPPED = "is_mapped"
        private const val SAMPLE_RATE_TELEMETRY = 100f
        private const val THRESHOLD_LONG_TASK_INTERVAL = 250L
    }
}

internal fun configureTimeseries(
    config: BenchmarkConfig,
    setTimeseriesConfiguration: (TimeseriesConfiguration) -> Unit
) {
    if (config.isInstrumentedRun() && config.isTimeseriesScenario()) {
        setTimeseriesConfiguration(TimeseriesConfiguration.DEFAULT)
    }
}
