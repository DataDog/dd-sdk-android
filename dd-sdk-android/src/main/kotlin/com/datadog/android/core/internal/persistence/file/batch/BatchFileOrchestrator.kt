/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.canWriteSafe
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.isFileSafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import com.datadog.android.v2.api.InternalLogger
import java.io.File
import java.io.FileFilter
import java.util.Locale
import kotlin.math.roundToLong

internal class BatchFileOrchestrator(
    private val rootDir: File,
    private val config: FilePersistenceConfig,
    private val internalLogger: InternalLogger
) : FileOrchestrator {

    private val fileFilter = BatchFileFilter(internalLogger)

    // Offset the recent threshold for read and write to avoid conflicts
    // Arbitrary offset as ±5% of the threshold
    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private val recentReadDelayMs = (config.recentDelayMs * INCREASE_PERCENT).roundToLong()

    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private val recentWriteDelayMs = (config.recentDelayMs * DECREASE_PERCENT).roundToLong()

    // keep track of how many items were written in the last known file
    private var previousFile: File? = null
    private var previousFileItemCount: Int = 0

    // region FileOrchestrator

    @WorkerThread
    override fun getWritableFile(forceNewFile: Boolean): File? {
        if (!isRootDirValid()) {
            return null
        }

        deleteObsoleteFiles()
        freeSpaceIfNeeded()

        return if (!forceNewFile) {
            getReusableWritableFile() ?: createNewFile()
        } else {
            createNewFile()
        }
    }

    @WorkerThread
    override fun getReadableFile(excludeFiles: Set<File>): File? {
        if (!isRootDirValid()) {
            return null
        }

        deleteObsoleteFiles()

        val files = listSortedBatchFiles()

        return files.firstOrNull {
            (it !in excludeFiles) && !isFileRecent(it, recentReadDelayMs)
        }
    }

    @WorkerThread
    override fun getAllFiles(): List<File> {
        if (!isRootDirValid()) {
            return emptyList()
        }

        return listSortedBatchFiles()
    }

    @WorkerThread
    override fun getFlushableFiles(): List<File> {
        return getAllFiles()
    }

    @WorkerThread
    override fun getRootDir(): File? {
        if (!isRootDirValid()) {
            return null
        }

        return rootDir
    }

    @WorkerThread
    override fun getMetadataFile(file: File): File? {
        if (file.parent != rootDir.path) {
            // may happen if batch file was requested with pending orchestrator, but meta file
            // is requested with granted orchestrator (due to consent change). Not an issue, because
            // batch file should be migrated to the same folder, but leaving this debug point
            // just in case.
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                DEBUG_DIFFERENT_ROOT.format(Locale.US, file.path, rootDir.path)
            )
        }

        return if (file.name.matches(batchFileNameRegex)) {
            file.metadata
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                ERROR_NOT_BATCH_FILE.format(Locale.US, file.path)
            )
            null
        }
    }

    // endregion

    // region Internal

    @Suppress("LiftReturnOrAssignment", "ReturnCount")
    private fun isRootDirValid(): Boolean {
        if (rootDir.existsSafe(internalLogger)) {
            if (rootDir.isDirectory) {
                if (rootDir.canWriteSafe(internalLogger)) {
                    return true
                } else {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        targets = listOf(
                            InternalLogger.Target.MAINTAINER,
                            InternalLogger.Target.TELEMETRY
                        ),
                        ERROR_ROOT_NOT_WRITABLE.format(Locale.US, rootDir.path)
                    )
                    return false
                }
            } else {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    targets = listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    ERROR_ROOT_NOT_DIR.format(Locale.US, rootDir.path)
                )
                return false
            }
        } else {
            synchronized(rootDir) {
                // double check if directory was already created by some other thread
                // entered this branch
                if (rootDir.existsSafe(internalLogger)) {
                    return true
                }

                if (rootDir.mkdirsSafe(internalLogger)) {
                    return true
                } else {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        targets = listOf(
                            InternalLogger.Target.MAINTAINER,
                            InternalLogger.Target.TELEMETRY
                        ),
                        ERROR_CANT_CREATE_ROOT.format(Locale.US, rootDir.path)
                    )
                    return false
                }
            }
        }
    }

    private fun createNewFile(): File {
        val newFileName = System.currentTimeMillis().toString()
        val newFile = File(rootDir, newFileName)
        previousFile = newFile
        previousFileItemCount = 1
        return newFile
    }

    @Suppress("ReturnCount")
    private fun getReusableWritableFile(): File? {
        val files = listSortedBatchFiles()
        val lastFile = files.lastOrNull() ?: return null

        val lastKnownFile = previousFile
        val lastKnownFileItemCount = previousFileItemCount
        if (lastKnownFile != lastFile) {
            // this situation can happen because:
            // 1. `lastFile` is a file written during a previous session
            // 2. `lastFile` was created by another system/process
            // 3. `lastKnownFile` was deleted
            // In any case, we don't know the item count, so to be safe, we create a new file
            return null
        }

        val isRecentEnough = isFileRecent(lastFile, recentWriteDelayMs)
        val hasRoomForMore = lastFile.lengthSafe(internalLogger) < config.maxBatchSize
        val hasSlotForMore = (lastKnownFileItemCount < config.maxItemsPerBatch)

        return if (isRecentEnough && hasRoomForMore && hasSlotForMore) {
            previousFileItemCount = lastKnownFileItemCount + 1
            lastFile
        } else {
            null
        }
    }

    private fun isFileRecent(file: File, delayMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val fileTimestamp = file.name.toLongOrNull() ?: 0L
        return fileTimestamp >= (now - delayMs)
    }

    private fun deleteObsoleteFiles() {
        val files = listSortedBatchFiles()
        val threshold = System.currentTimeMillis() - config.oldFileThreshold
        files
            .asSequence()
            .filter { (it.name.toLongOrNull() ?: 0) < threshold }
            .forEach {
                it.deleteSafe(internalLogger)
                if (it.metadata.existsSafe(internalLogger)) {
                    it.metadata.deleteSafe(internalLogger)
                }
            }
    }

    private fun freeSpaceIfNeeded() {
        val files = listSortedBatchFiles()
        val sizeOnDisk = files.sumOf { it.lengthSafe(internalLogger) }
        val maxDiskSpace = config.maxDiskSpace
        val sizeToFree = sizeOnDisk - maxDiskSpace
        if (sizeToFree > 0) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                ERROR_DISK_FULL.format(Locale.US, sizeOnDisk, maxDiskSpace, sizeToFree)
            )
            files.fold(sizeToFree) { remainingSizeToFree, file ->
                if (remainingSizeToFree > 0) {
                    val deletedFileSize = deleteFile(file)
                    val deletedMetaFileSize = deleteFile(file.metadata)
                    remainingSizeToFree - deletedFileSize - deletedMetaFileSize
                } else {
                    remainingSizeToFree
                }
            }
        }
    }

    private fun deleteFile(file: File): Long {
        if (!file.existsSafe(internalLogger)) return 0

        val size = file.lengthSafe(internalLogger)
        return if (file.deleteSafe(internalLogger)) {
            size
        } else {
            0
        }
    }

    private fun listSortedBatchFiles(): List<File> {
        return rootDir.listFilesSafe(fileFilter, internalLogger).orEmpty().sorted()
    }

    private val File.metadata: File
        get() = File("${this.path}_metadata")

    // endregion

    // region FileFilter

    internal class BatchFileFilter(private val internalLogger: InternalLogger) : FileFilter {
        override fun accept(file: File?): Boolean {
            return file != null &&
                file.isFileSafe(internalLogger) &&
                file.name.matches(batchFileNameRegex)
        }
    }

    // endregion

    companion object {

        const val DECREASE_PERCENT = 0.95
        const val INCREASE_PERCENT = 1.05

        private val batchFileNameRegex = Regex("\\d+")
        internal const val ERROR_ROOT_NOT_WRITABLE = "The provided root dir is not writable: %s"
        internal const val ERROR_ROOT_NOT_DIR = "The provided root file is not a directory: %s"
        internal const val ERROR_CANT_CREATE_ROOT = "The provided root file can't be created: %s"
        internal const val ERROR_DISK_FULL = "Too much disk space used (%d/%d): " +
            "cleaning up to free %d bytes…"
        internal const val ERROR_NOT_BATCH_FILE = "The file provided is not a batch file: %s"
        internal const val DEBUG_DIFFERENT_ROOT = "The file provided (%s) doesn't belong" +
            " to the current folder (%s)"
    }
}
