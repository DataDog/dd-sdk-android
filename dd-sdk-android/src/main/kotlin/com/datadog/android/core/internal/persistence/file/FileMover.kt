/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.errorWithTelemetry
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

internal class FileMover(val internalLogger: Logger) {

    /**
     * Deletes the file or directory (recursively if needed).
     * @param target the target [File] to delete
     * @return whether the delete was successful
     */
    @WorkerThread
    fun delete(target: File): Boolean {
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

    /**
     * Move the children files from `srcDir` to the `destDir`.
     */
    @WorkerThread
    fun moveFiles(srcDir: File, destDir: File): Boolean {
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

    private fun moveFile(file: File, destDir: File): Boolean {
        val destFile = File(destDir, file.name)
        return file.renameToSafe(destFile)
    }

    @Suppress("StringLiteralDuplication")
    companion object {
        internal const val ERROR_DELETE = "Unable to delete file: %s"
        internal const val INFO_MOVE_NO_SRC = "Unable to move files; " +
            "source directory does not exist: %s"
        internal const val ERROR_MOVE_NOT_DIR = "Unable to move files; " +
            "file is not a directory: %s"
        internal const val ERROR_MOVE_NO_DST = "Unable to move files; " +
            "could not create directory: %s"
    }
}
