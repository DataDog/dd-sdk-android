/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.utils.sdkLogger
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class FileReader(
    internal val fileOrchestrator: Orchestrator,
    private val dataDirectory: File,
    private val prefix: CharSequence = "",
    private val suffix: CharSequence = ""
) : Reader {

    private val lockedFiles: MutableSet<String> = mutableSetOf()

    // region LogReader

    override fun readNextBatch(): Batch? {
        val (file, data) = readNextFile()

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
        synchronized(lockedFiles) {
            lockedFiles.remove(batchId)
        }
    }

    override fun dropBatch(batchId: String) {
        sdkLogger.i("dropBatch $batchId")
        val fileToDelete = File(dataDirectory, batchId)
        if (deleteFile(fileToDelete)) {
            releaseBatch(batchId)
        }
    }

    override fun dropAllBatches() {
        sdkLogger.i("dropAllBatches")
        fileOrchestrator
            .getAllFiles()
            .forEach {
                if (deleteFile(it)) {
                    releaseBatch(it.name)
                }
            }
    }

    // endregion

    // region Internal

    private fun readNextFile(): Pair<File?, ByteArray> {
        val file = lockAndGetFile()
        return if (file != null) {
            val data = try {
                file.readBytes(withPrefix = prefix, withSuffix = suffix)
            } catch (e: FileNotFoundException) {
                sdkLogger.e("Couldn't create an input stream from file ${file?.path}", e)
                ByteArray(0)
            } catch (e: IOException) {
                sdkLogger.e("Couldn't read messages from file ${file?.path}", e)
                ByteArray(0)
            }
            file to data
        } else {
            file to ByteArray(0)
        }
    }

    private fun lockAndGetFile(): File? {
        var file: File? = null
        try {
            synchronized(lockedFiles) {
                val readFile = fileOrchestrator.getReadableFile(lockedFiles.toSet())
                if (readFile != null) {
                    lockedFiles.add(readFile.name)
                }
                file = readFile
            }
        } catch (e: SecurityException) {
            sdkLogger.e("Couldn't access file ${file?.path}", e)
            ByteArray(0)
        } catch (e: OutOfMemoryError) {
            sdkLogger.e("Couldn't read file ${file?.path} (not enough memory)", e)
            ByteArray(0)
        }
        return file
    }

    private fun deleteFile(fileToDelete: File): Boolean {
        if (fileToDelete.exists()) {
            if (fileToDelete.delete()) {
                sdkLogger.d("File ${fileToDelete.path} deleted")
                return true
            } else {
                sdkLogger.e("Error deleting file ${fileToDelete.path}")
            }
        } else {
            sdkLogger.w("file ${fileToDelete.path} does not exist")
        }

        return false
    }

    // endregion
}
