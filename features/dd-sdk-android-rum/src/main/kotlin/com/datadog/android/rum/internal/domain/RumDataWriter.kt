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
import com.datadog.android.rum.internal.domain.event.RumEventMeta
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.VitalAppLaunchEvent
import com.datadog.android.rum.model.VitalOperationStepEvent
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent

internal class RumDataWriter(
    internal val eventSerializer: Serializer<Any>,
    private val eventMetaSerializer: Serializer<RumEventMeta>,
    private val sdkCore: InternalSdkCore
) : DataWriter<Any> {

    // region DataWriter

    @WorkerThread
    @Suppress("ReturnCount")
    override fun write(writer: EventBatchWriter, element: Any, eventType: EventType): Boolean {
        val byteArray = eventSerializer.serializeToByteArray(element, sdkCore.internalLogger)
            ?: return false

        val batchEvent = if (element is ViewEvent) {
            val eventMeta = RumEventMeta.View(
                viewId = element.view.id,
                documentVersion = element.dd.documentVersion,
                hasAccessibility = element.view.accessibility != null
            )

            val serializedEventMeta = eventMetaSerializer.serializeToByteArray(eventMeta, sdkCore.internalLogger)
                ?: EMPTY_BYTE_ARRAY

            RawBatchEvent(
                data = byteArray,
                metadata = serializedEventMeta
            )
        } else {
            RawBatchEvent(data = byteArray)
        }

        synchronized(this) {
            val telemetryContext = TelemetryContext(
                featureName = Feature.RUM_FEATURE_NAME,
                eventType = resolveEventType(element)
            )

            val result = writer.write(batchEvent, null, eventType, telemetryContext)
            if (result) {
                onDataWritten(element, byteArray)
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

        private const val UNKNOWN_EVENT_TYPE = "unknown"

        private fun resolveEventType(event: Any): String = when (event) {
            is ActionEvent -> event.type
            is ErrorEvent -> event.type
            is LongTaskEvent -> event.type
            is ResourceEvent -> event.type
            is ViewEvent -> event.type
            is VitalAppLaunchEvent -> event.type
            is VitalOperationStepEvent -> event.type
            is TelemetryConfigurationEvent -> event.type
            is TelemetryDebugEvent -> event.type
            is TelemetryErrorEvent -> event.type
            is TelemetryUsageEvent -> event.type
            else -> UNKNOWN_EVENT_TYPE
        }
    }
}
