/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.tracing.internal.data.NoOpWriter
import com.datadog.android.tracing.internal.data.TraceWriter
import com.datadog.android.tracing.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.tracing.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.tracing.internal.domain.event.SpanEventSerializer
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.tracing.internal.net.TracesRequestFactory
import com.datadog.trace.common.writer.Writer
import java.util.concurrent.atomic.AtomicBoolean

internal class TracingFeature(
    private val configuration: Configuration.Feature.Tracing
) : StorageBackedFeature {

    internal var dataWriter: Writer = NoOpWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = TRACING_FEATURE_NAME

    override fun onInitialize(sdkCore: SdkCore, appContext: Context) {
        dataWriter = createDataWriter(sdkCore, configuration)
        initialized.set(true)
    }

    override val requestFactory: RequestFactory = TracesRequestFactory(configuration.endpointUrl)
    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        dataWriter = NoOpWriter()
        initialized.set(false)
    }

    // endregion

    private fun createDataWriter(
        sdkCore: SdkCore,
        configuration: Configuration.Feature.Tracing
    ): Writer {
        return TraceWriter(
            sdkCore,
            legacyMapper = DdSpanToSpanEventMapper(),
            eventMapper = SpanEventMapperWrapper(configuration.spanEventMapper),
            serializer = SpanEventSerializer(),
            internalLogger = internalLogger
        )
    }

    companion object {
        internal const val TRACING_FEATURE_NAME = "tracing"
    }
}
