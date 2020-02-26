/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.domain.Batch
import com.datadog.android.core.internal.utils.sdkLogger
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class FileReader(
    private val fileOrchestrator: Orchestrator,
    private val dataDirectory: File
) : Reader {

    private val readFiles: MutableSet<String> = mutableSetOf()
    private val sentBatches: MutableSet<String> = mutableSetOf()

    // region LogReader

    override fun readNextBatch(): Batch? {
        val (file, data) = synchronized(readFiles) {
            readNextFile()
        }

        return if (file == null) {
            null
        } else {
            Batch(
                file.name,
                data
            )
        }
    }

    override fun releaseBatch(batchId: String) {
        sdkLogger.i("releaseBatch $batchId")
        synchronized(readFiles) {
            readFiles.remove(batchId)
        }
    }

    override fun dropBatch(batchId: String) {
        sdkLogger.i("dropBatch $batchId")
        sentBatches.add(batchId)
        readFiles.remove(batchId)
        val fileToDelete = File(dataDirectory, batchId)

        deleteFile(fileToDelete)
    }

    override fun dropAllBatches() {
        sdkLogger.i("dropAllBatches")
        fileOrchestrator.getAllFiles().forEach { deleteFile(it) }
    }

    // endregion

    // region Internal

    private fun readNextFile(): Pair<File?, ByteArray> {
        var file: File? = null
        val data = try {
            file = fileOrchestrator.getReadableFile(sentBatches.union(readFiles))
            if (file == null) {
                ByteArray(0)
            } else {
                readFiles.add(file.name)
                file.readBytes(withPrefix = '[', withSuffix = ']')
            }
        } catch (e: FileNotFoundException) {
            sdkLogger.e("Couldn't create an input stream from file ${file?.path}", e)
            ByteArray(0)
        } catch (e: IOException) {
            sdkLogger.e("Couldn't read messages from file ${file?.path}", e)
            ByteArray(0)
        } catch (e: SecurityException) {
            sdkLogger.e("Couldn't access file ${file?.path}", e)
            ByteArray(0)
        }

        return file to data
    }

    private fun deleteFile(fileToDelete: File) {
        if (fileToDelete.exists()) {
            if (fileToDelete.delete()) {
                sdkLogger.d("File ${fileToDelete.path} deleted")
            } else {
                sdkLogger.e("Error deleting file ${fileToDelete.path}")
            }
        } else {
            sdkLogger.w("file ${fileToDelete.path} does not exist")
        }
    }

    // endregion
}
