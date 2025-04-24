/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import com.datadog.android.api.InternalLogger
import com.google.perftools.profiles.ProfileProto
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
            val pprof = getPprof()
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
            val merged = ProfileProto.Profile.newBuilder()

            // First, collect all unique sample types from all profiles and find the time range
            val sampleTypes = mutableSetOf<ProfileProto.ValueType>()
            var firstProfileStartTime: Long = Long.MAX_VALUE
            var lastProfileStartTime: Long = Long.MIN_VALUE

            tempSnapshots.values.forEach { profile ->
                sampleTypes.addAll(profile.sampleTypeList)
                firstProfileStartTime = minOf(firstProfileStartTime, profile.timeNanos)
                lastProfileStartTime = maxOf(lastProfileStartTime, profile.timeNanos)
            }

            // Add all sample types to the merged profile
            merged.addAllSampleType(sampleTypes)

            // Track next available IDs and ID translations
            var nextMappingId: Long = 1
            var nextFunctionId: Long = 1
            var nextLocationId: Long = 1
            val mappingIdMap = mutableMapOf<Long, Long>()
            val functionIdMap = mutableMapOf<Long, Long>()
            val locationIdMap = mutableMapOf<Long, Long>()

            // Map to track sample counts by stack trace
            val sampleCountMap = mutableMapOf<List<Long>, Long>()

            // Now merge the profiles
            tempSnapshots.values.forEach { profile ->
                // Process mappings first
                profile.mappingList.forEach { mapping ->
                    val newId = nextMappingId++
                    mappingIdMap[mapping.id] = newId
                    merged.addMapping(
                        ProfileProto.Mapping.newBuilder()
                            .setId(newId)
                            .setMemoryStart(mapping.memoryStart)
                            .setMemoryLimit(mapping.memoryLimit)
                            .setFileOffset(mapping.fileOffset)
                            .setFilename(mapping.filename)
                            .setBuildId(mapping.buildId)
                            .build()
                    )
                }

                // Process functions
                profile.functionList.forEach { function ->
                    val newId = nextFunctionId++
                    functionIdMap[function.id] = newId
                    merged.addFunction(
                        ProfileProto.Function.newBuilder()
                            .setId(newId)
                            .setName(function.name)
                            .setSystemName(function.systemName)
                            .setFilename(function.filename)
                            .setStartLine(function.startLine)
                            .build()
                    )
                }

                // Process locations
                profile.locationList.forEach { location ->
                    val newId = nextLocationId++
                    locationIdMap[location.id] = newId
                    val newLines = location.lineList.map { line ->
                        ProfileProto.Line.newBuilder()
                            .setFunctionId(functionIdMap[line.functionId] ?: line.functionId)
                            .setLine(line.line)
                            .build()
                    }
                    merged.addLocation(
                        ProfileProto.Location.newBuilder()
                            .setId(newId)
                            .addAllLine(newLines)
                            .setMappingId(mappingIdMap[location.mappingId] ?: location.mappingId)
                            .setAddress(location.address)
                            .setIsFolded(location.isFolded)
                            .build()
                    )
                }

                // Process samples with updated location IDs and aggregate them
                profile.sampleList.forEach { sample ->
                    val newLocationIds = sample.locationIdList.map { locationId ->
                        locationIdMap[locationId] ?: locationId
                    }

                    // Increment the count for this stack trace
                    val key = newLocationIds
                    sampleCountMap[key] = (sampleCountMap[key] ?: 0L) + 1
                }

                // Merge string table
                val currentStringTable = merged.stringTableList.toMutableList()
                val newStrings = profile.stringTableList.filter { it !in currentStringTable }
                merged.addAllStringTable(newStrings)
            }

            // Add aggregated samples to the merged profile
            sampleCountMap.forEach { (locationIds, count) ->
                merged.addSample(
                    ProfileProto.Sample.newBuilder()
                        .addAllLocationId(locationIds)
                        .addValue(count * 10_000_000L) // Convert count to nanoseconds (count × 10ms)
                        .build()
                )
            }

            // Clear the snapshots map
            tempSnapshots.clear()

            // Set profile metadata using the earliest start time and computed duration
            merged.setTimeNanos(firstProfileStartTime)
            val durationNanos = lastProfileStartTime - firstProfileStartTime
            merged.setDurationNanos(durationNanos)
            if (sampleTypes.isNotEmpty()) {
                merged.setDefaultSampleType(sampleTypes.first().type)
            }

            // Save the merged profile using the start time in the filename
            val startTimeMillis = TimeUnit.NANOSECONDS.toMillis(firstProfileStartTime)
            val endTimeInMillis = TimeUnit.NANOSECONDS.toMillis(lastProfileStartTime)
            val mergedFile = File(tracesDirectoryPath, "merged_${startTimeMillis}_$endTimeInMillis.pprof")
            FileOutputStream(mergedFile).use { out ->
                merged.build().writeTo(out)
            }

            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.MAINTAINER,
                { "Successfully merged and saved profile to ${mergedFile.absolutePath}" }
            )
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to merge and save profiles" },
                e
            )
        }
    }

    fun getPprof(): Profile {
        // Create a Profile builder
        val profileBuilder = ProfileProto.Profile.newBuilder()

        // Prepare string table (index 0 must be empty string per spec)
        val stringTable = mutableListOf<String>()
        val stringIndexMap = mutableMapOf<String, Int>()
        stringTable.add("") // string_table[0] = ""
        stringIndexMap[""] = 0

        fun getStringIndex(str: String): Int {
            stringIndexMap[str]?.let { return it }
            // Add new string to table and map, return its index
            stringTable.add(str)
            val newIndex = stringTable.size - 1
            stringIndexMap[str] = newIndex
            return newIndex
        }

        // Define sample type: "cpu" (type) and "nanoseconds" (unit) as per pprof conventions
        val cpuType = ProfileProto.ValueType.newBuilder()
            .setType(getStringIndex("cpu").toLong())
            .setUnit(getStringIndex("nanoseconds").toLong())
        profileBuilder.addSampleType(cpuType)

        profileBuilder
            .setPeriodType(cpuType)
            .setPeriod(TimeUnit.MILLISECONDS.toNanos(10)) // Match our 10ms collection interval

        // Maps for reusing Function and Location entries
        val functionIdMap = mutableMapOf<String, Long>() // maps fully-qualified function name -> functionId
        val functionBuilders = mutableMapOf<Long, ProfileProto.Function.Builder>()
        val locationIdMap = mutableMapOf<Pair<Long, Int>, Long>() // maps (functionId, line) -> locationId
        val locationBuilders = mutableMapOf<Long, ProfileProto.Location.Builder>()
        var nextFunctionId: Long = 1
        var nextLocationId: Long = 1

        // let take the stacktrace only for the main thread
        for ((thread, stack) in Thread.getAllStackTraces()) {
            if (thread == Thread.currentThread()) {
                continue
            }
            if (thread.name != "main") {
                continue
            }
            if (stack.isEmpty()) continue // skip threads with no Java stack

            // Build list of location IDs for this thread's stack (leaf frame first)
            val locationIdList = mutableListOf<Long>()
            for ((index, frame) in stack.withIndex()) {
                // Determine function (method) info from the stack frame
                val className = frame.className // e.g. "com.example.MyClass"
                val methodName = frame.methodName // e.g. "myFunction"
                val fileName = frame.fileName ?: "Unknown Source"
                val lineNumber = if (frame.lineNumber >= 0) frame.lineNumber else 0

                // Create or reuse a Function entry for this class+method
                val fullFuncName = "$className.$methodName"
                val funcId = functionIdMap.getOrPut(fullFuncName) {
                    val id = nextFunctionId++
                    // Use short method name and full name in the Function (name vs system_name)
                    val nameIndex = getStringIndex(methodName)
                    val fullNameIndex = getStringIndex(fullFuncName)
                    val fileNameIndex = getStringIndex(fileName)
                    val funcBuilder = ProfileProto.Function.newBuilder()
                        .setId(id)
                        .setName(nameIndex.toLong())
                        .setSystemName(fullNameIndex.toLong())
                        .setFilename(fileNameIndex.toLong())
                        .setStartLine(0)
                    functionBuilders[id] = funcBuilder
                    id
                }

                // Create or reuse a Location entry for this (function, line)
                val locKey = funcId to lineNumber
                val locId = locationIdMap.getOrPut(locKey) {
                    val id = nextLocationId++
                    val lineEntry = ProfileProto.Line.newBuilder()
                        .setFunctionId(funcId)
                        .setLine(lineNumber.toLong())
                    val locBuilder = ProfileProto.Location.newBuilder()
                        .setId(id)
                        .addLine(lineEntry)
                    // (Optional: mapping_id can be set if a Mapping entry is used)
                    locationBuilders[id] = locBuilder
                    id
                }
                // Add location ID. Ensure leaf (top of stack) is first as required
                // (Thread.getAllStackTraces() returns stack[0] as the topmost frame)
                locationIdList.add(locId)
            }

            // Create a Sample for this thread (one sample per thread with full stack)
            val sampleBuilder = ProfileProto.Sample.newBuilder()
            locationIdList.forEach { sampleBuilder.addLocationId(it) }
            sampleBuilder.addValue(1L) // Set value to 1 (one occurrence) instead of 10ms

            // Add thread metadata as labels (thread name and ID)
            sampleBuilder.addLabel(
                ProfileProto.Label.newBuilder()
                    .setKey(getStringIndex("thread_id").toLong())
                    .setNum(thread.id) // numeric thread ID
            )
            sampleBuilder.addLabel(
                ProfileProto.Label.newBuilder()
                    .setKey(getStringIndex("thread_name").toLong())
                    .setStr(getStringIndex(thread.name).toLong()) // thread name string
            )

            profileBuilder.addSample(sampleBuilder.build())
        }

        // (Optional) Add a single Mapping entry for the main binary (not strictly required if no addresses)
        val mainMapping = ProfileProto.Mapping.newBuilder()
            .setId(1)
            .setMemoryStart(0).setMemoryLimit(0).setFileOffset(0)
            .setFilename(getStringIndex("android_app").toLong()) // use app identifier or "android_app"
        profileBuilder.addMapping(mainMapping)

        // Add all Function and Location entries to the profile
        for (funcBuilder in functionBuilders.values) {
            profileBuilder.addFunction(funcBuilder)
        }
        for (locBuilder in locationBuilders.values) {
            profileBuilder.addLocation(locBuilder)
        }

        // Add the complete string table to the profile (all strings used by indices)
        profileBuilder.addAllStringTable(stringTable)

        // Set profile metadata: time of collection and duration (duration 0 for snapshot)
        profileBuilder.setTimeNanos(TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()))
        profileBuilder.setDurationNanos(0)
        // Set default sample type to "cpu" (index in string table for "cpu")
        profileBuilder.setDefaultSampleType(getStringIndex("cpu").toLong())

        // Build the Profile and write to file with gzip compression (pprof files are typically gzipped)
        val profile = profileBuilder.build()
        return profile
    }
}
