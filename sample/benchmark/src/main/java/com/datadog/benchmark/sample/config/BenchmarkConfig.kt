/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.config

import android.os.Bundle

internal class BenchmarkConfig(
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
        fun resolveSyntheticsBundle(bundle: Bundle?): BenchmarkConfig {
            val scenario = bundle?.getString(BM_SYNTHETICS_SCENARIO)
            val run = bundle?.getString(BM_SYNTHETICS_RUN)
            return BenchmarkConfig(
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
