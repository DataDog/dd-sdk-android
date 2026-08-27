/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

internal sealed interface EmbeddedContentEvent {

    data class RecordBatch(
        val records: List<Map<String, Any?>>,
        val slotId: String,
        val viewId: String
    ) : EmbeddedContentEvent

    data class Resource(
        val identifier: String,
        val data: ByteArray,
        val mimeType: String
    ) : EmbeddedContentEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Resource

            if (identifier != other.identifier) return false
            if (!data.contentEquals(other.data)) return false
            return mimeType == other.mimeType
        }

        override fun hashCode(): Int {
            var result = identifier.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            return result
        }
    }
}
