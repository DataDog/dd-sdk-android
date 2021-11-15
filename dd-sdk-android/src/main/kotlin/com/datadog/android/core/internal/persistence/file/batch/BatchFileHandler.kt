/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.isDirectorySafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.core.internal.persistence.file.renameToSafe
import com.datadog.android.core.internal.utils.copyTo
import com.datadog.android.core.internal.utils.use
import com.datadog.android.log.Logger
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

internal class BatchFileHandler(
    private val internalLogger: Logger
) : FileHandler {

    // region FileHandler

    override fun writeData(
        file: File,
        data: ByteArray,
        append: Boolean,
        separator: ByteArray?
    ): Boolean {

        return try {
            lockFileAndWriteData(file, append, separator, data)
            true
        } catch (e: IOException) {
            internalLogger.e(ERROR_WRITE.format(Locale.US, file.path), e)
            false
        } catch (e: SecurityException) {
            internalLogger.e(ERROR_WRITE.format(Locale.US, file.path), e)
            false
        }
    }

    override fun readData(
        file: File,
        prefix: ByteArray?,
        suffix: ByteArray?
    ): ByteArray {
        return try {
            readFileData(file, prefix ?: EMPTY_BYTE_ARRAY, suffix ?: EMPTY_BYTE_ARRAY)
        } catch (e: IOException) {
            internalLogger.e(ERROR_READ.format(Locale.US, file.path), e)
            EMPTY_BYTE_ARRAY
        } catch (e: SecurityException) {
            internalLogger.e(ERROR_READ.format(Locale.US, file.path), e)
            EMPTY_BYTE_ARRAY
        }
    }

    override fun delete(target: File): Boolean {
        return try {
            target.deleteRecursively()
        } catch (e: FileNotFoundException) {
            internalLogger.e(ERROR_DELETE.format(Locale.US, target.path), e)
            false
        } catch (e: SecurityException) {
            internalLogger.e(ERROR_DELETE.format(Locale.US, target.path), e)
            false
        }
    }

    override fun moveFiles(srcDir: File, destDir: File): Boolean {
        if (!srcDir.existsSafe()) {
            internalLogger.i(INFO_MOVE_NO_SRC.format(Locale.US, srcDir.path))
            return true
        }
        if (!srcDir.isDirectorySafe()) {
            internalLogger.e(ERROR_MOVE_NOT_DIR.format(Locale.US, srcDir.path))
            return false
        }
        if (!destDir.existsSafe()) {
            if (!destDir.mkdirsSafe()) {
                internalLogger.e(ERROR_MOVE_NO_DST.format(Locale.US, srcDir.path))
                return false
            }
        } else if (!destDir.isDirectorySafe()) {
            internalLogger.e(ERROR_MOVE_NOT_DIR.format(Locale.US, destDir.path))
            return false
        }

        val srcFiles = srcDir.listFilesSafe().orEmpty()
        return srcFiles.all { file -> moveFile(file, destDir) }
    }

    // endregion

    // region Internal

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    @Throws(IOException::class)
    private fun lockFileAndWriteData(
        file: File,
        append: Boolean,
        separator: ByteArray?,
        data: ByteArray
    ) {
        FileOutputStream(file, append).use { outputStream ->
            outputStream.channel.lock().use {
                if (file.length() > 0 && separator != null) {
                    outputStream.write(separator)
                }
                outputStream.write(data)
            }
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    @Throws(IOException::class)
    private fun readFileData(
        file: File,
        prefix: ByteArray,
        suffix: ByteArray
    ): ByteArray {
        val inputLength = file.lengthSafe().toInt()
        val outputLength = inputLength + prefix.size + suffix.size
        val result = ByteArray(outputLength)

        // Copy prefix
        prefix.copyTo(0, result, 0, prefix.size)

        // Read file iteratively
        var offset = prefix.size
        var remaining = inputLength
        file.inputStream().use {
            while (remaining > 0) {
                val read = it.read(result, offset, remaining)
                if (read < 0) break
                offset += read
                remaining -= read
            }
        }

        // Copy suffix
        suffix.copyTo(0, result, offset, suffix.size)
        offset += suffix.size

        return if (result.size == offset) {
            result
        } else {
            result.copyOf(offset)
        }
    }

    private fun moveFile(file: File, destDir: File): Boolean {
        val destFile = File(destDir, file.name)
        return file.renameToSafe(destFile)
    }

    // endregion

    @Suppress("StringLiteralDuplication")
    companion object {

        private val EMPTY_BYTE_ARRAY = ByteArray(0)

        internal const val ERROR_WRITE = "Unable to write data to file: %s"
        internal const val ERROR_READ = "Unable to read data from file: %s"
        internal const val ERROR_DELETE = "Unable to delete file: %s"
        internal const val INFO_MOVE_NO_SRC = "Unable to move files; " +
            "source directory does not exist: %s"
        internal const val ERROR_MOVE_NOT_DIR = "Unable to move files; " +
            "file is not a directory: %s"
        internal const val ERROR_MOVE_NO_DST = "Unable to move files; " +
            "could not create directory: %s"
    }
}
