/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import androidx.annotation.WorkerThread
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.api.storage.write
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.serializeToByteArray
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.domain.event.RumEventMeta
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.domain.scope.DiffThenFullView
import com.datadog.android.rum.internal.domain.scope.MappedViewEvent
import com.datadog.android.rum.internal.domain.scope.RumViewUpdateData
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.android.rum.model.VitalAppLaunchEvent
import com.datadog.android.rum.model.VitalOperationStepEvent
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent

@Suppress("TooManyFunctions")
internal class RumDataWriter(
    internal val eventMapper: RumEventMapper,
    internal val eventSerializer: RumEventSerializer,
    private val eventMetaSerializer: Serializer<RumEventMeta>,
    private val sdkCore: InternalSdkCore
) : DataWriter<Any> {

    private var currentViewId: String? = null

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: Any, eventType: EventType): Boolean {
        // We support two full-view payload forms:
        // - MappedViewEvent: produced by RumViewEventWriter in the regular runtime pipeline,
        //   already passed through ViewEventMapper and should not be mapped again.
        // - ViewEvent: raw full views (for example late-crash reporting path) that still need
        //   ViewEventMapper processing before serialization.
        return when (element) {
            is MappedViewEvent -> writeMappedViewEvent(writer, element.viewEvent, eventType)
            is ViewEvent -> writeRawViewEvent(writer, element, eventType)
            is RumViewUpdateData -> writeViewUpdateEvent(writer, element, eventType)
            is DiffThenFullView -> writeDiffThenFullView(writer, element, eventType)
            else -> writeOtherEvent(writer, element, eventType)
        }
    }

    @WorkerThread
    private fun writeRawViewEvent(writer: EventBatchWriter, event: ViewEvent, eventType: EventType): Boolean {
        val mappedEvent = eventMapper.map(event) as? ViewEvent ?: return false
        return writeMappedViewEvent(writer, mappedEvent, eventType)
    }

    @WorkerThread
    private fun writeMappedViewEvent(writer: EventBatchWriter, event: ViewEvent, eventType: EventType): Boolean {
        onViewEventSubmitted(event)

        val (byteArray, serializedEventMeta) = serializeViewEvent(event) ?: return false

        return writeBatchEvent(
            writer,
            RawBatchEvent(data = byteArray, metadata = serializedEventMeta),
            eventType,
            telemetryContext(event)
        ) {
            onDataWritten(event, byteArray)
        }
    }

    @WorkerThread
    private fun writeFullViewCheckpoint(writer: EventBatchWriter, event: ViewEvent, eventType: EventType): Boolean {
        val (byteArray, serializedEventMeta) = serializeViewEvent(event) ?: return false

        return writeBatchEvent(
            writer,
            RawBatchEvent(data = byteArray, metadata = serializedEventMeta),
            eventType,
            telemetryContext(event)
        ) {
            onDataWritten(event, byteArray)
        }
    }

    @WorkerThread
    private fun serializeViewEvent(event: ViewEvent): Pair<ByteArray, ByteArray>? {
        val byteArray = eventSerializer.serializeToByteArray(event, sdkCore.internalLogger)
            ?: return null

        val eventMeta = RumEventMeta.View(
            viewId = event.view.id,
            documentVersion = event.dd.documentVersion,
            hasAccessibility = event.view.accessibility != null
        )
        val serializedEventMeta = eventMetaSerializer.serializeToByteArray(eventMeta, sdkCore.internalLogger)
            ?: EMPTY_BYTE_ARRAY

        return byteArray to serializedEventMeta
    }

    @WorkerThread
    private fun writeViewUpdateEvent(
        writer: EventBatchWriter,
        eventData: RumViewUpdateData,
        eventType: EventType,
        writeCrashRecovery: Boolean = true
    ): Boolean {
        val event = eventData.viewUpdate
        val byteArray = eventSerializer.serializeToByteArray(event, sdkCore.internalLogger)
            ?: return false

        val eventMeta = RumEventMeta.ViewUpdate(
            viewId = event.view.id,
            documentVersion = event.dd.documentVersion
        )
        val serializedEventMeta = eventMetaSerializer.serializeToByteArray(eventMeta, sdkCore.internalLogger)
            ?: EMPTY_BYTE_ARRAY

        return writeBatchEvent(
            writer,
            RawBatchEvent(data = byteArray, metadata = serializedEventMeta),
            eventType,
            telemetryContext(event)
        ) {
            if (writeCrashRecovery) {
                // serialize the full ViewEvent only on successful write, for crash recovery
                val byteArrayView = eventSerializer.serializeToByteArray(eventData.viewEvent, sdkCore.internalLogger)
                if (byteArrayView != null) onDataWritten(eventData.viewEvent, byteArrayView)
            }
        }
    }

    @WorkerThread
    private fun writeDiffThenFullView(
        writer: EventBatchWriter,
        eventData: DiffThenFullView,
        eventType: EventType
    ): Boolean {
        // Write the partial diff first — skip crash-recovery write since the full view below will do it
        val diffWritten = writeViewUpdateEvent(
            writer,
            RumViewUpdateData(eventData.viewUpdate, eventData.viewEvent),
            eventType,
            writeCrashRecovery = false
        )
        // Only write the full view checkpoint if the diff was persisted
        if (diffWritten) {
            writeFullViewCheckpoint(writer, eventData.viewEvent, eventType)
        }
        // Always return diffWritten so prevViewEvent is updated whenever the diff was
        // persisted, keeping the baseline consistent regardless of full view write outcome
        return diffWritten
    }

    @WorkerThread
    private fun writeOtherEvent(writer: EventBatchWriter, event: Any, eventType: EventType): Boolean {
        val mappedElement = eventMapper.map(event) ?: return false
        return eventSerializer.serializeToByteArray(mappedElement, sdkCore.internalLogger)
            ?.let { writeBatchEvent(writer, RawBatchEvent(data = it), eventType, telemetryContext(event)) }
            ?: false
    }

    @WorkerThread
    private fun writeBatchEvent(
        writer: EventBatchWriter,
        batchEvent: RawBatchEvent,
        eventType: EventType,
        telemetryContext: TelemetryContext,
        onSuccess: () -> Unit = {}
    ): Boolean {
        return synchronized(this) {
            val result = writer.write(batchEvent, null, eventType, telemetryContext)
            if (result) onSuccess()
            result
        }
    }

    @WorkerThread
    internal fun onDataWritten(data: ViewEvent, rawData: ByteArray) {
        onViewEventWritten(data, rawData)
    }

    @WorkerThread
    internal fun onViewEventSubmitted(data: ViewEvent) {
        synchronized(this) {
            if (data.dd.documentVersion == FIRST_VIEW_DOCUMENT_VERSION) {
                currentViewId = data.view.id
            }
        }
    }

    @WorkerThread
    private fun onViewEventWritten(data: ViewEvent, rawData: ByteArray) {
        if (data.view.id == currentViewId) {
            sdkCore.writeLastViewEvent(rawData)
        }
    }

    private fun telemetryContext(event: Any): TelemetryContext =
        TelemetryContext(featureName = Feature.RUM_FEATURE_NAME, eventType = resolveEventType(event))

    companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)

        internal const val FIRST_VIEW_DOCUMENT_VERSION = 2L

        private const val UNKNOWN_EVENT_TYPE = "unknown"

        private fun resolveEventType(event: Any): String = when (event) {
            is ActionEvent -> event.type
            is ErrorEvent -> event.type
            is LongTaskEvent -> event.type
            is ResourceEvent -> event.type
            is ViewEvent -> event.type
            is ViewUpdateEvent -> event.type
            is VitalAppLaunchEvent -> event.type
            is VitalOperationStepEvent -> event.type
            is TelemetryConfigurationEvent -> event.type
            is TelemetryDebugEvent -> event.type
            is TelemetryErrorEvent -> event.type
            is TelemetryUsageEvent -> event.type
            is TimeseriesCpuEvent -> event.type
            is TimeseriesMemoryEvent -> event.type
            else -> UNKNOWN_EVENT_TYPE
        }
    }
}
