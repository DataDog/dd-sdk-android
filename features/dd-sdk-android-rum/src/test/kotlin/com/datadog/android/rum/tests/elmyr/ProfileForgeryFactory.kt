/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tests.elmyr

import com.google.perftools.profiles.ProfileProto
import com.google.perftools.profiles.ProfileProto.Profile
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.concurrent.TimeUnit

internal class ProfileForgeryFactory : ForgeryFactory<Profile> {
    override fun getForgery(forge: Forge): Profile {
        val profileBuilder = ProfileProto.Profile.newBuilder()

        // Add sample type
        val sampleType = ProfileProto.ValueType.newBuilder()
            .setType(1) // "cpu" string index
            .setUnit(2) // "nanoseconds" string index
            .build()
        profileBuilder.addSampleType(sampleType)
        profileBuilder.setPeriodType(sampleType)
        profileBuilder.setPeriod(TimeUnit.MILLISECONDS.toNanos(10))

        // Add string table
        val stringTable = listOf(
            "", // index 0 must be empty string
            "cpu",
            "nanoseconds",
            "thread_id",
            "thread_name",
            "main"
        )
        profileBuilder.addAllStringTable(stringTable)

        // Add a sample
        val sample = ProfileProto.Sample.newBuilder()
            .addValue(1L)
            .addLabel(
                ProfileProto.Label.newBuilder()
                    .setKey(3) // "thread_id" string index
                    .setNum(forge.aLong())
            )
            .addLabel(
                ProfileProto.Label.newBuilder()
                    .setKey(4) // "thread_name" string index
                    .setStr(5) // "main" string index
            )
            .build()
        profileBuilder.addSample(sample)

        // Add a function
        val function = ProfileProto.Function.newBuilder()
            .setId(1)
            .setName(1) // "cpu" string index
            .setSystemName(1) // "cpu" string index
            .setFilename(1) // "cpu" string index
            .setStartLine(0)
            .build()
        profileBuilder.addFunction(function)

        // Add a location
        val location = ProfileProto.Location.newBuilder()
            .setId(1)
            .addLine(
                ProfileProto.Line.newBuilder()
                    .setFunctionId(1)
                    .setLine(42)
            )
            .build()
        profileBuilder.addLocation(location)

        // Add a mapping
        val mapping = ProfileProto.Mapping.newBuilder()
            .setId(1)
            .setMemoryStart(0)
            .setMemoryLimit(0)
            .setFileOffset(0)
            .setFilename(1) // "cpu" string index
            .build()
        profileBuilder.addMapping(mapping)

        // Set profile metadata
        profileBuilder.setTimeNanos(TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()))
        profileBuilder.setDurationNanos(0)

        return profileBuilder.build()
    }
}
