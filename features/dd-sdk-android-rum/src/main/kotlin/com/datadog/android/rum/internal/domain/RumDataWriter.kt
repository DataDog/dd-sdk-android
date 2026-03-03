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
import com.datadog.android.rum.model.ViewUpdateEvent
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
        val mappedElement = when (element) {
            is ViewEvent, is ViewUpdateEvent -> element
            else -> eventMapper.map(element) ?: return false
        }

        val byteArray = eventSerializer.serializeToByteArray(
            mappedElement,
            sdkCore.internalLogger
        ) ?: return false

        val batchEvent = if (mappedElement is ViewEvent) {
            val hasAccessibility = mappedElement.view.accessibility != null

            val eventMeta = RumEventMeta.View(
                viewId = mappedElement.view.id,
                documentVersion = mappedElement.dd.documentVersion,
                hasAccessibility = hasAccessibility
            )
            val serializedEventMeta =
                eventMetaSerializer.serializeToByteArray(eventMeta, sdkCore.internalLogger)
                    ?: EMPTY_BYTE_ARRAY
            RawBatchEvent(
                data = byteArray,
                metadata = serializedEventMeta
            )
        } else {
            RawBatchEvent(data = byteArray)
        }

        synchronized(this) {
            val result = writer.write(batchEvent, null, eventType)
            if (result) {
                onDataWritten(mappedElement, byteArray)
            }
            return result
        }
    }

    // endregion

    // region Internal

    @WorkerThread
    internal fun onDataWritten(data: Any, rawData: ByteArray) {
        when (data) {
            is ViewEvent -> sdkCore.writeLastViewEvent(rawData)
        }
    }

    // endregion

    companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
