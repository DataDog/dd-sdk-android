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

internal open class ImmediateFileWriter<T : Any>(
    internal val fileOrchestrator: Orchestrator,
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

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun consume(model: T) {
        serialiseEvent(model)?.let {
            persistData(it, model)
        }
    }

    // region Protected

    protected open fun writeData(data: ByteArray, model: T) {
        val file = try {
            fileOrchestrator.getWritableFile(data.size)
        } catch (e: SecurityException) {
            sdkLogger.e("Unable to access batch file directory", e)
            null
        }

        if (file != null) {
            writeDataToFile(file, data)
        } else {
            sdkLogger.e("Could not get a valid file")
        }
    }

    protected fun writeDataToFile(
        file: File,
        dataAsByteArray: ByteArray,
        append: Boolean = true,
        withSeparator: Boolean = true
    ) {
        try {
            val outputStream = FileOutputStream(file, append)
            outputStream.use { stream ->
                lockFileAndWriteData(stream, file, dataAsByteArray, withSeparator)
            }
        } catch (e: IllegalStateException) {
            sdkLogger.e("Exception when trying to lock the file: [${file.canonicalPath}] ", e)
        } catch (e: FileNotFoundException) {
            sdkLogger.e("Couldn't create an output stream to file ${file.path}", e)
        } catch (e: IOException) {
            sdkLogger.e("Exception when trying to write data to: [${file.canonicalPath}] ", e)
        }
    }

    // endregion

    // region Internal

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun serialiseEvent(model: T): String? {
        return try {
            serializer.serialize(model)
        } catch (e: Throwable) {
            sdkLogger.w("Unable to serialize ${model.javaClass.simpleName}", e)
            null
        }
    }

    private fun persistData(data: String, model: T) {
        if (data.length >= MAX_ITEM_SIZE) {
            devLogger.e("Unable to persist data, serialized size is too big\n$data")
        } else {
            synchronized(this) {
                writeData(data.toByteArray(Charsets.UTF_8), model)
            }
        }
    }

    private fun lockFileAndWriteData(
        stream: FileOutputStream,
        file: File,
        dataAsByteArray: ByteArray,
        withSeparator: Boolean = true
    ) {
        stream.channel.lock().use {
            if (file.length() > 0 && withSeparator) {
                stream.write(separatorBytes + dataAsByteArray)
            } else {
                stream.write(dataAsByteArray)
            }
        }
    }

    // endregion

    companion object {
        private const val MAX_ITEM_SIZE = 256 * 1024 // 256 Kb
    }
}
