/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.log.internal.storage

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.serializeToByteArray
import com.datadog.android.log.model.LogEvent
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.internal.storage.DataWriter

internal class LogsDataWriter(
    internal val serializer: Serializer<LogEvent>,
    private val internalLogger: InternalLogger
) : DataWriter<LogEvent> {

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: LogEvent): Boolean {
        val serialized = serializer.serializeToByteArray(element, internalLogger) ?: return false
        return synchronized(this) { writer.write(serialized, null) }
    }
}
