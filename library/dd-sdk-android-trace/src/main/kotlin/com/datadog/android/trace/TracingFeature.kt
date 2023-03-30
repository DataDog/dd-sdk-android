/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import android.content.Context
import com.datadog.android.trace.internal.data.NoOpWriter
import com.datadog.android.trace.internal.data.TraceWriter
import com.datadog.android.trace.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.NoOpSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.domain.event.SpanEventSerializer
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.trace.common.writer.Writer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracing feature class, which needs to be registered with Datadog SDK instance.
 */
class TracingFeature internal constructor(
    customEndpointUrl: String?,
    internal val spanEventMapper: SpanEventMapper
) : StorageBackedFeature {

    internal var dataWriter: Writer = NoOpWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.TRACING_FEATURE_NAME

    private lateinit var sdkCore: SdkCore

    override fun onInitialize(
        sdkCore: SdkCore,
        appContext: Context
    ) {
        this.sdkCore = sdkCore
        dataWriter = createDataWriter(sdkCore)
        initialized.set(true)
    }

    override val requestFactory: RequestFactory by lazy {
        TracesRequestFactory(
            customEndpointUrl,
            sdkCore._internalLogger
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
        sdkCore: SdkCore
    ): Writer {
        val internalLogger = sdkCore._internalLogger
        return TraceWriter(
            sdkCore,
            legacyMapper = DdSpanToSpanEventMapper(),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, internalLogger),
            serializer = SpanEventSerializer(internalLogger),
            internalLogger = internalLogger
        )
    }

    /**
     * A Builder class for a [TracingFeature].
     */
    class Builder {
        private var customEndpointUrl: String? = null
        private var spanEventMapper: SpanEventMapper = NoOpSpanEventMapper()

        /**
         * Let the Tracing feature target a custom server.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            customEndpointUrl = endpoint
            return this
        }

        /**
         * Sets the [SpanEventMapper] for the Trace [com.datadog.android.trace.model.SpanEvent].
         * You can use this interface implementation to modify the
         * [com.datadog.android.trace.model.SpanEvent] attributes before serialisation.
         *
         * @param eventMapper the [SpanEventMapper] implementation.
         */
        fun setSpanEventMapper(eventMapper: SpanEventMapper): Builder {
            spanEventMapper = eventMapper
            return this
        }

        /**
         * Builds a [TracingFeature] based on the current state of this Builder.
         */
        fun build(): TracingFeature {
            return TracingFeature(
                customEndpointUrl = customEndpointUrl,
                spanEventMapper = spanEventMapper
            )
        }
    }
}
