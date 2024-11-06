/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.benchmark

internal enum class SyntheticsScenario(val value: String) {

    SessionReplay("sr"),

    SessionReplayCompose("sr_compose"),

    Rum("rum"),

    Trace("trace"),

    Logs("logs");

    companion object {
        fun from(value: String): SyntheticsScenario? {
            return values().firstOrNull { it.value == value }
        }
    }
}
