/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.data.file

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.domain.Serializer
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

internal class RumFileWriter(
    ndkCrashDataDirectory: File,
    fileOrchestrator: Orchestrator,
    serializer: Serializer<RumEvent>,
    separator: CharSequence = PayloadDecoration.JSON_ARRAY_DECORATION.separator
) : ImmediateFileWriter<RumEvent>(fileOrchestrator, serializer, separator) {

    private val lastViewEventFile: File

    init {
        ndkCrashDataDirectory.mkdirs()
        lastViewEventFile = File(ndkCrashDataDirectory, LAST_VIEW_EVENT_FILE_NAME)
    }

    // region ImmediateFileWriter

    override fun writeData(data: ByteArray, model: RumEvent): Boolean {
        val writeDataSuccess = super.writeData(data, model)
        if (writeDataSuccess) {
            val event = model.event
            when (event) {
                is ViewEvent -> persistViewEvent(data)
                is ActionEvent -> notifyEventSent(event.view.id, EventType.ACTION)
                is ResourceEvent -> notifyEventSent(event.view.id, EventType.RESOURCE)
                is ErrorEvent -> {
                    if (event.error.isCrash == true) {
                        notifyEventSent(event.view.id, EventType.CRASH)
                    } else {
                        notifyEventSent(event.view.id, EventType.ERROR)
                    }
                }
                is LongTaskEvent -> notifyEventSent(event.view.id, EventType.LONG_TASK)
            }
        }
        return writeDataSuccess
    }

    // endregion

    // region Internal

    private fun persistViewEvent(data: ByteArray) {
        if (!lastViewEventFile.exists()) {
            lastViewEventFile.createNewFile()
        }
        // persist the serialised ViewEvent in the NDK crash data folder
        fileHandler.writeData(lastViewEventFile, data)
    }

    private fun notifyEventSent(viewId: String, eventType: EventType) {
        val rumMonitor = GlobalRum.get()
        if (rumMonitor is AdvancedRumMonitor) {
            rumMonitor.eventSent(viewId, eventType)
        }
    }

    // endregion

    companion object {
        internal const val LAST_VIEW_EVENT_FILE_NAME = "last_view_event"
    }
}
