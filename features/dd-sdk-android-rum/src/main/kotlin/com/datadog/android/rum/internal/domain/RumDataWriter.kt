/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import androidx.annotation.WorkerThread
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.serializeToByteArray
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.android.v2.core.storage.DataWriter

internal class RumDataWriter(
    internal val serializer: Serializer<Any>,
    private val sdkCore: InternalSdkCore
) : DataWriter<Any> {

    // region DataWriter

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: Any): Boolean {
        val byteArray = serializer.serializeToByteArray(
            element,
            sdkCore.internalLogger
        ) ?: return false

        synchronized(this) {
            val result = writer.write(byteArray, null)
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
            is ViewEvent -> persistViewEvent(rawData)
            is ActionEvent -> notifyEventSent(
                data.view.id,
                StorageEvent.Action(data.action.frustration?.type?.size ?: 0)
            )
            is ResourceEvent -> notifyEventSent(data.view.id, StorageEvent.Resource)
            is ErrorEvent -> {
                if (data.error.isCrash != true) {
                    notifyEventSent(data.view.id, StorageEvent.Error)
                }
            }
            is LongTaskEvent -> {
                if (data.longTask.isFrozenFrame == true) {
                    notifyEventSent(data.view.id, StorageEvent.FrozenFrame)
                } else {
                    notifyEventSent(data.view.id, StorageEvent.LongTask)
                }
            }
        }
    }

    @WorkerThread
    private fun persistViewEvent(data: ByteArray) {
        sdkCore.writeLastViewEvent(data)
    }

    private fun notifyEventSent(viewId: String, storageEvent: StorageEvent) {
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        if (rumMonitor is AdvancedRumMonitor) {
            rumMonitor.eventSent(viewId, storageEvent)
        }
    }

    // endregion
}
