/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import java.io.File
import java.io.FileNotFoundException

internal class FileMover(val internalLogger: InternalLogger) {

    private val logger = DdSdkAndroidCoreLogger(internalLogger)

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
            logger.logErrorDelete(targetPath = target.path, throwable = e)
            false
        } catch (e: SecurityException) {
            logger.logErrorDelete(targetPath = target.path, throwable = e)
            false
        }
    }

    /**
     * Move the children files from `srcDir` to the `destDir`.
     */
    @Suppress("ReturnCount")
    @WorkerThread
    fun moveFiles(srcDir: File, destDir: File): Boolean {
        if (!srcDir.existsSafe(internalLogger)) {
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { INFO_MOVE_NO_SRC.format(Locale.US, srcDir.path) }
            )
            return true
        }
        if (!srcDir.isDirectorySafe(internalLogger)) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_MOVE_NOT_DIR.format(Locale.US, srcDir.path) }
            )
            return false
        }
        if (!destDir.existsSafe(internalLogger)) {
            if (!destDir.mkdirsSafe(internalLogger)) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    { ERROR_MOVE_NO_DST.format(Locale.US, srcDir.path) }
                )
                return false
            }
        } else if (!destDir.isDirectorySafe(internalLogger)) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_MOVE_NOT_DIR.format(Locale.US, destDir.path) }
            )
            return false
        }

        val srcFiles = srcDir.listFilesSafe(internalLogger).orEmpty()
        return srcFiles.all { file -> moveFile(file, destDir) }
    }

    private fun moveFile(file: File, destDir: File): Boolean {
        val destFile = File(destDir, file.name)
        return file.renameToSafe(destFile, internalLogger)
    }

}
