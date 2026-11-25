/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

/**
 * Holds the data and metadata of an event written via the [StubSDKCore].
 * @param eventData the event data
 * @param rawEventMetadata the event metadata as it was written, which may be binary
 * @param batchMetadata the batch metadata
 */
@Suppress("ArrayInDataClass")
data class StubEvent(
    val eventData: String,
    val rawEventMetadata: ByteArray,
    val batchMetadata: String
) {

    /**
     * The event metadata decoded as UTF-8. Invalid bytes are replaced rather than rejected, so prefer
     * [rawEventMetadata] for binary metadata.
     */
    val eventMetadata: String
        get() = String(rawEventMetadata, Charsets.UTF_8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StubEvent) return false
        return eventData == other.eventData &&
            rawEventMetadata.contentEquals(other.rawEventMetadata) &&
            batchMetadata == other.batchMetadata
    }

    override fun hashCode(): Int {
        var result = eventData.hashCode()
        result = 31 * result + rawEventMetadata.contentHashCode()
        result = 31 * result + batchMetadata.hashCode()
        return result
    }

    override fun toString(): String {
        return "StubEvent(eventData=$eventData, eventMetadata=$eventMetadata, batchMetadata=$batchMetadata)"
    }
}
