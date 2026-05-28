/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import androidx.annotation.WorkerThread
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.internal.timeseries.serializer.JsonSerializer
import com.google.gson.JsonObject

internal class Pipeline<T : Any>(
    private val sdkCore: FeatureSdkCore,
    private val reader: DataPointsReader<T>,
    private val buffer: Buffer<T>,
    private val serializer: JsonSerializer<T>,
    private val dataWriter: DataWriter<Any>
) {
    val intervalMs: Long get() = reader.intervalMs

    @WorkerThread
    fun execute() {
        reader.read()?.let(buffer::add)
        if (buffer.isFull()) flush()
    }

    @WorkerThread
    fun flush() = buffer.drain()
        .takeIf { it.isNotEmpty() }
        ?.let(serializer::serialize)
        ?.let(::write)

    private fun write(json: JsonObject) = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ?.withWriteContext { _, writeScope ->
            writeScope { dataWriter.write(it, json, EventType.DEFAULT) }
        }
}
