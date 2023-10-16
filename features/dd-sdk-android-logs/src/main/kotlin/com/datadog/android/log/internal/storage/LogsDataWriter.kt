/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.storage

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.serializeToByteArray
import com.datadog.android.log.model.LogEvent

internal class LogsDataWriter(
    internal val serializer: Serializer<LogEvent>,
    private val internalLogger: InternalLogger
) : DataWriter<LogEvent> {

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: LogEvent): Boolean {
        val serialized = serializer.serializeToByteArray(element, internalLogger) ?: return false
        return synchronized(this) { writer.write(RawBatchEvent(data = serialized), batchMetadata = null) }
    }
}
