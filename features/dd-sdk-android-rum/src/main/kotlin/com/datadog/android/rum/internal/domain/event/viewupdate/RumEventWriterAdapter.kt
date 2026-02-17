/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType

/**
 * Adapts EventWriter interface to SDK's DataWriter<Any>.
 *
 * This adapter allows ViewEventTracker to write events through the SDK's
 * standard event writing infrastructure without depending directly on
 * DataWriter.
 *
 * @param writeScope The event write scope for accessing EventBatchWriter
 * @param dataWriter The SDK's DataWriter for persisting events
 */
internal class RumEventWriterAdapter(
    private val writeScope: EventWriteScope,
    private val dataWriter: DataWriter<Any>
) : EventWriter {

    /**
     * Writes an event using the SDK's DataWriter.
     *
     * The event is expected to be a Map<String, Any?> with "type" field
     * indicating whether it's a "view" or "view_update" event.
     *
     * @param event The event to write (Map format)
     * @return true if write succeeded, false otherwise
     */
    override fun write(event: Map<String, Any?>): Boolean {
        return try {
            var success = false
            writeScope { batchWriter ->
                // Call DataWriter with the EventBatchWriter provided by writeScope
                success = dataWriter.write(batchWriter, event, EventType.DEFAULT)
            }
            success
        } catch (e: Exception) {
            // Log error and return false on failure
            false
        }
    }
}
