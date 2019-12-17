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
import java.io.File
import java.util.Base64 as JavaBase64

internal class LogFileReader(
    private val fileOrchestrator: FileOrchestrator,
    private val rootDirectory: File
) : LogReader {

    private val sentBatches: MutableSet<String> = mutableSetOf()

    // region LogReader

    override fun readNextBatch(): Batch? {
        val nextLogFile = fileOrchestrator.getReadableFile(sentBatches) ?: return null
        var inputBytes = nextLogFile.readBytes()
//        var logs = inputBytes.toString(Charsets.UTF_8)

//        logs = "[" + logs + "]"

        return Batch(nextLogFile.name, inputBytes)
    }

    fun File.readBytes(): ByteArray = inputStream().use { input ->
        var offset = 1
        val length = length()
        if (length > Int.MAX_VALUE) throw OutOfMemoryError("File $this is too big ($length bytes) to fit in memory.")
        var remaining =length.toInt()
        val result = ByteArray(remaining + 2)
        while (remaining > 0) {
            val read = input.read(result, offset, remaining)
            if (read < 0) break
            remaining -= read
            offset += read
        }
        result[offset]=']'.toByte()
        result[0]='['.toByte()
        offset += 1
        if (remaining == 0) result else result.copyOf(offset)
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
        val TAG = "LogFileReader"
    }
}
