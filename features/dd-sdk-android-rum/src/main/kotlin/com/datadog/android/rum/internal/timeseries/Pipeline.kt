/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.NoOpInsightsCollector
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader
import com.datadog.android.rum.internal.timeseries.serializer.JsonSerializer
import com.datadog.android.rum.internal.timeseries.serializer.TimeseriesAttributes
import com.google.gson.JsonElement

@Suppress("LongParameterList")
internal class Pipeline<T : Any>(
    private val sdkCore: FeatureSdkCore,
    private val reader: DataPointsReader<T>,
    private val buffer: Buffer<T>,
    private val serializer: JsonSerializer<T>,
    private val dataWriter: DataWriter<Any>,
    private val insightsCollector: InsightsCollector = NoOpInsightsCollector()
) {
    val intervalMs: Long get() = reader.intervalMs

    @WorkerThread
    fun execute() {
        reader.read()?.let(buffer::add)
        if (buffer.isFull()) flush()
    }

    @WorkerThread
    fun flush() {
        val dataPoints = buffer.drain().ifEmpty { return }
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext: DatadogContext, writeScope: EventWriteScope ->
                val json = safeCall(ERROR_SERIALIZATION_FAILED) {
                    serializer.serialize(datadogContext, dataPoints)
                } ?: return@withWriteContext

                writeScope { batchWriter ->
                    safeCall(ERROR_FLUSH_FAILED) {
                        if (dataWriter.write(batchWriter, json, EventType.DEFAULT)) {
                            insightsCollector.onTimeseries(json.timeseriesName)
                        }
                    }
                }
            }
    }

    private inline fun <R> safeCall(message: String, block: () -> R): R? = try {
        block()
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        sdkCore.internalLogger.log(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            messageBuilder = { message },
            throwable = t
        )
        null
    }

    private companion object {
        private const val ERROR_FLUSH_FAILED = "Timeseries flush failed"
        private const val ERROR_SERIALIZATION_FAILED = "Timeseries serialization failed"
        private val JsonElement.timeseriesName: String
            get() = asJsonObject.getAsJsonObject(TimeseriesAttributes.KEY_TIMESERIES)
                ?.get(TimeseriesAttributes.KEY_NAME)
                ?.asString.orEmpty()
    }
}
