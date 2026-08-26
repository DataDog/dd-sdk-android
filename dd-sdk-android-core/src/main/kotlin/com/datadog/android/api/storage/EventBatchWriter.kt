/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.storage

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.storage.TelemetryAwareEventBatchWriter
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.lint.InternalApi

/**
 * Writer allowing [FeatureScope] to write events in the storage.
 */
interface EventBatchWriter {

    /**
     * Writes the content of the event to the current available batch.
     * @param event the event to write (content + metadata)
     * @param batchMetadata the optional updated batch metadata
     * @param eventType additional information about the event data
     *
     * @return true if event was written, false otherwise.
     */
    @WorkerThread
    fun write(
        event: RawBatchEvent,
        batchMetadata: ByteArray?,
        eventType: EventType
    ): Boolean
}

/**
 * Writes an event, forwarding [telemetryContext] when the underlying writer supports
 * dropped-event telemetry, otherwise falling back to the plain [EventBatchWriter.write].
 * @param event the event to write (content + metadata)
 * @param batchMetadata the optional updated batch metadata
 * @param eventType additional information about the event data
 * @param telemetryContext metadata attached to dropped-event telemetry
 * @return true if event was written, false otherwise.
 */
@InternalApi
@WorkerThread
fun EventBatchWriter.write(
    event: RawBatchEvent,
    batchMetadata: ByteArray?,
    eventType: EventType,
    telemetryContext: TelemetryContext
): Boolean = (this as? TelemetryAwareEventBatchWriter)
    ?.write(event, batchMetadata, eventType, telemetryContext)
    ?: write(event, batchMetadata, eventType)
