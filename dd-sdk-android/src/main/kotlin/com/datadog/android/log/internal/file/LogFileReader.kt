/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.annotation.TargetApi
import android.os.Build
import android.util.Base64 as AndroidBase64
import com.datadog.android.log.internal.Batch
import com.datadog.android.log.internal.LogReader
import com.datadog.android.log.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.split
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Base64 as JavaBase64

internal class LogFileReader(
    private val fileOrchestrator: FileOrchestrator,
    private val rootDirectory: File
) : LogReader {

    private val sentBatches: MutableSet<String> = mutableSetOf()

    // region LogReader

    override fun readNextBatch(): Batch? {
        val nextLogFile = fileOrchestrator.getReadableFile(sentBatches) ?: return null

        val logs = try {
            val inputBytes = nextLogFile.readBytes()
            inputBytes.split(LogFileStrategy.SEPARATOR_BYTE)
        } catch (e: FileNotFoundException) {
            sdkLogger.e("$TAG: Couldn't create an input stream from file ${nextLogFile.path}", e)
            emptyList<ByteArray>()
        } catch (e: IOException) {
            sdkLogger.e("$TAG: Couldn't read logs from file ${nextLogFile.path}", e)
            emptyList<ByteArray>()
        }

        return Batch(nextLogFile.name, logs.map { deobfuscate(it) })
    }

    override fun dropBatch(batchId: String) {
        sdkLogger.i("$TAG: onBatchSent $batchId")
        sentBatches.add(batchId)
        val fileToDelete = File(rootDirectory, batchId)

        if (fileToDelete.exists()) {
            if (fileToDelete.delete()) {
                sdkLogger.d("$TAG: File ${fileToDelete.path} deleted")
            } else {
                sdkLogger.e("$TAG: Error deleting file ${fileToDelete.path}")
            }
        } else {
            sdkLogger.w("$TAG: Sent batch with  unknown id $batchId")
        }
    }

    // endregion

    // region Internal

    private fun deobfuscate(input: ByteArray): String {
        val output = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deobfuscateApi26(input)
        } else {
            AndroidBase64.decode(input, AndroidBase64.DEFAULT)
        }

        return String(output, Charsets.UTF_8)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun deobfuscateApi26(input: ByteArray): ByteArray {
        val decoder = JavaBase64.getDecoder()
        return decoder.decode(input)
    }

    // endregion

    companion object {
        val TAG = "LogFileReader"
    }
}
