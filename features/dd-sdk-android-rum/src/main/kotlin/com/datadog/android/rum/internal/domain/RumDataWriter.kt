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

    // region DataWriter

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: Any, eventType: EventType): Boolean {
        return when (element) {
            is ViewEvent -> writeViewEvent(writer = writer, event = element, eventType = eventType)
            is RumViewUpdateData -> writeViewUpdateEvent(writer = writer, eventData = element, eventType = eventType)
            else -> writeOtherEvent(writer = writer, event = element, eventType = eventType)
        }
    }

    // endregion

    // region Internal

    // endregion

    private fun writeViewEvent(writer: EventBatchWriter, event: ViewEvent, eventType: EventType): Boolean {
        val byteArray = eventSerializer.serializeToByteArray(
            event,
            sdkCore.internalLogger
        ) ?: return false

        val eventMeta = RumEventMeta.View(
            viewId = event.view.id,
            documentVersion = event.dd.documentVersion
        )
        val serializedEventMeta =
            eventMetaSerializer.serializeToByteArray(eventMeta, sdkCore.internalLogger)
                ?: EMPTY_BYTE_ARRAY

        val batchEvent = RawBatchEvent(
            data = byteArray,
            metadata = serializedEventMeta
        )

        synchronized(this) {
            val result = writer.write(batchEvent, null, eventType)
            if (result) {
                sdkCore.writeLastViewEvent(byteArray)
            }
            return result
        }
    }

    private fun writeViewUpdateEvent(writer: EventBatchWriter, eventData: RumViewUpdateData, eventType: EventType): Boolean {
        val event = eventData.viewUpdate
        val viewEvent = eventData.viewEvent

        val byteArray = eventSerializer.serializeToByteArray(
            event,
            sdkCore.internalLogger
        ) ?: return false

        val byteArrayView = eventSerializer.serializeToByteArray(
            viewEvent,
            sdkCore.internalLogger
        ) ?: return false

        val eventMeta = RumEventMeta.ViewUpdate(
            viewId = event.view.id,
            documentVersion = event.dd.documentVersion
        )
        val serializedEventMeta =
            eventMetaSerializer.serializeToByteArray(eventMeta, sdkCore.internalLogger)
                ?: EMPTY_BYTE_ARRAY

        val batchEvent = RawBatchEvent(
            data = byteArray,
            metadata = serializedEventMeta
        )

        synchronized(this) {
            val result = writer.write(batchEvent, null, eventType)
            if (result) {
                sdkCore.writeLastViewEvent(byteArrayView)
            }
            return result
        }
    }

    private fun writeOtherEvent(writer: EventBatchWriter, event: Any, eventType: EventType): Boolean {
        val mappedElement = eventMapper.map(event) ?: return false

        val byteArray = eventSerializer.serializeToByteArray(
            mappedElement,
            sdkCore.internalLogger
        ) ?: return false

        val batchEvent = RawBatchEvent(data = byteArray)

        return synchronized(this) {
            writer.write(batchEvent, null, eventType)
        }
    }

    companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
