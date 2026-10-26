/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.config

internal fun BenchmarkConfig.isInstrumentedRun() = run == SyntheticsRun.Instrumented

internal fun BenchmarkConfig.isProfilingScenario() = scenario == SyntheticsScenario.Profiling

internal fun BenchmarkConfig.isTimeseriesScenario() = scenario == SyntheticsScenario.Timeseries

internal fun BenchmarkConfig.isTracingScenario() = scenario == SyntheticsScenario.Trace

internal fun BenchmarkConfig.isSessionReplayScenario() = when (scenario) {
    SyntheticsScenario.SessionReplayCompose,
    SyntheticsScenario.Upload,
    SyntheticsScenario.SessionReplay -> true

    SyntheticsScenario.RumManual,
    SyntheticsScenario.Trace,
    SyntheticsScenario.LogsCustom,
    SyntheticsScenario.LogsHeavyTraffic,
    SyntheticsScenario.RumAuto,
    SyntheticsScenario.Profiling,
    SyntheticsScenario.Timeseries,
    null -> false
}

internal fun BenchmarkConfig.isRumScenario() = when (scenario) {
    SyntheticsScenario.RumAuto,
    SyntheticsScenario.RumManual,
    SyntheticsScenario.Profiling -> true

    SyntheticsScenario.SessionReplay,
    SyntheticsScenario.SessionReplayCompose,
    SyntheticsScenario.Trace,
    SyntheticsScenario.LogsCustom,
    SyntheticsScenario.LogsHeavyTraffic,
    SyntheticsScenario.Upload,
    SyntheticsScenario.Timeseries,
    null -> false
}

internal fun BenchmarkConfig.isLogsScenario() = when (scenario) {
    SyntheticsScenario.LogsCustom,
    SyntheticsScenario.LogsHeavyTraffic -> true

    SyntheticsScenario.SessionReplay,
    SyntheticsScenario.SessionReplayCompose,
    SyntheticsScenario.RumManual,
    SyntheticsScenario.Trace,
    SyntheticsScenario.Upload,
    SyntheticsScenario.RumAuto,
    SyntheticsScenario.Profiling,
    SyntheticsScenario.Timeseries,
    null -> false
}
