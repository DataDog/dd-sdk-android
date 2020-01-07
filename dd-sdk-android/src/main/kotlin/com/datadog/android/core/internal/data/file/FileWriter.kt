/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.data.DataMigrator
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.threading.LazyHandlerThread
import com.datadog.android.log.internal.utils.sdkLogger
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

internal class FileWriter<T : Any>(
    private val fileOrchestrator: Orchestrator,
    private val serializer: Serializer<T>,
    private val dataMigrator: DataMigrator
) : LazyHandlerThread(THREAD_NAME),
    Writer<T> {

    init {
        start()
        post(Runnable {
            dataMigrator.migrateData()
        })
    }

    // region File Writer

    override fun write(model: T) {
        post(Runnable {
            val data = serializer.serialize(model)

            if (data.length >= MAX_LOG_SIZE) {
                // TODO RUMM-49 warn user that the log is too big !
            } else {
                synchronized(this) {
                    writeData(data)
                }
            }
        })
    }

    private fun writeData(data: String) {
        var file: File? = null
        try {
            val dataAsByteArray = data.toByteArray(Charsets.UTF_8)
            file = fileOrchestrator.getWritableFile(dataAsByteArray.size)
            if (file != null) {
                writeDataToFile(file, dataAsByteArray)
            } else {
                sdkLogger.e("$TAG: Could not write on a null file")
            }
        } catch (e: FileNotFoundException) {
            sdkLogger.e("$TAG: Couldn't create an output stream to file ${file?.path}", e)
        } catch (e: IOException) {
            sdkLogger.e("$TAG: Couldn't write data to file ${file?.path}", e)
        } catch (e: SecurityException) {
            sdkLogger.e("$TAG: Couldn't access file ${file?.path}", e)
        }
    }

    private fun writeDataToFile(file: File, dataAsByteArray: ByteArray) {
        val fileSize = file.length()
        FileOutputStream(file, true).use {
            if (fileSize > 0) {
                it.write(separator)
            }
            it.write(dataAsByteArray)
        }
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
