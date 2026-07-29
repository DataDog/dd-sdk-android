/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.use
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.BYTE_LOST_UNKNOWN
import com.datadog.android.internal.telemetry.TelemetryContext.Companion.TELEMETRY_FILE_PATH
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
        append: Boolean,
        telemetryContext: TelemetryContext
    ): Boolean {
        return try {
            lockFileAndWriteData(file, append, data)
            true
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_WRITE },
                e,
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = data.size,
                    TELEMETRY_FILE_PATH to file.path
                )
            )
            false
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_WRITE },
                e,
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = data.size,
                    TELEMETRY_FILE_PATH to file.path
                )
            )
            false
        }
    }

    @WorkerThread
    override fun readData(
        file: File,
        telemetryContext: TelemetryContext
    ): ByteArray {
        return try {
            if (!file.exists()) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { ERROR_READ },
                    additionalProperties = telemetryContext.asAttributesMap(
                        bytesLost = BYTE_LOST_UNKNOWN,
                        TELEMETRY_FILE_PATH to file.path
                    )
                )
                EMPTY_BYTE_ARRAY
            } else if (file.isDirectory) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { ERROR_READ },
                    additionalProperties = telemetryContext.asAttributesMap(
                        bytesLost = BYTE_LOST_UNKNOWN,
                        TELEMETRY_FILE_PATH to file.path
                    )
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
                { ERROR_READ },
                e,
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = BYTE_LOST_UNKNOWN,
                    TELEMETRY_FILE_PATH to file.path
                )
            )
            EMPTY_BYTE_ARRAY
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_READ },
                e,
                additionalProperties = telemetryContext.asAttributesMap(
                    bytesLost = BYTE_LOST_UNKNOWN,
                    TELEMETRY_FILE_PATH to file.path
                )
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
        internal const val ERROR_WRITE = "Unable to write data to file."
        internal const val ERROR_READ = "Unable to read data from file."
    }
}
