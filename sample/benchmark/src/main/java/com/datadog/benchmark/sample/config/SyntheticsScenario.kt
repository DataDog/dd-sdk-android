/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.config

internal enum class SyntheticsScenario(val value: String) {

    SessionReplay("sr"),

    SessionReplayCompose("sr_compose"),

    RumManual("rum_manual"),

    RumAuto("rum_auto"),

    Trace("trace"),

    LogsCustom("logs_custom"),

    LogsHeavyTraffic("logs_heavy_traffic"),

    Upload("upload");

    companion object {
        fun from(value: String): SyntheticsScenario? {
            return values().firstOrNull { it.value == value }
        }
    }
}
