/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.telemetry

/**
 * Telemetry metadata for dropped-event diagnostics. Instances are converted to an
 * `additionalProperties` map via [asAttributesMap] and attached to internal telemetry error events so that
 * dropped-event diagnostics stay consistent and queryable across features and across the read/write
 * persistence paths.
 */
data class TelemetryContext(
    /**
     * Name of the feature ("rum", "logs", "tracing", ...) whose event was affected.
     */
    val featureName: String,
    /**
     * Category of the dropped event, in a feature-specific format (e.g. RUM uses its model event
     * type such as "view", "action", "error") — when known.
     */
    val eventType: String? = null
) {
    /**
     * Converts this metadata instance into a map of telemetry attributes suitable for
     * ingestion by the telemetry batch.
     */
    fun asAttributesMap(
        bytesLost: Int
    ): Map<String, Any> = asAttributesMap(featureName, bytesLost, eventType)

    companion object {
        /** Sentinel value used when the number of bytes lost for a dropped event is not known. */
        const val BYTE_LOST_UNKNOWN: Int = -1

        /** Name of the feature ("rum", "logs", "tracing", ...) whose event was affected. */
        internal const val FEATURE_NAME: String = "event.feature_name"

        /** Serialized size, in bytes, of an event that was dropped because it exceeded the size limit. */
        internal const val EVENT_DROPPED_BYTES: String = "event.dropped_bytes"

        /** Category of the dropped event, in a feature-specific format (e.g. RUM uses its model
         * event type such as "view", "action", "error") — when known. */
        internal const val EVENT_TYPE: String = "event.type"

        /**
         * Builds a map of telemetry attributes from the provided feature name,
         * optional dropped bytes, and optional event type.
         */
        fun asAttributesMap(
            featureName: String,
            bytesLost: Int? = null,
            eventType: String? = null
        ): Map<String, Any> = buildMap {
            put(FEATURE_NAME, featureName)
            bytesLost?.let { put(EVENT_DROPPED_BYTES, it) }
            eventType?.let { put(EVENT_TYPE, it) }
        }
    }
}
