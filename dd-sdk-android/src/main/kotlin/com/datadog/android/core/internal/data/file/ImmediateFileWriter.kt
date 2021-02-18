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
import java.io.File

internal open class ImmediateFileWriter<T : Any>(
    internal val fileOrchestrator: Orchestrator,
    private val serializer: Serializer<T>,
    separator: CharSequence = PayloadDecoration.JSON_ARRAY_DECORATION.separator,
    protected val fileHandler: FileHandler = FileHandler()
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

    // region Protected

    protected open fun writeData(data: ByteArray, model: T): Boolean {
        val file = getFile(data)

        if (file != null) {
            return fileHandler.writeData(file, data, true, separatorBytes)
        } else {
            sdkLogger.e("Could not get a valid file")
        }

        return false
    }

    protected fun getFile(data: ByteArray): File? {
        return try {
            fileOrchestrator.getWritableFile(data.size)
        } catch (e: SecurityException) {
            sdkLogger.e("Unable to access batch file directory", e)
            null
        }
    }

    // endregion

    // region Internal

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun consume(model: T) {
        serialiseEvent(model)?.let {
            checkDataSizeAndWrite(it, model)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun serialiseEvent(model: T): String? {
        return try {
            serializer.serialize(model)
        } catch (e: Throwable) {
            sdkLogger.w("Unable to serialize ${model.javaClass.simpleName}", e)
            null
        }
    }

    private fun checkDataSizeAndWrite(data: String, model: T) {
        if (data.length >= MAX_ITEM_SIZE) {
            devLogger.e("Unable to persist data, serialized size is too big\n$data")
        } else {
            synchronized(this) {
                writeData(data.toByteArray(Charsets.UTF_8), model)
            }
        }
    }

    // endregion

    companion object {
        private const val MAX_ITEM_SIZE = 256 * 1024 // 256 Kb
    }
}
