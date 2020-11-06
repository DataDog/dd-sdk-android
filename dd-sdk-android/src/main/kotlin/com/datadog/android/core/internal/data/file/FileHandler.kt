/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.utils.sdkLogger
import java.io.File
import java.lang.NullPointerException

internal class FileHandler {

    // region FileHandler

    @SuppressWarnings("TooGenericExceptionCaught")
    fun clearFile(source: File): Boolean {
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
            sdkLogger.e(
                "Unable to move the files. " +
                    "There is no directory at this path: [$sourceDirectory]"
            )
            return false
        }

        if (!sourceDirectory.isDirectory) {
            sdkLogger.e(
                "Unable to move the files." +
                    "[$sourceDirectory] is not a directory."
            )
            return false
        }

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

    // endregion

    // region Internal

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
