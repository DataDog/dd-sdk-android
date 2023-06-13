/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.data

import androidx.annotation.WorkerThread
import com.datadog.android.event.EventMapper
import com.datadog.android.trace.internal.domain.event.ContextAwareMapper
import com.datadog.android.trace.internal.storage.ContextAwareSerializer
import com.datadog.android.trace.model.SpanEvent
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.opentracing.DDSpan
import com.datadog.trace.common.writer.Writer
import java.util.Locale

internal class TraceWriter(
    private val sdkCore: FeatureSdkCore,
    private val legacyMapper: ContextAwareMapper<DDSpan, SpanEvent>,
    internal val eventMapper: EventMapper<SpanEvent>,
    private val serializer: ContextAwareSerializer<SpanEvent>,
    private val internalLogger: InternalLogger
) : Writer {

    // region Writer
    override fun start() {
        // NO - OP
    }

    override fun write(trace: MutableList<DDSpan>?) {
        if (trace == null) return
        sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                trace.forEach { span ->
                    @Suppress("ThreadSafety") // called in the worker context
                    writeSpan(datadogContext, eventBatchWriter, span)
                }
            }
    }

    override fun close() {
        // NO - OP
    }

    override fun incrementTraceCount() {
        // NO - OP
    }

    // endregion

    @WorkerThread
    private fun writeSpan(
        datadogContext: DatadogContext,
        writer: EventBatchWriter,
        span: DDSpan
    ) {
        val spanEvent = legacyMapper.map(datadogContext, span)
        val mapped = eventMapper.map(spanEvent) ?: return
        try {
            val serialized = serializer
                .serialize(datadogContext, mapped)
                ?.toByteArray(Charsets.UTF_8) ?: return
            synchronized(this) {
                writer.write(serialized, null)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                ERROR_SERIALIZING.format(Locale.US, mapped.javaClass.simpleName),
                e
            )
        }
    }

    companion object {
        internal const val ERROR_SERIALIZING = "Error serializing %s model"
    }
}
