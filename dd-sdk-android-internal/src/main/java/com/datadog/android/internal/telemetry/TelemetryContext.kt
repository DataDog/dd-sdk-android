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
        bytesLost: Int,
        vararg customFields: Pair<String, Any> = emptyArray()
    ): Map<String, Any> = asAttributesMap(featureName, bytesLost, eventType, *customFields)

    companion object {
        /** Sentinel value used when the number of bytes lost for a dropped event is not known. */
        const val BYTE_LOST_UNKNOWN: Int = -1

        /** Name of the feature ("rum", "logs", "tracing", ...) whose event was affected. */
        internal const val FEATURE_NAME: String = "telemetry.feature_name"

        /** Serialized size, in bytes, of an event that was dropped because it exceeded the size limit. */
        internal const val EVENT_DROPPED_BYTES: String = "telemetry.dropped_bytes"

        /** Category of the dropped event, in a feature-specific format (e.g. RUM uses its model
         * event type such as "view", "action", "error") — when known. */
        internal const val EVENT_TYPE: String = "telemetry.event.type"

        /** Configured limit, in bytes, that the persisted data exceeded. */
        const val TELEMETRY_DATA_LIMIT: String = "telemetry.data.limit"

        /** Path of the file involved in the telemetry event. */
        const val TELEMETRY_FILE_PATH: String = "telemetry.file.path"

        /** Batch operation being performed when the telemetry event was recorded. */
        const val TELEMETRY_BATCH_OPERATION: String = "telemetry.batch.operation"

        /** Number of bytes expected to be read or written for the batch operation. */
        const val TELEMETRY_BATCH_BYTES_EXPECTED: String = "telemetry.batch.bytes_expected"

        /** Number of bytes actually read or written for the batch operation. */
        const val TELEMETRY_BATCH_BYTES_ACTUAL: String = "telemetry.batch.bytes_actual"

        /** Identifier of the TLV block type that was actually read. */
        const val TELEMETRY_BLOCK_TYPE_ACTUAL_IDENTIFIER: String = "telemetry.block.type.actual_identifier"

        /** TLV block type that was expected to be read. */
        const val TELEMETRY_BLOCK_TYPE_EXPECTED: String = "telemetry.block.type.expected"

        /** Identifier of the TLV block type that was expected to be read. */
        const val TELEMETRY_BLOCK_TYPE_EXPECTED_IDENTIFIER: String = "telemetry.block.type.expected_identifier"

        /** Type of TLV entry, whether captured while writing it or while reading it back. */
        const val TELEMETRY_TLV_TYPE: String = "telemetry.tlv.type"

        /** Size, in bytes, of the TLV entry involved in the telemetry event. */
        const val TELEMETRY_TLV_SIZE: String = "telemetry.tlv.size"

        /** Configured size limit, in bytes, that the TLV entry exceeded. */
        const val TELEMETRY_TLV_SIZE_LIMIT: String = "telemetry.tlv.limit"

        /** Length, in bytes, of the TLV entry's data block. */
        const val TELEMETRY_TLV_DATA_LENGTH: String = "telemetry.tlv.data.length"

        /** Configured length limit, in bytes, that the TLV entry's data block exceeded. */
        const val TELEMETRY_TLV_DATA_LENGTH_LIMIT: String = "telemetry.tlv.data.limit"

        /** Length, in bytes, of the TLV entry's header block. */
        const val TELEMETRY_TLV_HEADER_LENGTH: String = "telemetry.tlv.header.length"

        /** Configured length limit, in bytes, that the TLV entry's header block exceeded. */
        const val TELEMETRY_TLV_HEADER_LENGTH_LIMIT: String = "telemetry.tlv.header.limit"

        /**
         * Builds a map of telemetry attributes from the provided feature name,
         * optional dropped bytes, and optional event type.
         */
        fun asAttributesMap(
            featureName: String,
            bytesLost: Int? = null,
            eventType: String? = null,
            vararg customFields: Pair<String, Any> = emptyArray()
        ): Map<String, Any> = buildMap {
            put(FEATURE_NAME, featureName)
            bytesLost?.let { put(EVENT_DROPPED_BYTES, it) }
            eventType?.let { put(EVENT_TYPE, it) }
            customFields.forEach { put(it.first, it.second) }
        }
    }
}
