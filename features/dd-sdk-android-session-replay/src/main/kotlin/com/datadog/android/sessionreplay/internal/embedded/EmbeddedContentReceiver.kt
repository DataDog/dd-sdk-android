/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.JsonSerializer
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.sessionreplay.internal.processor.ResourceProcessor
import com.datadog.android.sessionreplay.internal.storage.EmbeddedContentRecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.concurrent.Executor

internal class EmbeddedContentReceiver(
    private val rumContextProvider: RumContextProvider,
    private val recordWriter: () -> EmbeddedContentRecordWriter,
    private val resourceProcessor: () -> ResourceProcessor,
    private val isRecording: () -> Boolean,
    private val executor: () -> Executor,
    private val internalLogger: InternalLogger
) {
    fun receive(event: EmbeddedContentEvent) {
        if (!isRecording()) {
            return
        }
        when (event) {
            is EmbeddedContentEvent.RecordBatch -> {
                if (event.records.isNotEmpty()) {
                    val rumContext = rumContextProvider.getRumContext()
                    if (rumContext.isValid()) {
                        val scheduledWriter = recordWriter()
                        executor().executeSafe(PROCESS_EVENT_TASK_NAME, internalLogger) {
                            processRecordBatch(event, rumContext, scheduledWriter)
                        }
                    }
                }
            }

            is EmbeddedContentEvent.Resource -> {
                if (rumContextProvider.getRumContext().hasValidApplicationAndSession()) {
                    val scheduledProcessor = resourceProcessor()
                    executor().executeSafe(PROCESS_EVENT_TASK_NAME, internalLogger) {
                        processResource(event, scheduledProcessor)
                    }
                }
            }
        }
    }

    private fun processRecordBatch(
        event: EmbeddedContentEvent.RecordBatch,
        rumContext: SessionReplayRumContext,
        writer: EmbeddedContentRecordWriter
    ) {
        val records = JsonArray()
            .also { array ->
                event.records.forEach { record ->
                    val jsonRecord = JsonSerializer.toJsonElement(record)
                    if (jsonRecord is JsonObject) {
                        jsonRecord.addProperty(RECORD_SLOT_ID_KEY, event.slotId)
                        array.add(jsonRecord)
                    } else {
                        logInvalidRecord()
                    }
                }
            }

        if (records.size() == 0) {
            return
        }

        val enrichedRecord = JsonObject().apply {
            addProperty(APPLICATION_ID_KEY, rumContext.applicationId)
            addProperty(SESSION_ID_KEY, rumContext.sessionId)
            addProperty(VIEW_ID_KEY, event.viewId)
            add(RECORDS_KEY, records)
        }
        writer.writeRaw(
            enrichedRecord.toString().toByteArray(Charsets.UTF_8),
            event.viewId,
            records.size()
        )
    }

    private fun processResource(
        event: EmbeddedContentEvent.Resource,
        processor: ResourceProcessor
    ) {
        processor.process(event.identifier, event.data, event.mimeType)
    }

    private fun logInvalidRecord() {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { INVALID_EMBEDDED_RECORD_MESSAGE }
        )
    }

    companion object {
        // Enriched record envelope keys use the storage schema naming convention.
        internal const val APPLICATION_ID_KEY = "application_id"
        internal const val SESSION_ID_KEY = "session_id"
        internal const val VIEW_ID_KEY = "view_id"
        internal const val RECORDS_KEY = "records"

        // Embedded records use the Session Replay record schema naming convention.
        internal const val RECORD_SLOT_ID_KEY = "slotId"
        internal const val INVALID_EMBEDDED_RECORD_MESSAGE =
            "Session Replay received an invalid embedded content record."
        internal const val PROCESS_EVENT_TASK_NAME = "Embedded Session Replay event processing"
    }
}
