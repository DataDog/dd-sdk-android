/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.rum.internal.domain.scope.RumRawEvent

internal data class TelemetryEventId(
    val type: TelemetryType,
    val message: String,
    val kind: String?
)

internal val RumRawEvent.SendTelemetry.identity: TelemetryEventId
    get() {
        return TelemetryEventId(type, message, kind)
    }
