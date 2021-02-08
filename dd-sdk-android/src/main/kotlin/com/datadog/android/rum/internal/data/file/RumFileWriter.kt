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
import com.datadog.android.rum.internal.domain.event.RumEvent
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
        if (writeDataSuccess && model.event is ViewEvent) {
            if (!lastViewEventFile.exists()) {
                lastViewEventFile.createNewFile()
            }
            // persist the serialised ViewEvent in the NDK crash data folder
            fileHandler.writeData(lastViewEventFile, data)
        }
        return writeDataSuccess
    }

    // endregion

    companion object {
        internal const val LAST_VIEW_EVENT_FILE_NAME = "last_view_event"
    }
}
