/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils

import com.datadog.android.rum.internal.domain.event.ResourceTiming

internal fun ResourceTiming.asTimingsPayload(): MutableMap<String, Any?> {
    return mutableMapOf(
        "download" to mutableMapOf(
            "startTime" to downloadStart,
            "duration" to downloadDuration
        ),
        "ssl" to mutableMapOf(
            "startTime" to sslStart,
            "duration" to sslDuration
        ),
        "firstByte" to mutableMapOf(
            "startTime" to firstByteStart,
            "duration" to firstByteDuration
        ),
        "connect" to mutableMapOf(
            "startTime" to connectStart,
            "duration" to connectDuration
        ),
        "dns" to mutableMapOf(
            "startTime" to dnsStart,
            "duration" to dnsDuration
        )
    )
}
