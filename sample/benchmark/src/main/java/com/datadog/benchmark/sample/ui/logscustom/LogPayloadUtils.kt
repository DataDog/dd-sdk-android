/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("StringLiteralDuplication")

package com.datadog.benchmark.sample.ui.logscustom

import java.util.UUID

internal fun LogPayloadSize.createLogAttributes(): Map<String, Any?> {
    return when (this) {
        LogPayloadSize.Small -> SMALL_ATTRIBUTES_PAYLOAD
        LogPayloadSize.Medium -> MEDIUM_ATTRIBUTES_PAYLOAD
        LogPayloadSize.Large -> LARGE_ATTRIBUTES_PAYLOAD
    }
}

private val MEDIUM_ATTRIBUTES_PAYLOAD = mapOf(
    "user" to mapOf(
        "id" to UUID.randomUUID().toString(),
        "name" to "John Doe",
        "email" to "johndoe@example.com"
    ),
    "device" to mapOf(
        "type" to "iPhone",
        "os" to "iOS 17.0"
    ),
    "log_type" to "user_event"
)

private val LARGE_ATTRIBUTES_PAYLOAD = mapOf(
    "log_type" to "user_event",
    "session" to mapOf(
        "id" to UUID.randomUUID().toString(),
        "startTime" to "2024-02-27T12:00:00Z",
        "duration" to "2450"
    ),
    "user" to mapOf(
        "id" to "a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6",
        "name" to "John Doe",
        "email" to "johndoe@example.com"
    ),
    "location" to mapOf(
        "city" to "San Francisco",
        "country" to "USA"
    ),
    "device" to mapOf(
        "model" to "iPhone 15 Pro",
        "os" to "iOS 17.2",
        "battery" to "80%"
    ),
    "network" to mapOf(
        "type" to "WiFi",
        "carrier" to "Verizon"
    ),
    "errorStack" to mapOf(
        "stackTrace" to "Error at module XYZ -> function ABC",
        "crashType" to "NullPointerException"
    )
)

private val SMALL_ATTRIBUTES_PAYLOAD = mapOf(
    "log_type" to "simple"
)
