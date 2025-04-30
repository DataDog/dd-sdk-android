/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import android.content.Context
import android.util.Log
import com.google.perftools.profiles.ProfileProto.Profile
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class MergeTraceDumper private constructor(
    private val tracesDirectoryPath: String
) {
    private var executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val snapshotCounter = AtomicLong(0)
    private val tempSnapshots = ConcurrentHashMap<Long, Profile>()
    private val isStarted: AtomicBoolean = AtomicBoolean(false)

    private val snapshotIntervalInMilliseconds = AtomicLong()

    fun startDumpingTrace(intervalInMillis: Long) {
        if (isStarted.getAndSet(true)) {
            return
        }
        // Clean up previous snapshots
        tempSnapshots.clear()
        snapshotCounter.set(0)

        snapshotIntervalInMilliseconds.set(intervalInMillis)
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadScheduledExecutor()
        }
        // Schedule snapshot collection every 10ms
        executor.scheduleWithFixedDelay(
            { collectSnapshot(intervalInMillis) },
            0,
            intervalInMillis,
            TimeUnit.MILLISECONDS
        )

        // Schedule merging every minute
        executor.scheduleWithFixedDelay(
            { mergeAndSaveSnapshots(intervalInMillis) },
            1,
            1,
            TimeUnit.MINUTES
        )
    }

    fun stopDumpingTrace() {
        // take the current value of snapshotIntervalInMillis before stopping
        val intervalInMillis = snapshotIntervalInMilliseconds.get()
        if (!isStarted.getAndSet(false)) {
            return
        }

        // Schedule the final merge in the worker thread
        executor.execute {
            try {
                mergeAndSaveSnapshots(intervalInMillis)
            } finally {
                // Shutdown the executor after the merge is complete
                executor.shutdown()
                try {
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    executor.shutdownNow()
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    private fun collectSnapshot(intervalInMillis: Long) {
        if (!isStarted.get()) {
            return
        }
        try {
            val intervalInNanos = TimeUnit.MILLISECONDS.toNanos(intervalInMillis)
            val pprof = ProfileUtils.createProfile(intervalInNanos)
            val snapshotId = snapshotCounter.incrementAndGet()
            tempSnapshots[snapshotId] = pprof

            Log.d(
                "MergeTraceDumper",
                "Collected snapshot $snapshotId"
            )
        } catch (e: Exception) {
            Log.e(
                "MergeTraceDumper",
                "Failed to collect snapshot",
                e
            )
        }
    }

    private fun mergeAndSaveSnapshots(intervalInMillis: Long) {
        try {
            val intervalInNanos = TimeUnit.MILLISECONDS.toNanos(intervalInMillis)
            val profiles = tempSnapshots.values
            val mergedProfile = ProfileUtils.merge(tempSnapshots.values.toList(), intervalInNanos)
            var firstProfileStartTime: Long = Long.MAX_VALUE
            var lastProfileStartTime: Long = Long.MIN_VALUE

            profiles.forEach { profile ->
                firstProfileStartTime = minOf(firstProfileStartTime, profile.timeNanos)
                lastProfileStartTime = maxOf(lastProfileStartTime, profile.timeNanos)
            }
            val startTimeMillis = TimeUnit.NANOSECONDS.toMillis(firstProfileStartTime)
            val endTimeInMillis = TimeUnit.NANOSECONDS.toMillis(lastProfileStartTime)
            val mergedFile = File(tracesDirectoryPath, "merged_${startTimeMillis}_$endTimeInMillis.pprof")
            FileOutputStream(mergedFile).use { out ->
                mergedProfile.writeTo(out)
            }
            Log.i(
                "MergeTraceDumper",
                "Successfully merged and saved profiles"
            )
        } catch (e: Exception) {
            Log.e(
                "MergeTraceDumper",
                "Failed to merge and save profiles",
                e
            )
        } finally {
            // Clear the temporary snapshots after merging
            tempSnapshots.clear()
            snapshotCounter.set(0)
        }
    }

    companion object {
        // make it a singleton
        @Volatile
        private var INSTANCE: MergeTraceDumper? = null

        fun getInstance(context: Context): MergeTraceDumper {
            if (INSTANCE != null) {
                return INSTANCE!!
            } else {
                synchronized(this) {
                    if (INSTANCE != null) {
                        return INSTANCE!!
                    } else {
                        val storageDirectory = context.getExternalFilesDir(null) ?: context.filesDir
                        INSTANCE = MergeTraceDumper(storageDirectory.absolutePath)
                        return INSTANCE!!
                    }
                }
            }
        }

        fun getInstance(): MergeTraceDumper {
            return INSTANCE ?: throw IllegalStateException("MergeTraceDumper not initialized")
        }
    }
}
