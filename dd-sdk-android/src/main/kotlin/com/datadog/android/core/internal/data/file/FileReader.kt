/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import android.annotation.TargetApi
import android.os.Build
import android.util.Base64 as AndroidBase64
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.domain.Batch
import com.datadog.android.log.internal.file.LogFileStrategy
import com.datadog.android.log.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.split
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.IllegalArgumentException
import java.util.Base64 as JavaBase64

internal class FileReader(
    private val fileOrchestrator: Orchestrator,
    private val rootDirectory: File
) : Reader {

    private val sentBatches: MutableSet<String> = mutableSetOf()

    // region LogReader

    override fun readNextBatch(): Batch? {
        var file: File? = null
        val logs = try {
            file = fileOrchestrator.getReadableFile(sentBatches) ?: return null
            val inputBytes = file.readBytes()
            inputBytes.split(LogFileStrategy.SEPARATOR_BYTE)
        } catch (e: FileNotFoundException) {
            sdkLogger.e("$TAG: Couldn't create an input stream from file ${file?.path}", e)
            emptyList<ByteArray>()
        } catch (e: IOException) {
            sdkLogger.e("$TAG: Couldn't read logs from file ${file?.path}", e)
            emptyList<ByteArray>()
        } catch (e: SecurityException) {
            sdkLogger.e("$TAG: Couldn't access file ${file?.path}", e)
            emptyList<ByteArray>()
        }

        return if (file == null) {
            null
        } else {
            Batch(
                file.name,
                logs.mapNotNull { deobfuscate(it) })
        }
    }

    override fun dropBatch(batchId: String) {
        sdkLogger.i("$TAG: dropBatch $batchId")
        sentBatches.add(batchId)
        val fileToDelete = File(rootDirectory, batchId)

        deleteFile(fileToDelete)
    }

    override fun dropAllBatches() {
        sdkLogger.i("$TAG: dropAllBatches")
        fileOrchestrator.getAllFiles().forEach { deleteFile(it) }
    }

    // endregion

    // region Internal

    private fun deobfuscate(input: ByteArray): String? {
        val output = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                deobfuscateApi26(input)
            } else {
                AndroidBase64.decode(input, AndroidBase64.DEFAULT)
            }
        } catch (e: IllegalArgumentException) {
            sdkLogger.e("Invalid log found, dropping it", e)
            return null
        }

        return String(output, Charsets.UTF_8)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun deobfuscateApi26(input: ByteArray): ByteArray {
        val decoder = JavaBase64.getDecoder()
        return decoder.decode(input)
    }

    private fun deleteFile(fileToDelete: File) {
        if (fileToDelete.exists()) {
            if (fileToDelete.delete()) {
                sdkLogger.d("$TAG: File ${fileToDelete.path} deleted")
            } else {
                sdkLogger.e("$TAG: Error deleting file ${fileToDelete.path}")
            }
        } else {
            sdkLogger.w("$TAG: file ${fileToDelete.path} does not exist")
        }
    }

    // endregion

    companion object {
        private const val TAG = "FileReader"
    }
}
