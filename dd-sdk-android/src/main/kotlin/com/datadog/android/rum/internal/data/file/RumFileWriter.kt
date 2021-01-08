/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.data.file

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.file.DefaultFileWriter
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.model.ViewEvent
import java.io.File

internal class RumFileWriter(
    ndkCrashDataDirectory: File,
    fileOrchestrator: Orchestrator,
    serializer: Serializer<RumEvent>,
    separator: CharSequence = PayloadDecoration.JSON_ARRAY_DECORATION.separator
) : DefaultFileWriter<RumEvent>(fileOrchestrator, serializer, separator) {

    private val lastViewEventFile: File

    init {
        ndkCrashDataDirectory.mkdirs()
        lastViewEventFile = File(ndkCrashDataDirectory, LAST_VIEW_EVENT_FILE_NAME)
    }

    // region DefaultFileWriter

    override fun serialiseEvent(model: RumEvent): String? {
        val serialisedEvent = super.serialiseEvent(model)
        if (serialisedEvent != null && model.event is ViewEvent) {
            // persist the serialised ViewEvent in the NDK crash data folder
            writeDataToFile(lastViewEventFile, serialisedEvent.toByteArray(Charsets.UTF_8), false)
        }
        return serialisedEvent
    }

    // endregion

    companion object {
        internal const val LAST_VIEW_EVENT_FILE_NAME = "last_view_event"
    }
}
