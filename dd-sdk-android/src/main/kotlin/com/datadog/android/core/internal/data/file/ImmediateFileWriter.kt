/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.core.internal.utils.use
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.IllegalStateException

internal class ImmediateFileWriter<T : Any>(
    private val fileOrchestrator: Orchestrator,
    private val serializer: Serializer<T>,
    separator: CharSequence = PayloadDecoration.JSON_ARRAY_DECORATION.separator
) : Writer<T> {

    private val separatorBytes = separator.toString().toByteArray(Charsets.UTF_8)

    // region Writer

    override fun write(model: T) {
        consume(model)
    }

    override fun write(models: List<T>) {
        models.forEach {
            consume(it)
        }
    }

    // endregion

    // region Internal

    private fun consume(model: T) {
        val data = serializer.serialize(model)

        if (data.length >= MAX_ITEM_SIZE) {
            devLogger.e("Unable to persist data, serialized size is too big\n$data")
        } else {
            synchronized(this) {
                writeData(data)
            }
        }
    }

    private fun writeData(data: String) {
        var file: File? = null
        try {
            val dataAsByteArray = data.toByteArray(Charsets.UTF_8)
            file = fileOrchestrator.getWritableFile(dataAsByteArray.size)
            if (file != null) {
                writeDataToFile(file, dataAsByteArray)
            } else {
                sdkLogger.e("Could not get a valid file")
            }
        } catch (e: FileNotFoundException) {
            sdkLogger.e("Couldn't create an output stream to file ${file?.path}", e)
        } catch (e: IOException) {
            sdkLogger.e("Couldn't write data to file ${file?.path}", e)
        } catch (e: SecurityException) {
            sdkLogger.e("Couldn't access file ${file?.path}", e)
        }
    }

    private fun writeDataToFile(file: File, dataAsByteArray: ByteArray) {
        try {
            val outputStream = FileOutputStream(file, true)
            outputStream.use { stream ->
                lockFileAndWriteData(stream, file, dataAsByteArray)
            }
        } catch (e: IllegalStateException) {
            sdkLogger.e(
                "Exception when trying to lock the file: [${file.canonicalPath}] ", e
            )
        } catch (e: IOException) {
            sdkLogger.e(
                "Exception when trying to write data to: [${file.canonicalPath}] ", e
            )
        }
    }

    private fun lockFileAndWriteData(
        stream: FileOutputStream,
        file: File,
        dataAsByteArray: ByteArray
    ) {
        stream.channel.lock().use {
            if (file.length() > 0) {
                stream.write(separatorBytes)
            }
            stream.write(dataAsByteArray)
        }
    }

    // endregion

    companion object {
        private const val MAX_ITEM_SIZE = 256 * 1024 // 256 Kb
    }
}
