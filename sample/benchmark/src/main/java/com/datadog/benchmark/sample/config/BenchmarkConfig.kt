/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.config

import android.content.SharedPreferences
import android.os.Bundle

internal class BenchmarkConfig(
    val run: SyntheticsRun? = null,
    val scenario: SyntheticsScenario? = null
) {
    val isComposeEnabled = scenario == SyntheticsScenario.SessionReplayCompose

    fun getRun(): String {
        return run?.value ?: ""
    }

    fun getScenario(): String {
        return scenario?.value ?: ""
    }

    fun saveToPrefs(preferences: SharedPreferences) {
        preferences.edit().apply {
            putString(SYNTHETICS_SCENARIO_PREFS_KEY, scenario?.value)
            putString(SYNTHETICS_RUN_PREFS_KEY, run?.value)
            commit()
        }
    }

    companion object {
        fun resolveSyntheticsBundle(bundle: Bundle?): BenchmarkConfig {
            val scenario = bundle?.getString(BM_SYNTHETICS_SCENARIO)
            val run = bundle?.getString(BM_SYNTHETICS_RUN)
            return BenchmarkConfig(
                scenario = resolveScenario(scenario),
                run = resolveRun(run)
            )
        }

        fun fromPrefs(sharedPreferences: SharedPreferences): BenchmarkConfig {
            val scenario = resolveScenario(sharedPreferences.getString(SYNTHETICS_SCENARIO_PREFS_KEY, null))
            val run = resolveRun(sharedPreferences.getString(SYNTHETICS_RUN_PREFS_KEY, null))

            return BenchmarkConfig(run = run, scenario = scenario)
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
        private const val SYNTHETICS_SCENARIO_PREFS_KEY = "synthetics.benchmark.scenario"
        private const val SYNTHETICS_RUN_PREFS_KEY = "synthetics.benchmark.run"
    }
}
