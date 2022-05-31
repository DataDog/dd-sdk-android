/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.single

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.SingleItemFileHandler
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.isDirectorySafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.core.internal.persistence.file.readBytesSafe
import com.datadog.android.core.internal.persistence.file.renameToSafe
import com.datadog.android.core.internal.utils.use
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.datadog.android.security.Encryption
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

// TODO RUMM-2235 Rework file persistence classes/interfaces
// This file may go away after rework, not adding tests for now (it is a simplified copy of
// BatchFileHandler)
@Suppress("unused")
internal class PlainFileHandler(
    private val internalLogger: Logger
) : SingleItemFileHandler {

    // region FileHandler

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
            internalLogger.errorWithTelemetry(ERROR_WRITE.format(Locale.US, file.path), e)
            false
        } catch (e: SecurityException) {
            internalLogger.errorWithTelemetry(ERROR_WRITE.format(Locale.US, file.path), e)
            false
        }
    }

    @WorkerThread
    override fun readData(
        file: File
    ): ByteArray {
        return file.readBytesSafe() ?: ByteArray(0)
    }

    @WorkerThread
    override fun delete(target: File): Boolean {
        return try {
            target.deleteRecursively()
        } catch (e: FileNotFoundException) {
            internalLogger.errorWithTelemetry(ERROR_DELETE.format(Locale.US, target.path), e)
            false
        } catch (e: SecurityException) {
            internalLogger.errorWithTelemetry(ERROR_DELETE.format(Locale.US, target.path), e)
            false
        }
    }

    @WorkerThread
    override fun moveFiles(srcDir: File, destDir: File): Boolean {
        if (!srcDir.existsSafe()) {
            internalLogger.i(INFO_MOVE_NO_SRC.format(Locale.US, srcDir.path))
            return true
        }
        if (!srcDir.isDirectorySafe()) {
            internalLogger.errorWithTelemetry(ERROR_MOVE_NOT_DIR.format(Locale.US, srcDir.path))
            return false
        }
        if (!destDir.existsSafe()) {
            if (!destDir.mkdirsSafe()) {
                internalLogger.errorWithTelemetry(ERROR_MOVE_NO_DST.format(Locale.US, srcDir.path))
                return false
            }
        } else if (!destDir.isDirectorySafe()) {
            internalLogger.errorWithTelemetry(ERROR_MOVE_NOT_DIR.format(Locale.US, destDir.path))
            return false
        }

        val srcFiles = srcDir.listFilesSafe().orEmpty()
        return srcFiles.all { file -> moveFile(file, destDir) }
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

    private fun moveFile(file: File, destDir: File): Boolean {
        val destFile = File(destDir, file.name)
        return file.renameToSafe(destFile)
    }

    // endregion

    @Suppress("StringLiteralDuplication")
    companion object {

        internal const val ERROR_WRITE = "Unable to write data to file: %s"
        internal const val ERROR_DELETE = "Unable to delete file: %s"
        internal const val INFO_MOVE_NO_SRC = "Unable to move files; " +
            "source directory does not exist: %s"
        internal const val ERROR_MOVE_NOT_DIR = "Unable to move files; " +
            "file is not a directory: %s"
        internal const val ERROR_MOVE_NO_DST = "Unable to move files; " +
            "could not create directory: %s"

        /**
         * Creates either plain [PlainFileHandler] or [PlainFileHandler] wrapped in
         * [EncryptedSingleItemFileHandler] if encryption is provided.
         */
        fun create(internalLogger: Logger, encryption: Encryption?): SingleItemFileHandler {
            return if (encryption == null) {
                PlainFileHandler(internalLogger)
            } else {
                EncryptedSingleItemFileHandler(encryption, PlainFileHandler(internalLogger))
            }
        }
    }
}
