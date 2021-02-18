/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.file.FileHandler
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import com.datadog.android.core.internal.domain.Serializer

internal class NdkFileWriter<T : Any>(
    fileOrchestrator: Orchestrator,
    serializer: Serializer<T>,
    fileHandler: FileHandler = FileHandler()
) : ImmediateFileWriter<T>(fileOrchestrator, serializer, "", fileHandler) {

    override fun writeData(data: ByteArray, model: T): Boolean {
        val file = getFile(data)
        return if (file != null) {
            fileHandler.writeData(file, data)
        } else {
            false
        }
    }
}
