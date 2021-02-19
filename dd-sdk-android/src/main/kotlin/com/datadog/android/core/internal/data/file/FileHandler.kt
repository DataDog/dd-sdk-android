/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.core.internal.utils.use
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.NullPointerException

internal class FileHandler {

    // region FileHandler

    @SuppressWarnings("TooGenericExceptionCaught")
    fun deleteFileOrDirectory(source: File): Boolean {
        return try {
            source.deleteRecursively()
        } catch (e: Throwable) {
            sdkLogger.e(
                "Unable to clear the file at [${source.absolutePath}]",
                e
            )
            false
        }
    }

    fun moveFiles(sourceDirectory: File, destinationDirectory: File): Boolean {
        if (!sourceDirectory.exists()) {
            sdkLogger.w(
                "There were no files to move. " +
                    "There is no directory at this path: [$sourceDirectory]"
            )
            return true
        }

        if (!sourceDirectory.isDirectory) {
            sdkLogger.w(
                "There were no files to move." +
                    "[$sourceDirectory] is not a directory."
            )
            return true
        }
        destinationDirectory.mkdirs()

        val files = sourceDirectory.listFiles()
        if (files == null || files.isEmpty()) {
            return true
        }
        return files
            .map {
                moveFile(destinationDirectory, it)
            }.reduce { overallSuccess, success ->
                overallSuccess && success
            }
    }

    fun writeData(
        file: File,
        dataAsByteArray: ByteArray,
        append: Boolean = false,
        separator: ByteArray? = null
    ): Boolean {

        var isSuccess = false

        try {
            val outputStream = FileOutputStream(file, append)
            outputStream.use { stream ->
                lockFileAndWriteData(stream, file, dataAsByteArray, separator)
            }
            isSuccess = true
        } catch (e: IllegalStateException) {
            sdkLogger.e("Exception when trying to lock the file: [${file.canonicalPath}] ", e)
        } catch (e: FileNotFoundException) {
            sdkLogger.e("Couldn't create an output stream to file ${file.path}", e)
        } catch (e: IOException) {
            sdkLogger.e("Exception when trying to write data to: [${file.canonicalPath}] ", e)
        }

        return isSuccess
    }

    // endregion

    // region Internal

    private fun lockFileAndWriteData(
        stream: FileOutputStream,
        file: File,
        dataAsByteArray: ByteArray,
        separator: ByteArray?
    ) {
        stream.channel.lock().use {
            if (file.length() > 0 && separator != null) {
                stream.write(separator + dataAsByteArray)
            } else {
                stream.write(dataAsByteArray)
            }
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught")
    private fun moveFile(destinationDirectory: File, file: File): Boolean {
        val newFile = File(destinationDirectory, file.name)
        return try {
            file.renameTo(newFile)
        } catch (e: SecurityException) {
            sdkLogger.e(
                "Unable to move file: [${file.absolutePath}] " +
                    "to new file: [${newFile.absolutePath}]",
                e
            )
            false
        } catch (e: NullPointerException) {
            sdkLogger.e(
                "Unable to move file: [${file.absolutePath}] " +
                    "to new file: [${newFile.absolutePath}]",
                e
            )
            false
        }
    }

    // endregion
}
