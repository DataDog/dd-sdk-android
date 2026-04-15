/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import android.os.FileObserver
import android.os.FileObserver.CREATE
import android.os.FileObserver.DELETE
import android.os.FileObserver.MOVED_FROM
import android.os.FileObserver.MOVED_TO
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
import com.datadog.android.internal.time.TimeProvider
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong

@Suppress("TooManyFunctions")
internal class BatchFileOrchestrator(
    private val rootDir: File,
    internal val config: FilePersistenceConfig,
    private val internalLogger: InternalLogger,
    private val metricsDispatcher: MetricsDispatcher,
    private val timeProvider: TimeProvider,
    private val pendingFiles: AtomicInteger = AtomicInteger(0)
) : FileOrchestrator {

    // Offset the recent threshold for read and write to avoid conflicts
    // Arbitrary offset as ±5% of the threshold
    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private val recentReadDelayMs = (config.recentDelayMs * INCREASE_PERCENT).roundToLong()

    @Suppress("UnsafeThirdPartyFunctionCall") // rounded Double isn't NaN
    private val recentWriteDelayMs = (config.recentDelayMs * DECREASE_PERCENT).roundToLong()

    private val currentBatchState = AtomicReference(CurrentBatchState(null, 0L, 0L))
    private val lastCleanupTimestamp = AtomicLong(0L)
    private val isFileObserverStarted = AtomicBoolean(false)

    private val knownFiles: MutableSet<File> = mutableSetOf()

    @Suppress("DEPRECATION") // Recommended constructor only available in API 29 Q
    internal val fileObserver = object : FileObserver(rootDir.path, FILE_OBSERVER_MASK) {
        override fun onEvent(event: Int, name: String?) {
            if (!name.isNullOrEmpty() && name.isBatchFileName) {
                val file = File(rootDir, name)
                val isAdded = event and (MOVED_TO or CREATE) != 0
                val isRemoved = event and (MOVED_FROM or DELETE) != 0
                if (isAdded) {
                    synchronized(knownFiles) {
                        knownFiles.add(file)
                    }
                }
                if (isRemoved) {
                    synchronized(knownFiles) {
                        knownFiles.remove(file)
                    }
                }
            }
        }
    }

    // region FileOrchestrator

    @WorkerThread
    override fun getWritableFile(): File? {
        if (!isRootDirValid()) {
            return null
        }

        if (canDoCleanup()) {
            var files = listBatchFiles()
            files = deleteObsoleteFiles(files)
            freeSpaceIfNeeded(files)
            lastCleanupTimestamp.set(timeProvider.getDeviceTimestampMillis())
        }

        return getReusableWritableFile() ?: createNewFile()
    }

    @WorkerThread
    override fun getReadableFile(excludeFiles: Set<File>): File? {
        if (!isRootDirValid()) {
            return null
        }

        val files = deleteObsoleteFiles(listSortedBatchFiles())
        lastCleanupTimestamp.set(timeProvider.getDeviceTimestampMillis())
        pendingFiles.set(files.count())

        return files.firstOrNull {
            (it !in excludeFiles) && !isFileRecent(it, recentReadDelayMs) && it.existsSafe(internalLogger)
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

    @Suppress("ReturnCount")
    private fun isRootDirValid(): Boolean {
        val isValid = if (rootDir.existsSafe(internalLogger)) {
            when {
                !rootDir.isDirectory -> {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                        { ERROR_ROOT_NOT_DIR.format(Locale.US, rootDir.path) }
                    )
                    false
                }

                !rootDir.canWriteSafe(internalLogger) -> {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                        { ERROR_ROOT_NOT_WRITABLE.format(Locale.US, rootDir.path) }
                    )
                    false
                }

                else -> true
            }
        } else {
            createRootDirectory()
        }

        if (isValid) startFileObserverIfNotStarted()
        return isValid
    }

    private fun createRootDirectory(): Boolean = synchronized(rootDir) {
        val created = rootDir.existsSafe(internalLogger) || rootDir.mkdirsSafe(internalLogger)
        if (!created) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_CANT_CREATE_ROOT.format(Locale.US, rootDir.path) }
            )
        }
        created
    }

    private fun startFileObserverIfNotStarted() {
        if (isFileObserverStarted.compareAndSet(false, true)) {
            fileObserver.startWatching()
            synchronized(knownFiles) {
                rootDir.listFilesSafe(internalLogger)
                    ?.filter { it.name.isBatchFileName }
                    ?.let { knownFiles.addAll(it) }
            }
        }
    }

    private fun createNewFile(): File = synchronized(currentBatchState) {
        val now = timeProvider.getDeviceTimestampMillis()
        val newFile = File(rootDir, now.toString())
        val previousState = currentBatchState.get()
        if (previousState.file != null) {
            metricsDispatcher.sendBatchClosedMetric(
                previousState.file,
                BatchClosedMetadata(
                    lastTimeWasUsedInMs = previousState.lastAccessTimestamp,
                    eventsCount = previousState.itemCount
                )
            )
        }
        currentBatchState.set(CurrentBatchState(newFile, 1L, now))
        pendingFiles.incrementAndGet()
        synchronized(knownFiles) {
            knownFiles.add(newFile)
        }
        newFile
    }

    @Suppress("ReturnCount")
    private fun getReusableWritableFile(): File? {
        val files = listBatchFiles()
        val lastFile = files.latestBatchFile ?: return null

        if (!lastFile.existsSafe(internalLogger)) {
            return null
        }

        synchronized(currentBatchState) {
            val state = currentBatchState.get()
            if (state.file != lastFile) {
                // this situation can happen because:
                // 1. `lastFile` is a file written during a previous session
                // 2. `lastFile` was created by another system/process
                // 3. `state.file` was deleted
                // In any case, we don't know the item count, so to be safe, we create a new file
                return null
            }

            val isRecentEnough = isFileRecent(lastFile, recentWriteDelayMs)
            val hasRoomForMore = lastFile.lengthSafe(internalLogger) < config.maxBatchSize
            val hasSlotForMore = (state.itemCount < config.maxItemsPerBatch)

            if (!isRecentEnough || !hasRoomForMore || !hasSlotForMore) {
                return null
            }

            currentBatchState.set(
                state.copy(
                    itemCount = state.itemCount + 1,
                    lastAccessTimestamp = timeProvider.getDeviceTimestampMillis()
                )
            )
            return lastFile
        }
    }

    private fun isFileRecent(file: File, delayMs: Long): Boolean {
        val now = timeProvider.getDeviceTimestampMillis()
        val fileTimestamp = file.name.toLongOrNull() ?: 0L
        return fileTimestamp >= (now - delayMs)
    }

    private fun deleteObsoleteFiles(files: List<File>): List<File> {
        val threshold = timeProvider.getDeviceTimestampMillis() - config.oldFileThreshold
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
        return timeProvider.getDeviceTimestampMillis() - lastCleanupTimestamp.get() > config.cleanupFrequencyThreshold
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

    private data class CurrentBatchState(
        val file: File?,
        val itemCount: Long,
        val lastAccessTimestamp: Long
    )

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
