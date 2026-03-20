/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import androidx.annotation.WorkerThread
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.serializeToByteArray
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.domain.event.RumEventMeta
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.domain.scope.RumViewUpdateData
import com.datadog.android.rum.model.ViewEvent

internal class RumDataWriter(
    internal val eventMapper: RumEventMapper,
    internal val eventSerializer: RumEventSerializer,
    private val eventMetaSerializer: Serializer<RumEventMeta>,
    private val sdkCore: InternalSdkCore
) : DataWriter<Any> {

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: Any, eventType: EventType): Boolean {
        return when (element) {
            is ViewEvent -> writeViewEvent(writer, element, eventType)
            is RumViewUpdateData -> writeViewUpdateEvent(writer, element, eventType)
            else -> writeOtherEvent(writer, element, eventType)
        }
    }

    @WorkerThread
    private fun writeViewEvent(writer: EventBatchWriter, event: ViewEvent, eventType: EventType): Boolean {
        val byteArray = eventSerializer.serializeToByteArray(event, sdkCore.internalLogger)
            ?: return false

        val eventMeta = RumEventMeta.View(
            viewId = event.view.id,
            documentVersion = event.dd.documentVersion
        )
        val serializedEventMeta = eventMetaSerializer.serializeToByteArray(eventMeta, sdkCore.internalLogger)
            ?: EMPTY_BYTE_ARRAY

        return writeBatchEvent(writer, RawBatchEvent(data = byteArray, metadata = serializedEventMeta), eventType) {
            sdkCore.writeLastViewEvent(byteArray)
        }
    }

    @WorkerThread
    private fun writeViewUpdateEvent(
        writer: EventBatchWriter,
        eventData: RumViewUpdateData,
        eventType: EventType
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

        return writeBatchEvent(writer, RawBatchEvent(data = byteArray, metadata = serializedEventMeta), eventType) {
            // serialize the full ViewEvent only on successful write, for crash recovery
            val byteArrayView = eventSerializer.serializeToByteArray(eventData.viewEvent, sdkCore.internalLogger)
            if (byteArrayView != null) sdkCore.writeLastViewEvent(byteArrayView)
        }
    }

    @WorkerThread
    private fun writeOtherEvent(writer: EventBatchWriter, event: Any, eventType: EventType): Boolean {
        val mappedElement = eventMapper.map(event) ?: return false
        return eventSerializer.serializeToByteArray(mappedElement, sdkCore.internalLogger)
            ?.let { writeBatchEvent(writer, RawBatchEvent(data = it), eventType) }
            ?: false
    }

    @WorkerThread
    private fun writeBatchEvent(
        writer: EventBatchWriter,
        batchEvent: RawBatchEvent,
        eventType: EventType,
        onSuccess: () -> Unit = {}
    ): Boolean {
        return synchronized(this) {
            val result = writer.write(batchEvent, null, eventType)
            if (result) onSuccess()
            result
        }
    }

    companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
