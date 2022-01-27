/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator

internal class DataFlusher(
    internal val fileOrchestrator: FileOrchestrator,
    internal val decoration: PayloadDecoration,
    internal val handler: FileHandler
) : Flusher {

    override fun flush(uploader: DataUploader) {
        val toUploadFiles = fileOrchestrator.getFlushableFiles()
        toUploadFiles.forEach {
            val batch = handler.readData(
                it,
                decoration.prefixBytes,
                decoration.suffixBytes,
                decoration.separatorBytes
            )
            uploader.upload(batch)
            handler.delete(it)
        }
    }
}
