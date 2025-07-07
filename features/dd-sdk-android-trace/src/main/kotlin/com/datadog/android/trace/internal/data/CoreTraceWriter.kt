/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.data

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.event.EventMapper
import com.datadog.android.event.NoOpEventMapper
import com.datadog.android.internal.concurrent.CompletableFuture
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.internal.SpanAttributes
import com.datadog.android.trace.internal.domain.event.ContextAwareMapper
import com.datadog.android.trace.internal.storage.ContextAwareSerializer
import com.datadog.android.trace.model.SpanEvent
import com.datadog.legacy.trace.api.sampling.PrioritySampling
import com.datadog.trace.common.writer.Writer
import com.datadog.trace.core.DDSpan
import java.util.Locale

internal class CoreTraceWriter(
    private val sdkCore: FeatureSdkCore,
    internal val ddSpanToSpanEventMapper: ContextAwareMapper<DDSpan, SpanEvent>,
    internal val eventMapper: EventMapper<SpanEvent> = NoOpEventMapper(),
    private val serializer: ContextAwareSerializer<SpanEvent>,
    private val internalLogger: InternalLogger
) : Writer {

    // region Writer
    override fun start() {
        // NO - OP
    }

    override fun write(trace: List<DDSpan>?) {
        if (trace == null) return
        sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
            ?.withWriteContext { datadogContext, writeScope ->
                val writeSpans = trace
                    .filter {
                        it.samplingPriority() !in DROP_SAMPLING_PRIORITIES
                    }
                    .map { it.bundleWithRum() }
                // TODO RUM-4092 Add the capability in the serializer to handle multiple spans in one payload
                writeScope {
                    writeSpans
                        .forEach { span ->
                            @Suppress("ThreadSafety") // called in the worker context
                            writeSpan(datadogContext, it, span)
                        }
                }
            }
    }

    override fun flush(): Boolean {
        // NO - OP
        return true
    }

    override fun incrementDropCounts(p0: Int) {
        // NO - OP
    }

    override fun close() {
        // NO - OP
    }

    // endregion

    @Suppress("UNCHECKED_CAST")
    private fun DDSpan.bundleWithRum(): DDSpan {
        val initialDatadogContext = tags[SpanAttributes.DATADOG_INITIAL_CONTEXT]
            as? CompletableFuture<DatadogContext>
        setTag(SpanAttributes.DATADOG_INITIAL_CONTEXT, null as String?)
        if (initialDatadogContext != null) {
            if (initialDatadogContext.isComplete()) {
                val rumContext = initialDatadogContext.value
                    .featuresContext[Feature.RUM_FEATURE_NAME]
                    .orEmpty()
                setTag(
                    LogAttributes.RUM_APPLICATION_ID,
                    rumContext["application_id"] as? String
                )
                setTag(LogAttributes.RUM_SESSION_ID, rumContext["session_id"] as? String)
                setTag(LogAttributes.RUM_VIEW_ID, rumContext["view_id"] as? String)
                setTag(LogAttributes.RUM_ACTION_ID, rumContext["action_id"] as? String)
            } else {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR }
                )
            }
        }
        return this
    }

    @WorkerThread
    private fun writeSpan(
        datadogContext: DatadogContext,
        writer: EventBatchWriter,
        span: DDSpan
    ) {
        val spanEvent = ddSpanToSpanEventMapper.map(datadogContext, span)
        val mapped = eventMapper.map(spanEvent) ?: return
        try {
            val serialized = serializer
                .serialize(datadogContext, mapped)
                ?.toByteArray(Charsets.UTF_8) ?: return
            synchronized(this) {
                writer.write(RawBatchEvent(data = serialized), batchMetadata = null, eventType = EventType.DEFAULT)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                { ERROR_SERIALIZING.format(Locale.US, mapped.javaClass.simpleName) },
                e
            )
        }
    }

    companion object {
        internal const val ERROR_SERIALIZING = "Error serializing %s model"
        internal const val INITIAL_DATADOG_CONTEXT_NOT_AVAILABLE_ERROR = "Initial span creation Datadog context" +
            " is not available at the write time."
        internal val DROP_SAMPLING_PRIORITIES = setOf(PrioritySampling.SAMPLER_DROP, PrioritySampling.USER_DROP)
    }
}
