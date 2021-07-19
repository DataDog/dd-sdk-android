/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileDataWriter
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.EventType
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import java.io.File

internal class RumDataWriter(
    fileOrchestrator: FileOrchestrator,
    serializer: Serializer<RumEvent>,
    decoration: PayloadDecoration,
    handler: FileHandler,
    private val lastViewEventFile: File
) : BatchFileDataWriter<RumEvent>(
    fileOrchestrator,
    serializer,
    decoration,
    handler
) {

    override fun onDataWritten(data: RumEvent, rawData: ByteArray) {
        val event = data.event
        when (event) {
            is ViewEvent -> persistViewEvent(rawData)
            is ActionEvent -> notifyEventSent(event.view.id, EventType.ACTION)
            is ResourceEvent -> notifyEventSent(event.view.id, EventType.RESOURCE)
            is ErrorEvent -> {
                if (event.error.isCrash != true) {
                    notifyEventSent(event.view.id, EventType.ERROR)
                }
            }
            is LongTaskEvent -> notifyEventSent(event.view.id, EventType.LONG_TASK)
        }
    }

    // endregion

    // region Internal

    private fun persistViewEvent(data: ByteArray) {
        handler.writeData(lastViewEventFile, data)
    }

    private fun notifyEventSent(viewId: String, eventType: EventType) {
        val rumMonitor = GlobalRum.get()
        if (rumMonitor is AdvancedRumMonitor) {
            rumMonitor.eventSent(viewId, eventType)
        }
    }

    // endregion
}
