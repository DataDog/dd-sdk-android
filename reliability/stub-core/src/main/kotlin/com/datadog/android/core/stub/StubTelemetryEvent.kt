/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.api.InternalLogger

/**
 * Stubs a telemetry event.
 * @param type the [Type] of event
 * @param message the message attached to this telemetry
 * @param additionalProperties a map of additional properties
 * @param samplingRate the (optional) sampling rate to avoid spamming the same message in our telemetry org
 * @param level the (optional) level attached to Log telemetry events
 * @param creationSampleRate the (optional) sampling rate to avoid spamming the same message in our telemetry org for
 * long-lived metrics like method performance measure
 */
data class StubTelemetryEvent(
    val type: Type,
    val message: String,
    val additionalProperties: Map<String, Any?>,
    val samplingRate: Float? = null,
    val level: InternalLogger.Level? = null,
    val creationSampleRate: Float? = null
) {
    /**
     * The type of Telemetry event.
     */
    enum class Type {
        /** An API Usage event. */
        API_USAGE,

        /** A Log event. */
        LOG,

        /** A Metric event. */
        METRIC
    }
}
