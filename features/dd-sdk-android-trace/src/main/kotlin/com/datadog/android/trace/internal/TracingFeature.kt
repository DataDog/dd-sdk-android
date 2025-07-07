/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.impl.internal.DatadogSpanWriterWrapper
import com.datadog.android.trace.internal.data.NoOpCoreTracerWriter
import com.datadog.android.trace.internal.data.CoreTraceWriter
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.domain.event.SpanEventSerializer
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.trace.common.writer.Writer
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

    internal var coreTracerDataWriter: Writer = NoOpCoreTracerWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.TRACING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        coreTracerDataWriter = createCoreTracerDataWriter(sdkCore)
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
        initialized.set(false)
    }

    // endregion

    // region InternalCoreWriterProvider

    override fun getCoreTracerWriter() = DatadogSpanWriterWrapper(coreTracerDataWriter)

    // endregion

    private fun createCoreTracerDataWriter(sdkCore: FeatureSdkCore): Writer {
        val internalLogger = sdkCore.internalLogger
        return CoreTraceWriter(
            sdkCore,
            ddSpanToSpanEventMapper = CoreTracerSpanToSpanEventMapper(networkInfoEnabled),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, internalLogger),
            serializer = SpanEventSerializer(internalLogger),
            internalLogger = internalLogger
        )
    }
}
