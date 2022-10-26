/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.tracing.internal.data.NoOpWriter
import com.datadog.android.tracing.internal.data.TraceWriter
import com.datadog.android.tracing.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.tracing.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.tracing.internal.domain.event.SpanEventSerializer
import com.datadog.android.v2.api.SdkCore
import com.datadog.trace.common.writer.Writer
import java.util.concurrent.atomic.AtomicBoolean

internal class TracingFeature(
    private val sdkCore: SdkCore
) {

    internal var dataWriter: Writer = NoOpWriter()
    internal val initialized = AtomicBoolean(false)

    // region SdkFeature

    fun initialize(configuration: Configuration.Feature.Tracing) {
        dataWriter = createDataWriter(configuration)
        initialized.set(true)
    }

    fun stop() {
        dataWriter = NoOpWriter()
        initialized.set(false)
    }

    private fun createDataWriter(
        configuration: Configuration.Feature.Tracing
    ): Writer {
        return TraceWriter(
            sdkCore,
            legacyMapper = DdSpanToSpanEventMapper(),
            eventMapper = SpanEventMapperWrapper(configuration.spanEventMapper),
            serializer = SpanEventSerializer(),
            internalLogger = sdkLogger
        )
    }

    // endregion

    companion object {
        internal const val TRACING_FEATURE_NAME = "tracing"
    }
}
