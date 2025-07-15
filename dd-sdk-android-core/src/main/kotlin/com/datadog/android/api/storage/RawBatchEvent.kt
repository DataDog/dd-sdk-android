/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.storage

/**
 * Representation of the raw data which is going to be written in the batch file.
 *
 * @property data Raw data to write.
 * @property metadata Optional metadata to write for this event.
 */
data class RawBatchEvent(
    val data: ByteArray,
    val metadata: ByteArray = EMPTY_BYTE_ARRAY,
    val mimeType: String? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawBatchEvent

        if (!data.contentEquals(other.data)) return false
        if (!metadata.contentEquals(other.metadata)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + metadata.contentHashCode()
        return result
    }

    private companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
