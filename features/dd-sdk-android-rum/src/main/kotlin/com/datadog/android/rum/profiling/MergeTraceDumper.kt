/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import com.datadog.android.api.InternalLogger
import com.google.perftools.profiles.ProfileProto.Profile
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class MergeTraceDumper(
    private val tracesDirectoryPath: String,
    private val internalLogger: InternalLogger
) {
    private var executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val snapshotCounter = AtomicLong(0)
    private val tempSnapshots = ConcurrentHashMap<Long, Profile>()
    private val isStopped: AtomicBoolean = AtomicBoolean(true)

    fun startDumpingTrace() {
        if (!isStopped.getAndSet(false)) {
            return
        }
        // Clean up previous snapshots
        tempSnapshots.clear()
        snapshotCounter.set(0)

        if (executor.isShutdown) {
            executor = Executors.newSingleThreadScheduledExecutor()
        }
        // Schedule snapshot collection every 10ms
        executor.scheduleWithFixedDelay(
            { collectSnapshot() },
            100,
            10,
            TimeUnit.MILLISECONDS
        )

        // Schedule merging every minute
        executor.scheduleWithFixedDelay(
            { mergeAndSaveSnapshots() },
            1,
            1,
            TimeUnit.MINUTES
        )
    }

    fun stopDumpingTrace() {
        if (isStopped.getAndSet(true)) {
            return
        }

        // Schedule the final merge in the worker thread
        executor.execute {
            try {
                mergeAndSaveSnapshots()
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

    private fun collectSnapshot() {
        if (isStopped.get()) {
            return
        }
        try {
            val pprof = ProfileUtils.createProfile()
            val snapshotId = snapshotCounter.incrementAndGet()
            tempSnapshots[snapshotId] = pprof

            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "Collected snapshot $snapshotId" }
            )
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to collect snapshot" },
                e
            )
        }
    }

    private fun mergeAndSaveSnapshots() {
        try {
            val profiles = tempSnapshots.values
            val mergedProfile = ProfileUtils.merge(tempSnapshots.values.toList())
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
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { "Successfully merged and saved profiles" }
            )
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to merge and save profiles" },
                e
            )
        }finally {

            // Clear the temporary snapshots after merging
            tempSnapshots.clear()
            snapshotCounter.set(0)
        }
    }
}
