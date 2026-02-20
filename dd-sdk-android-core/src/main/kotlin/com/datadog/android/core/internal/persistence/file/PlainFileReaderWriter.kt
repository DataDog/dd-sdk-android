/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.core.internal.utils.use
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Stores data as-is. Use for any non-RUM/Trace/Logs data.
 */
internal class PlainFileReaderWriter(
    private val internalLogger: InternalLogger
) : FileReaderWriter {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)

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
            logger.logPlainFileWriteIoError(
                filePath = file.path,
                throwable = e
            )
            false
        } catch (e: SecurityException) {
            logger.logPlainFileWriteSecurityError(
                filePath = file.path,
                throwable = e
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
                logger.logPlainFileReadNotExists(filePath = file.path)
                EMPTY_BYTE_ARRAY
            } else if (file.isDirectory) {
                logger.logPlainFileReadIsDirectory(filePath = file.path)
                EMPTY_BYTE_ARRAY
            } else {
                @Suppress("UnsafeThirdPartyFunctionCall") // necessary catch blocks exist
                file.readBytes()
            }
        } catch (e: IOException) {
            logger.logPlainFileReadIoError(
                filePath = file.path,
                throwable = e
            )
            EMPTY_BYTE_ARRAY
        } catch (e: SecurityException) {
            logger.logPlainFileReadSecurityError(
                filePath = file.path,
                throwable = e
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
    }
}
