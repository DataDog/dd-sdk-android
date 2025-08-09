/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import android.os.FileObserver
import android.os.FileObserver.CREATE
import android.os.FileObserver.DELETE
import android.os.FileObserver.MOVED_TO
import android.os.FileObserver.MOVED_FROM
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.metrics.BatchClosedMetadata
import com.datadog.android.core.internal.metrics.MetricsDispatcher
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.canWriteSafe
import com.datadog.android.core.internal.persistence.file.deleteSafe
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.persistence.file.listFilesSafe
import com.datadog.android.core.internal.persistence.file.mkdirsSafe
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToLong

// TODO RUM-438 Improve this class: need to make it thread-safe and optimize work with file
//  system in order to reduce the number of syscalls (which are expensive) for files already seen
@Suppress("TooManyFunctions")
internal class BatchFileOrchestrator(
    private val rootDir: File,
    internal val config: FilePersistenceConfig,
    private val internalLogger: InternalLogger,
    private val metricsDispatcher: MetricsDispatcher,
    private val pendingFiles: AtomicInteger = AtomicInteger(0)
) : FileOrchestrator {

    // Offset the recent threshold for read and write to avoid conflicts
    // Arbitrary offset as ±5% of the threshold
    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private val recentReadDelayMs = (config.recentDelayMs * INCREASE_PERCENT).roundToLong()

    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private val recentWriteDelayMs = (config.recentDelayMs * DECREASE_PERCENT).roundToLong()

    // keep track of how many items were written in the last known file
    private var previousFile: File? = null
    private var previousFileItemCount: Long = 0
    private var lastFileAccessTimestamp: Long = 0L
    private var lastCleanupTimestamp: Long = 0L

    private val knownFiles: MutableSet<File> =
        rootDir.listFilesSafe(internalLogger)?.toMutableSet() ?: mutableSetOf()

    @Suppress("DEPRECATION") // Recommended constructor only available in API 29 Q
    internal val fileObserver = object : FileObserver(rootDir.path, FILE_OBSERVER_MASK) {
        override fun onEvent(event: Int, name: String?) {
            if (!name.isNullOrEmpty() && name.isBatchFileName) {
                val file = File(rootDir, name)
                when (event) {
                    MOVED_TO, CREATE -> {
                        synchronized(knownFiles) {
                            knownFiles.add(file)
                        }

                    }

                    MOVED_FROM, DELETE -> {
                        synchronized(knownFiles) {
                            knownFiles.remove(file)
                        }
                    }
                }
            }
        }

    }

    init {
        fileObserver.startWatching()
    }

    // region FileOrchestrator

    @WorkerThread
    override fun getWritableFile(forceNewFile: Boolean): File? {
        if (!isRootDirValid()) {
            return null
        }

        if (canDoCleanup()) {
            var files = listBatchFiles()
            files = deleteObsoleteFiles(files)
            freeSpaceIfNeeded(files)
            lastCleanupTimestamp = System.currentTimeMillis()
        }

        return if (!forceNewFile) {
            getReusableWritableFile() ?: createNewFile()
        } else {
            createNewFile(true)
        }
    }

    @WorkerThread
    override fun getReadableFile(excludeFiles: Set<File>): File? {
        if (!isRootDirValid()) {
            return null
        }

        val files = listSortedBatchFiles().let {
            deleteObsoleteFiles(it)
        }
        lastCleanupTimestamp = System.currentTimeMillis()
        pendingFiles.set(files.count())

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

    override fun getRootDirName(): String {
        return rootDir.nameWithoutExtension
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
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { DEBUG_DIFFERENT_ROOT.format(Locale.US, file.path, rootDir.path) }
            )
        }

        return if (file.isBatchFile) {
            file.metadata
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_NOT_BATCH_FILE.format(Locale.US, file.path) }
            )
            null
        }
    }

    // endregion

    // region Internal

    override fun decrementAndGetPendingFilesCount(): Int {
        return pendingFiles.decrementAndGet()
    }

    @Suppress("LiftReturnOrAssignment", "ReturnCount")
    private fun isRootDirValid(): Boolean {
        if (rootDir.existsSafe(internalLogger)) {
            if (rootDir.isDirectory) {
                if (rootDir.canWriteSafe(internalLogger)) {
                    return true
                } else {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        listOf(
                            InternalLogger.Target.MAINTAINER,
                            InternalLogger.Target.TELEMETRY
                        ),
                        { ERROR_ROOT_NOT_WRITABLE.format(Locale.US, rootDir.path) }
                    )
                    return false
                }
            } else {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    { ERROR_ROOT_NOT_DIR.format(Locale.US, rootDir.path) }
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
                        listOf(
                            InternalLogger.Target.MAINTAINER,
                            InternalLogger.Target.TELEMETRY
                        ),
                        { ERROR_CANT_CREATE_ROOT.format(Locale.US, rootDir.path) }
                    )
                    return false
                }
            }
        }
    }

    private fun createNewFile(wasForced: Boolean = false): File {
        val newFileName = System.currentTimeMillis().toString()
        val newFile = File(rootDir, newFileName)
        val closedFile = previousFile
        val closedFileLastAccessTimestamp = lastFileAccessTimestamp
        if (closedFile != null) {
            metricsDispatcher.sendBatchClosedMetric(
                closedFile,
                BatchClosedMetadata(
                    lastTimeWasUsedInMs = closedFileLastAccessTimestamp,
                    eventsCount = previousFileItemCount,
                    forcedNew = wasForced
                )
            )
        }
        previousFile = newFile
        previousFileItemCount = 1
        lastFileAccessTimestamp = System.currentTimeMillis()
        pendingFiles.incrementAndGet()
        return newFile
    }

    @Suppress("ReturnCount")
    private fun getReusableWritableFile(): File? {
        val files = listBatchFiles()
        val lastFile = files.latestBatchFile ?: return null

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
            lastFileAccessTimestamp = System.currentTimeMillis()
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

    private fun deleteObsoleteFiles(files: List<File>): List<File> {
        val threshold = System.currentTimeMillis() - config.oldFileThreshold
        return files
            .mapNotNull {
                val isOldFile = (it.name.toLongOrNull() ?: 0) < threshold
                if (isOldFile) {
                    if (it.deleteSafe(internalLogger)) {
                        metricsDispatcher.sendBatchDeletedMetric(
                            batchFile = it,
                            removalReason = RemovalReason.Obsolete,
                            numPendingBatches = pendingFiles.decrementAndGet()
                        )
                    }
                    if (it.metadata.existsSafe(internalLogger)) {
                        it.metadata.deleteSafe(internalLogger)
                    }
                    null
                } else {
                    it
                }
            }
    }

    private fun freeSpaceIfNeeded(files: List<File>) {
        val sizeOnDisk = files.sumOf { it.lengthSafe(internalLogger) }
        val maxDiskSpace = config.maxDiskSpace
        val sizeToFree = sizeOnDisk - maxDiskSpace
        if (sizeToFree > 0) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_DISK_FULL.format(Locale.US, sizeOnDisk, maxDiskSpace, sizeToFree) }
            )
            files.sorted().fold(sizeToFree) { remainingSizeToFree, file ->
                if (remainingSizeToFree > 0) {
                    val deletedFileSize = deleteFile(file, true)
                    val deletedMetaFileSize = deleteFile(file.metadata)
                    remainingSizeToFree - deletedFileSize - deletedMetaFileSize
                } else {
                    remainingSizeToFree
                }
            }
        }
    }

    private fun deleteFile(file: File, sendMetric: Boolean = false): Long {
        if (!file.existsSafe(internalLogger)) return 0

        val size = file.lengthSafe(internalLogger)
        val wasDeleted = file.deleteSafe(internalLogger)
        return if (wasDeleted) {
            if (sendMetric) {
                metricsDispatcher.sendBatchDeletedMetric(file, RemovalReason.Purged, pendingFiles.decrementAndGet())
            }
            size
        } else {
            0
        }
    }

    private fun listBatchFiles(): List<File> {
        return synchronized(knownFiles) {
            knownFiles.toList()
        }
    }

    private fun listSortedBatchFiles(): List<File> {
        // note: since it is using File#compareTo, lexicographical sorting will be used, meaning "10" comes before "9".
        // but for our needs it is fine, because the moment when Unix timestamp adds one more digit will be in 2286.
        return listBatchFiles().sorted()
    }

    private fun canDoCleanup(): Boolean {
        return System.currentTimeMillis() - lastCleanupTimestamp > config.cleanupFrequencyThreshold
    }

    private val File.metadata: File
        get() = File("${this.path}_metadata")

    private val File.isBatchFile: Boolean
        get() = name.isBatchFileName

    private val String.isBatchFileName: Boolean
        get() = toLongOrNull() != null

    private val List<File>.latestBatchFile: File?
        get() = maxOrNull()

    // endregion

    companion object {

        private const val FILE_OBSERVER_MASK = CREATE or DELETE or MOVED_TO or MOVED_FROM

        const val DECREASE_PERCENT = 0.95
        const val INCREASE_PERCENT = 1.05

        internal const val ERROR_ROOT_NOT_WRITABLE = "The provided root dir is not writable: %s"
        internal const val ERROR_ROOT_NOT_DIR = "The provided root file is not a directory: %s"
        internal const val ERROR_CANT_CREATE_ROOT = "The provided root dir can't be created: %s"
        internal const val ERROR_DISK_FULL = "Too much disk space used (%d/%d): " +
            "cleaning up to free %d bytes…"
        internal const val ERROR_NOT_BATCH_FILE = "The file provided is not a batch file: %s"
        internal const val DEBUG_DIFFERENT_ROOT = "The file provided (%s) doesn't belong" +
            " to the current folder (%s)"
    }
}
