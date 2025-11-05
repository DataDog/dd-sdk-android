/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import android.content.Intent
import com.datadog.android.api.SdkCore
import com.datadog.android.compose.enableComposeActionTracking
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumMonitor
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
import com.datadog.benchmark.sample.config.SyntheticsRun
import com.datadog.benchmark.sample.config.SyntheticsScenario
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
        if (config.run == SyntheticsRun.Baseline) {
            return
        }

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

        SessionReplay.enable(sessionReplayConfig, sdkCore.get())
    }

    private fun needToEnableLogs(config: BenchmarkConfig): Boolean {
        return isInstrumentedRun(config) && isLogsScenario(config)
    }

    private fun enableLogs() {
        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig, sdkCore.get())
    }

    private fun needToEnableRum(config: BenchmarkConfig): Boolean {
        return if (isInstrumentedRun(config)) {
            isSessionReplayScenario(config) || isRumScenario(config)
        } else {
            false
        }
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
            setVitalOperationStepEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            setVitalAppLaunchEventMapper { event ->
                event.context?.additionalProperties?.put(ATTR_IS_MAPPED, true)
                event
            }
            enableComposeActionTracking()
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

    private fun isSessionReplayScenario(config: BenchmarkConfig) = when (config.scenario) {
        SyntheticsScenario.SessionReplayCompose,
        SyntheticsScenario.Upload,
        SyntheticsScenario.SessionReplay -> true
        SyntheticsScenario.RumManual,
        SyntheticsScenario.Trace,
        SyntheticsScenario.LogsCustom,
        SyntheticsScenario.LogsHeavyTraffic,
        SyntheticsScenario.RumAuto,
        null -> false
    }

    private fun isRumScenario(config: BenchmarkConfig) = when (config.scenario) {
        SyntheticsScenario.RumAuto,
        SyntheticsScenario.RumManual -> true
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
        SyntheticsScenario.RumManual,
        SyntheticsScenario.Trace,
        SyntheticsScenario.Upload,
        SyntheticsScenario.RumAuto,
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

        Trace.enable(tracesConfig, sdkCore.get())
    }

    private fun isInstrumentedRun(config: BenchmarkConfig) = config.run == SyntheticsRun.Instrumented

    companion object {
        private const val SAMPLE_IN_ALL_SESSIONS = 100f
        private const val ATTR_IS_MAPPED = "is_mapped"
        private const val SAMPLE_RATE_TELEMETRY = 100f
        private const val THRESHOLD_LONG_TASK_INTERVAL = 250L
    }
}
