/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import android.content.Context
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.internal.data.NoOpWriter
import com.datadog.android.trace.internal.data.TraceWriter
import com.datadog.android.trace.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.domain.event.SpanEventSerializer
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.trace.common.writer.Writer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracing feature class, which needs to be registered with Datadog SDK instance.
 */
internal class TracingFeature constructor(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?,
    internal val spanEventMapper: SpanEventMapper
) : StorageBackedFeature {

    internal var dataWriter: Writer = NoOpWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.TRACING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        dataWriter = createDataWriter(sdkCore)
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
        dataWriter = NoOpWriter()
        initialized.set(false)
    }

    // endregion

    private fun createDataWriter(
        sdkCore: FeatureSdkCore
    ): Writer {
        val internalLogger = sdkCore.internalLogger
        return TraceWriter(
            sdkCore,
            legacyMapper = DdSpanToSpanEventMapper(),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, internalLogger),
            serializer = SpanEventSerializer(internalLogger),
            internalLogger = internalLogger
        )
    }
}
