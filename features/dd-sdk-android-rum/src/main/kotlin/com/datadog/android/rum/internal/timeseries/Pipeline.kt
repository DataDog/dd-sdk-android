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
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.instrumentation.insights.InsightsCollector
import com.datadog.android.rum.internal.instrumentation.insights.NoOpInsightsCollector
import com.datadog.android.rum.internal.timeseries.factory.EventFactory
import com.datadog.android.rum.internal.timeseries.provider.DataPointsReader

@Suppress("LongParameterList")
internal class Pipeline<T : Any>(
    private val sdkCore: FeatureSdkCore,
    private val reader: DataPointsReader<T>,
    private val buffer: Buffer<T>,
    private val eventFactory: EventFactory<T, *>,
    private val dataWriter: DataWriter<Any>,
    private val insightsCollector: InsightsCollector = NoOpInsightsCollector()
) {
    val intervalMs: Long get() = reader.intervalMs

    @WorkerThread
    fun execute(rumContext: RumContext) {
        // reader.read() may hit the filesystem (/proc); kept outside the lock so a concurrent
        // flush() waits only for the buffer and never for I/O.
        val dataPoint = reader.read()
        synchronized(this) {
            dataPoint?.let(buffer::add)
            if (buffer.isFull()) drainAndWrite(rumContext)
        }
    }

    @WorkerThread
    fun flush(rumContext: RumContext) = synchronized(this) { drainAndWrite(rumContext) }

    private fun drainAndWrite(rumContext: RumContext) {
        val dataPoints = buffer.drain().ifEmpty { return }
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext(
                withFeatureContexts = setOf(
                    Feature.SESSION_REPLAY_FEATURE_NAME,
                    Feature.TRACING_FEATURE_NAME
                )
            ) { datadogContext: DatadogContext, writeScope: EventWriteScope ->
                val event = safeCall(ERROR_EVENT_CREATION_FAILED) {
                    eventFactory.create(datadogContext, rumContext, dataPoints)
                } ?: return@withWriteContext

                writeScope { batchWriter ->
                    safeCall(ERROR_FLUSH_FAILED) {
                        if (dataWriter.write(batchWriter, event, EventType.DEFAULT)) {
                            insightsCollector.onTimeseries(eventFactory.eventName)
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
        private const val ERROR_EVENT_CREATION_FAILED = "Timeseries event creation failed"
    }
}
