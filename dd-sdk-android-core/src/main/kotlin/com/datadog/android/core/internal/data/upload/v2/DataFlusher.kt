/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload.v2

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.core.internal.persistence.file.existsSafe

// TODO RUMM-0000 Should replace com.datadog.android.core.internal.net.DataFlusher once
//  features are configured as V2
internal class DataFlusher(
    internal val contextProvider: ContextProvider,
    internal val fileOrchestrator: FileOrchestrator,
    internal val fileReader: BatchFileReader,
    internal val metadataFileReader: FileReader<ByteArray>,
    internal val fileMover: FileMover,
    private val internalLogger: InternalLogger
) : Flusher {

    @WorkerThread
    override fun flush(uploader: DataUploader) {
        val context = contextProvider.context

        val toUploadFiles = fileOrchestrator.getFlushableFiles()
        toUploadFiles.forEach {
            val batch = fileReader.readData(it)
            val metaFile = fileOrchestrator.getMetadataFile(it)
            val meta = if (metaFile != null && metaFile.existsSafe(internalLogger)) {
                metadataFileReader.readData(metaFile)
            } else {
                null
            }
            uploader.upload(context, batch, meta)
            fileMover.delete(it)
            if (metaFile?.existsSafe(internalLogger) == true) {
                fileMover.delete(metaFile)
            }
        }
    }
}
