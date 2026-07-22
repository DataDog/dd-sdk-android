/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.storage

import androidx.annotation.WorkerThread
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.lint.InternalApi

/**
 * SDK-internal extension of [EventBatchWriter] that lets the SDK's own [DataWriter] implementations
 * attach dropped-event telemetry metadata to a write call. This is not part of the supported public
 * API: third-party code must never implement this interface; only the SDK's internal [EventBatchWriter]
 * implementations do, and only the SDK's internal [DataWriter] implementations should check for it
 * (e.g. via `writer as? TelemetryAwareEventBatchWriter`) before falling back to the plain
 * [EventBatchWriter.write].
 */
@InternalApi
interface TelemetryAwareEventBatchWriter : EventBatchWriter {
    /**
     * Write a raw event with telemetry metadata for dropped-event tracking.
     *
     * @param event the raw batch event to write
     * @param batchMetadata optional batch metadata
     * @param eventType the type of the event being written
     * @param telemetryContext telemetry metadata to attach to this write call
     * @return `true` if the event was written successfully, `false` otherwise
     */
    @WorkerThread
    fun write(
        event: RawBatchEvent,
        batchMetadata: ByteArray?,
        eventType: EventType,
        telemetryContext: TelemetryContext
    ): Boolean
}
