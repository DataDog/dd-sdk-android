/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.api.storage.RawBatchEvent

internal data class BatchData(
    val id: BatchId,
    val data: List<RawBatchEvent>,
    val metadata: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BatchData

        if (id != other.id) return false
        if (data != other.data) return false
        if (metadata != null) {
            if (other.metadata == null) return false
            if (!metadata.contentEquals(other.metadata)) return false
        } else if (other.metadata != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + (metadata?.contentHashCode() ?: 0)
        return result
    }
}
