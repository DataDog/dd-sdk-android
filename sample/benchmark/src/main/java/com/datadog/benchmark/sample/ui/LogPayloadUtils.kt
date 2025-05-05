/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("StringLiteralDuplication", "MatchingDeclarationName")

package com.datadog.benchmark.sample.ui

import android.util.Log
import java.util.UUID

internal enum class LogPayloadSize {
    Small,
    Medium,
    Large
}

internal fun LogPayloadSize.createLogAttributes(): Map<String, Any?> {
    return when (this) {
        LogPayloadSize.Small -> SMALL_ATTRIBUTES_PAYLOAD
        LogPayloadSize.Medium -> MEDIUM_ATTRIBUTES_PAYLOAD
        LogPayloadSize.Large -> LARGE_ATTRIBUTES_PAYLOAD
    }
}

internal val ALL_LOG_LEVELS = listOf(
    Log.ERROR,
    Log.VERBOSE,
    Log.ASSERT,
    Log.WARN,
    Log.INFO,
    Log.DEBUG
)

internal fun Int.stringRepresentation(): String {
    return when (this) {
        Log.ERROR -> "ERROR"
        Log.VERBOSE -> "VERBOSE"
        Log.ASSERT -> "ASSERT"
        Log.WARN -> "WARN"
        Log.INFO -> "INFO"
        Log.DEBUG -> "DEBUG"
        else -> "UNKNOWN"
    }
}

private val MEDIUM_ATTRIBUTES_PAYLOAD = mapOf(
    "benchmark_user" to mapOf(
        "id" to UUID.randomUUID().toString(),
        "name" to "John Doe",
        "email" to "johndoe@example.com"
    ),
    "benchmark_device" to mapOf(
        "type" to "Android Phone",
        "os" to "Android 15"
    ),
    "benchmark_log_type" to "user_event"
)

private val LARGE_ATTRIBUTES_PAYLOAD = mapOf(
    "benchmark_log_type" to "user_event",
    "benchmark_session" to mapOf(
        "id" to UUID.randomUUID().toString(),
        "startTime" to "2024-02-27T12:00:00Z",
        "duration" to "2450"
    ),
    "benchmark_user" to mapOf(
        "id" to "a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6",
        "name" to "John Doe",
        "email" to "johndoe@example.com"
    ),
    "benchmark_location" to mapOf(
        "city" to "San Francisco",
        "country" to "USA"
    ),
    "benchmark_device" to mapOf(
        "model" to "Google Pixel 8",
        "os" to "Android 15",
        "battery" to "80%"
    ),
    "benchmark_network" to mapOf(
        "type" to "WiFi",
        "carrier" to "Verizon"
    ),
    "benchmark_errorStack" to mapOf(
        "stackTrace" to "Error at module XYZ -> function ABC",
        "crashType" to "NullPointerException"
    )
)

private val SMALL_ATTRIBUTES_PAYLOAD = mapOf(
    "benchmark_log_type" to "simple"
)
