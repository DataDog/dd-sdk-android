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
import com.datadog.android.rum.internal.domain.event.RumEventMeta
import com.datadog.android.rum.model.ViewEvent

internal class RumDataWriter(
    internal val eventSerializer: Serializer<Any>,
    private val eventMetaSerializer: Serializer<RumEventMeta>,
    private val sdkCore: InternalSdkCore
) : DataWriter<Any> {

    // region DataWriter

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: Any, eventType: EventType): Boolean {
        val byteArray = eventSerializer.serializeToByteArray(
            element,
            sdkCore.internalLogger
        ) ?: return false

        val batchEvent = if (element is ViewEvent) {
            val hasAccessibility = if (element.view.accessibility != null) {
                isAccessibilityPopulated(element.view.accessibility)
            } else {
                false
            }

            val eventMeta = RumEventMeta.View(
                viewId = element.view.id,
                documentVersion = element.dd.documentVersion,
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

    private fun isAccessibilityPopulated(accessibility: ViewEvent.Accessibility): Boolean {
        return setOf<Any?>(
            accessibility.textSize,
            accessibility.assistiveSwitchEnabled,
            accessibility.assistiveTouchEnabled,
            accessibility.boldTextEnabled,
            accessibility.buttonShapesEnabled,
            accessibility.closedCaptioningEnabled,
            accessibility.grayscaleEnabled,
            accessibility.increaseContrastEnabled,
            accessibility.invertColorsEnabled,
            accessibility.monoAudioEnabled,
            accessibility.onOffSwitchLabelsEnabled,
            accessibility.reduceMotionEnabled,
            accessibility.reduceTransparencyEnabled,
            accessibility.reducedAnimationsEnabled,
            accessibility.rtlEnabled,
            accessibility.screenReaderEnabled,
            accessibility.shakeToUndoEnabled,
            accessibility.shouldDifferentiateWithoutColor,
            accessibility.singleAppModeEnabled,
            accessibility.speakScreenEnabled,
            accessibility.speakSelectionEnabled,
            accessibility.videoAutoplayEnabled
        ).any { it != null }
    }

    // endregion

    companion object {
        val EMPTY_BYTE_ARRAY = ByteArray(0)
    }
}
