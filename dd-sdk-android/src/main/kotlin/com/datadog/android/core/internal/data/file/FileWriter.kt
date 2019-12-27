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
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.threading.LazyHandlerThread
import com.datadog.android.log.internal.utils.sdkLogger
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Base64 as JavaBase64

internal class FileWriter<T : Any>(
    private val fileOrchestrator: Orchestrator,
    rootDirectory: File,
    private val serializer: Serializer<T>
) : LazyHandlerThread(THREAD_NAME),
    Writer<T> {

    // TODO: RUMM-148 Clean this code from here and move it into the FileOrchestrator eventually
    private val writeable: Boolean = if (!rootDirectory.exists()) {
        rootDirectory.mkdirs()
    } else {
        rootDirectory.isDirectory
    }

    init {
        if (!writeable) {
            sdkLogger.e(
                "$TAG: Can't write data on disk: directory ${rootDirectory.path} is invalid."
            )
        } else {
            start()
        }
    }

    // region LogWriter

    override fun write(model: T) {
        if (!writeable) return

        post(Runnable {
            val data = serializer.serialize(model)

            if (data.length >= MAX_LOG_SIZE) {
                // TODO RUMM-49 warn user that the log is too big !
            } else {
                writeData(data)
            }
        })
    }

    private fun obfuscateAndWriteData(data: String) {
        val obfData = obfuscate(data)

        synchronized(this) {
            writeLogSafely(obfData)
        }
    }

    private fun writeData(data: String) {
        var file: File? = null
        try {
            val dataAsByteArray =data.toByteArray(Charsets.UTF_8)
            file = fileOrchestrator.getWritableFile(dataAsByteArray.size)
            val fileSize = file.length()
            if(fileSize>0){
                file.appendBytes(separator)
            }
            file.appendBytes(dataAsByteArray)
        } catch (e: FileNotFoundException) {
            sdkLogger.e("$TAG: Couldn't create an output stream to file ${file?.path}", e)
        } catch (e: IOException) {
            sdkLogger.e("$TAG: Couldn't write data to file ${file?.path}", e)
        } catch (e: SecurityException) {
            sdkLogger.e("$TAG: Couldn't access file ${file?.path}", e)
        }
    }

    private fun writeLogSafely(obfData: ByteArray) {
        var file: File? = null
        try {
            file = fileOrchestrator.getWritableFile(obfData.size)
            file.appendBytes(obfData)
            file.appendBytes(separator)
        } catch (e: FileNotFoundException) {
            sdkLogger.e("$TAG: Couldn't create an output stream to file ${file?.path}", e)
        } catch (e: IOException) {
            sdkLogger.e("$TAG: Couldn't write data to file ${file?.path}", e)
        } catch (e: SecurityException) {
            sdkLogger.e("$TAG: Couldn't access file ${file?.path}", e)
        }
    }

    private fun obfuscate(model: String): ByteArray {
        val input = model.toByteArray(Charsets.UTF_8)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            obfuscateApi26(input)
        } else {
            AndroidBase64.encode(input, AndroidBase64.NO_WRAP)
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun obfuscateApi26(input: ByteArray): ByteArray {
        val encoder = JavaBase64.getEncoder()
        return encoder.encode(input)
    }

    // endregion

    companion object {

        private val separator = ByteArray(1) { SEPARATOR_BYTE }
        internal const val SEPARATOR_BYTE: Byte = ','.toByte()

        private const val THREAD_NAME = "ddog_w"

        private const val MAX_LOG_SIZE = 256 * 1024 // 256 Kb

        private const val TAG = "FileWriter"
    }
}
