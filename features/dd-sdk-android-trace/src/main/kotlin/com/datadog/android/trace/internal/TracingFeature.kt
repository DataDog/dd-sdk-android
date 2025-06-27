/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("DEPRECATION")

package com.datadog.android.trace.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.api.span.DatadogSpanWriter
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.impl.DatadogTracing
import com.datadog.android.trace.internal.data.NoOpCoreTracerWriter
import com.datadog.android.trace.internal.data.NoOpWriter
import com.datadog.android.trace.internal.data.OtelTraceWriter
import com.datadog.android.trace.internal.data.TraceWriter
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.domain.event.SpanEventSerializer
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.legacy.trace.common.writer.Writer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracing feature class, which needs to be registered with Datadog SDK instance.
 */
internal class TracingFeature(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?,
    internal val spanEventMapper: SpanEventMapper,
    internal val networkInfoEnabled: Boolean
) : InternalCoreWriterProvider, StorageBackedFeature {

    internal var legacyTracerWriter: Writer = NoOpWriter()
    internal var coreTracerDataWriter: com.datadog.trace.common.writer.Writer = NoOpCoreTracerWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.TRACING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        legacyTracerWriter = createDataWriter(sdkCore)
        coreTracerDataWriter = createOtelDataWriter(sdkCore)
        initialized.set(true)
    }

    override val requestFactory: RequestFactory by lazy {
        TracesRequestFactory(
            customEndpointUrl,
            sdkCore.internalLogger
        )
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        legacyTracerWriter = NoOpWriter()
        initialized.set(false)
    }

    // endregion

    // region InternalCoreWriterProvider

    override fun getCoreTracerWriter(): DatadogSpanWriter {
        return DatadogTracing.wrapWriter(coreTracerDataWriter)
    }

    // endregion

    private fun createDataWriter(
        sdkCore: FeatureSdkCore
    ): Writer {
        val internalLogger = sdkCore.internalLogger
        return TraceWriter(
            sdkCore,
            ddSpanToSpanEventMapper = DdSpanToSpanEventMapper(networkInfoEnabled),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, internalLogger),
            serializer = SpanEventSerializer(internalLogger),
            internalLogger = internalLogger
        )
    }

    private fun createOtelDataWriter(
        sdkCore: FeatureSdkCore
    ): com.datadog.trace.common.writer.Writer {
        val internalLogger = sdkCore.internalLogger
        return OtelTraceWriter(
            sdkCore,
            ddSpanToSpanEventMapper = CoreTracerSpanToSpanEventMapper(networkInfoEnabled),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, internalLogger),
            serializer = SpanEventSerializer(internalLogger),
            internalLogger = internalLogger
        )
    }

    companion object {
        internal const val IS_OPENTELEMETRY_ENABLED_CONFIG_KEY = "is_opentelemetry_enabled"
        internal const val OPENTELEMETRY_API_VERSION_CONFIG_KEY = "opentelemetry_api_version"
    }
}
