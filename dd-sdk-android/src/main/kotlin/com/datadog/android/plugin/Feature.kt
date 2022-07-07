/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.plugin

/**
 * Provides the available feature for which a [DatadogPlugin] can be assigned.
 */
enum class Feature(internal val featureName: String) {
    LOG("Logging"),
    CRASH("Crash Reporting"),
    TRACE("Tracing"),
    RUM("RUM"),
    SESSION_REPLAY("Session Replay")
}
