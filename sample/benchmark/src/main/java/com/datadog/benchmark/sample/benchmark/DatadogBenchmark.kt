/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.benchmark

import android.os.Bundle
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.material.MaterialExtensionSupport
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.DatadogMeter
import com.datadog.benchmark.sample.benchmark.DatadogBenchmark.Config
import com.datadog.sample.benchmark.BuildConfig

/**
 * Class to control the benchmark test in sample application. It takes the [Config] which is parsed
 * from Synthetics variables in order to decide which scenario it should run for the benchmark test.
 */
internal class DatadogBenchmark(config: Config) {

    private var meter: DatadogMeter = DatadogMeter.create(
        DatadogExporterConfiguration.Builder(BuildConfig.BM_API_KEY)
            .setApplicationId(BuildConfig.APPLICATION_ID)
            .setApplicationName(BENCHMARK_APPLICATION_NAME)
            .setRun(config.getRun())
            .setScenario(config.getScenario())
            .setApplicationVersion(BuildConfig.VERSION_NAME)
            .setIntervalInSeconds(METER_INTERVAL_IN_SECONDS)
            .build()
    )

    init {
        if (config.run == SyntheticsRun.Instrumented) {
            when (config.scenario) {
                SyntheticsScenario.SessionReplay -> enableSessionReplay()
                else -> {} // do nothing for now
            }
        }
    }

    fun start() {
        meter.startGauges()
    }

    fun stop() {
        meter.stopGauges()
    }

    private fun enableSessionReplay() {
        val sessionReplayConfig = SessionReplayConfiguration
            .Builder(SAMPLE_IN_ALL_SESSIONS)
            .setPrivacy(SessionReplayPrivacy.ALLOW)
            .addExtensionSupport(MaterialExtensionSupport())
            .build()
        SessionReplay.enable(sessionReplayConfig)
    }

    internal class Config(
        val run: SyntheticsRun? = null,
        val scenario: SyntheticsScenario? = null
    ) {
        fun getRun(): String {
            return run?.value ?: ""
        }

        fun getScenario(): String {
            return scenario?.value ?: ""
        }

        companion object {
            fun resolveSyntheticsBundle(bundle: Bundle?): Config {
                val scenario = bundle?.getString(BM_SYNTHETICS_SCENARIO)
                val run = bundle?.getString(BM_SYNTHETICS_RUN)
                return Config(
                    scenario = resolveScenario(scenario),
                    run = resolveRun(run)
                )
            }

            private fun resolveRun(run: String?): SyntheticsRun? {
                return run?.let {
                    SyntheticsRun.from(it)
                }
            }

            private fun resolveScenario(scenario: String?): SyntheticsScenario? {
                return scenario?.let {
                    SyntheticsScenario.from(it)
                }
            }

            private const val BM_SYNTHETICS_SCENARIO = "synthetics.benchmark.scenario"
            private const val BM_SYNTHETICS_RUN = "synthetics.benchmark.run"
        }
    }

    companion object {
        private const val METER_INTERVAL_IN_SECONDS = 10L
        private const val SAMPLE_IN_ALL_SESSIONS = 100f
        private const val BENCHMARK_APPLICATION_NAME = "Benchmark Application"
    }
}
