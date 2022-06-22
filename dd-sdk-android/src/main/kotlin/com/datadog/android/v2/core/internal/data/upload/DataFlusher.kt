/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.data.upload

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.net.DataUploader

// TODO RUMM-0000 Should replace com.datadog.android.core.internal.net.DataFlusher once
//  features are configured as V2
internal class DataFlusher(
    internal val contextProvider: ContextProvider,
    internal val fileOrchestrator: FileOrchestrator,
    internal val fileReader: BatchFileReader,
    internal val metadataFileReader: FileReader,
    internal val fileMover: FileMover
) : Flusher {

    @WorkerThread
    override fun flush(uploader: DataUploader) {
        val context = contextProvider.context ?: return

        val toUploadFiles = fileOrchestrator.getFlushableFiles()
        toUploadFiles.forEach {
            val batch = fileReader.readData(it)
            val metaFile = fileOrchestrator.getMetadataFile(it)
            val meta = if (metaFile != null) metadataFileReader.readData(metaFile) else null
            uploader.upload(context, batch, meta)
            fileMover.delete(it)
            if (metaFile?.existsSafe() == true) {
                fileMover.delete(metaFile)
            }
        }
    }
}
