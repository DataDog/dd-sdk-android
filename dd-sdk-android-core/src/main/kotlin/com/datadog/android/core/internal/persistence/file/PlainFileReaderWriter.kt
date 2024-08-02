/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.use
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * Stores data as-is. Use for any non-RUM/Trace/Logs data.
 */
internal class PlainFileReaderWriter(
    private val internalLogger: InternalLogger
) : FileReaderWriter {

    // region FileWriter+FileReader

    @WorkerThread
    override fun writeData(
        file: File,
        data: ByteArray,
        append: Boolean
    ): Boolean {
        return try {
            lockFileAndWriteData(file, append, data)
            true
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_WRITE.format(Locale.US, file.path) },
                e
            )
            false
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_WRITE.format(Locale.US, file.path) },
                e
            )
            false
        }
    }

    @WorkerThread
    override fun readData(
        file: File
    ): ByteArray {
        return try {
            if (!file.exists()) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { ERROR_READ.format(Locale.US, file.path) }
                )
                EMPTY_BYTE_ARRAY
            } else if (file.isDirectory) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { ERROR_READ.format(Locale.US, file.path) }
                )
                EMPTY_BYTE_ARRAY
            } else {
                @Suppress("UnsafeThirdPartyFunctionCall") // necessary catch blocks exist
                file.readBytes()
            }
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_READ.format(Locale.US, file.path) },
                e
            )
            EMPTY_BYTE_ARRAY
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_READ.format(Locale.US, file.path) },
                e
            )
            EMPTY_BYTE_ARRAY
        }
    }

    // endregion

    // region Internal

    @Throws(IOException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun lockFileAndWriteData(
        file: File,
        append: Boolean,
        data: ByteArray
    ) {
        FileOutputStream(file, append).use { outputStream ->
            outputStream.channel.lock().use {
                outputStream.write(data)
            }
        }
    }

    // endregion

    companion object {

        private val EMPTY_BYTE_ARRAY = ByteArray(0)

        internal const val ERROR_WRITE = "Unable to write data to file: %s"
        internal const val ERROR_READ = "Unable to read data from file: %s"
    }
}
