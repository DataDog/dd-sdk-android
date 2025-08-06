/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.profiling

import com.google.perftools.profiles.ProfileProto
import com.google.perftools.profiles.ProfileProto.Profile
import java.util.concurrent.TimeUnit

internal object ProfileUtils {

    fun createProfile(snapshotIntervalInNanos: Long = TimeUnit.MILLISECONDS.toNanos(2)): Profile {
        return createProfile(
            Thread.getAllStackTraces(),
            currentTimeInNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()),
            snapshotIntervalInNanos = snapshotIntervalInNanos
        )
    }

    fun createProfile(
        threadStackTraces: Map<Thread, Array<StackTraceElement>>,
        currentTimeInNanos: Long,
        snapshotIntervalInNanos: Long
    ): Profile {
        val stringIndexUtils = StringIndexUtils()
        val profileBuilder = Profile.newBuilder()

        val cpuType = ProfileProto.ValueType.newBuilder()
            .setType(stringIndexUtils.getStringIndex("cpu").toLong())
            .setUnit(stringIndexUtils.getStringIndex("nanoseconds").toLong())
        profileBuilder.addSampleType(cpuType)

        profileBuilder
            .setPeriodType(cpuType)
            .setPeriod(snapshotIntervalInNanos)

        val functionBuilders = mutableMapOf<Long, ProfileProto.Function.Builder>()
        val locationIdSet = mutableSetOf<Long>()
        val locationBuilders = mutableMapOf<Long, ProfileProto.Location.Builder>()
        val functionIdsSet = mutableSetOf<Long>()

        // Process the provided thread stack traces
        for ((thread, stacks) in threadStackTraces) {
            if (thread == Thread.currentThread()) {
                continue
            }
            if (thread.name != "main") {
                continue
            }
            if (stacks.isEmpty()) continue // skip threads with no Java stack

            val locationIdList = mutableListOf<Long>()
            for ((_, frame) in stacks.withIndex()) {
                // Determine function (method) info from the stack frame
                val className = frame.className // e.g. "com.example.MyClass"
                val methodName = frame.methodName // e.g. "myFunction"
                val fileName = frame.fileName ?: "Unknown Source"
                val lineNumber = if (frame.lineNumber >= 0) frame.lineNumber else 0

                // Create or reuse a Function entry for this class+method
                val fullFuncName = "$className.$methodName"
                val funId = frame.functionId()
                val locId = frame.locationId()

                if (!functionIdsSet.contains(funId)) {
                    val nameIndex = stringIndexUtils.getStringIndex(methodName)
                    val fullNameIndex = stringIndexUtils.getStringIndex(fullFuncName)
                    val fileNameIndex = stringIndexUtils.getStringIndex(fileName)
                    val funcBuilder = ProfileProto.Function.newBuilder()
                        .setId(funId)
                        .setName(nameIndex)
                        .setSystemName(fullNameIndex)
                        .setFilename(fileNameIndex.toLong())
                        .setStartLine(0)
                    functionBuilders[funId] = funcBuilder
                    functionIdsSet.add(funId)
                }
                if (!locationIdSet.contains(locId)) {
                    val lineEntry = ProfileProto.Line.newBuilder()
                        .setFunctionId(funId)
                        .setLine(lineNumber.toLong())
                    val locBuilder = ProfileProto.Location.newBuilder()
                        .setId(locId)
                        .addLine(lineEntry)
                    // (Optional: mapping_id can be set if a Mapping entry is used)
                    locationBuilders[locId] = locBuilder
                    locationIdSet.add(locId)
                }
                locationIdList.add(locId)
            }

            // Create a Sample for this thread (one sample per thread with full stack)
            val sampleBuilder = ProfileProto.Sample.newBuilder()
            locationIdList.forEach { sampleBuilder.addLocationId(it) }
            sampleBuilder.addValue(1L) // Set value to 1 (one occurrence) instead of 10ms

            // Add thread metadata as labels (thread name and ID)
            sampleBuilder.addLabel(
                ProfileProto.Label.newBuilder()
                    .setKey(stringIndexUtils.getStringIndex("thread_id").toLong())
                    .setNum(thread.id) // numeric thread ID
            )
            sampleBuilder.addLabel(
                ProfileProto.Label.newBuilder()
                    .setKey(stringIndexUtils.getStringIndex("thread_name").toLong())
                    .setStr(stringIndexUtils.getStringIndex(thread.name).toLong()) // thread name string
            )

            profileBuilder.addSample(sampleBuilder.build())
        }
        for (funcBuilder in functionBuilders.values) {
            profileBuilder.addFunction(funcBuilder)
        }
        for (locBuilder in locationBuilders.values) {
            profileBuilder.addLocation(locBuilder)
        }

        profileBuilder.addAllStringTable(stringIndexUtils.getStringTable())
        profileBuilder.setTimeNanos(currentTimeInNanos)
        profileBuilder.setDurationNanos(0)
        return profileBuilder.build()
    }

    fun merge(
        profiles: List<Profile>,
        snapshotIntervalInNanos: Long = TimeUnit.MILLISECONDS.toNanos(2)
    ): Profile {
        val merged = Profile.newBuilder()

        val stringIndexUtils = StringIndexUtils()
        val cpuType = ProfileProto.ValueType.newBuilder()
            .setType(stringIndexUtils.getStringIndex("cpu").toLong())
            .setUnit(stringIndexUtils.getStringIndex("nanoseconds").toLong())
        merged.addSampleType(cpuType)
        merged
            .setPeriodType(cpuType)
            .setPeriod(snapshotIntervalInNanos)

        var firstProfileStartTime: Long = Long.MAX_VALUE
        var lastProfileStartTime: Long = Long.MIN_VALUE

        profiles.forEach { profile ->
            firstProfileStartTime = minOf(firstProfileStartTime, profile.timeNanos)
            lastProfileStartTime = maxOf(lastProfileStartTime, profile.timeNanos)
        }

        val functionIdMap = mutableMapOf<Long, Long>()
        val locationIdMap = mutableMapOf<Long, Long>()

        profiles.forEach { profile ->
            profile.stringTableList
            // Process functions
            profile.functionList.forEach { function ->
                if (!functionIdMap.contains(function.id)) {
                    val name = profile.stringTableList[function.name.toInt()]
                    val systemName = profile.stringTableList[function.systemName.toInt()]
                    val filename = profile.stringTableList[function.filename.toInt()]
                    val functionBuilder = ProfileProto.Function.newBuilder()
                        .setId(function.id)
                        .setName(stringIndexUtils.getStringIndex(name))
                        .setSystemName(stringIndexUtils.getStringIndex(systemName))
                        .setFilename(stringIndexUtils.getStringIndex(filename))
                        .setStartLine(function.startLine)
                    functionIdMap[function.id] = function.id
                    merged.addFunction(functionBuilder.build())
                }
            }

            // Process locations
            profile.locationList.forEach { location ->
                if (!locationIdMap.contains(location.id)) {
                    locationIdMap[location.id] = location.id
                    merged.addLocation(location)
                }
            }

            // Process samples with updated location IDs and aggregate them
            profile.sampleList.forEach { sample ->
                val labels = sample.labelList.map { label ->
                    val key = profile.stringTableList[label.key.toInt()]
                    val strValue = profile.stringTableList[label.str.toInt()]
                    ProfileProto.Label.newBuilder()
                        .setKey(stringIndexUtils.getStringIndex(key))
                        .setNum(label.num)
                        .setStr(stringIndexUtils.getStringIndex(strValue))
                        .build()
                }
                val newSample = ProfileProto.Sample.newBuilder()
                    .addAllLocationId(
                        sample.locationIdList.map { locationId ->
                            locationIdMap[locationId] ?: locationId
                        }
                    )
                    .addValue(snapshotIntervalInNanos)
                    .addAllLabel(labels)
                    .build()
                merged.addSample(newSample)
            }
        }

        // Set profile metadata using the earliest start time and computed duration
        merged.addAllStringTable(stringIndexUtils.getStringTable())
        merged.setTimeNanos(firstProfileStartTime)
        val durationNanos = lastProfileStartTime - firstProfileStartTime
        merged.setDurationNanos(durationNanos)
        return merged.build()
    }

    internal fun StackTraceElement.locationId(): Long {
        return this.hashCode().toLong()
    }

    internal fun StackTraceElement.functionId(): Long {
        return "${this.className}.${this.methodName}.${this.fileName}".hashCode().toLong()
    }
}
