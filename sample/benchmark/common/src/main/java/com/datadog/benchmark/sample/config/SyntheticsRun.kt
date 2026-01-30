/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.config

enum class SyntheticsRun(val value: String) {

    Baseline("baseline"),

    Instrumented("instrumented");

    companion object {
        fun from(value: String): SyntheticsRun? {
            return values().firstOrNull { it.value == value }
        }
    }
}
