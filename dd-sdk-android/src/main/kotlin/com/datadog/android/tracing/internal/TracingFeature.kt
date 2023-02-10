/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import android.content.Context
import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogSite
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.event.NoOpSpanEventMapper
import com.datadog.android.event.SpanEventMapper
import com.datadog.android.tracing.internal.data.NoOpWriter
import com.datadog.android.tracing.internal.data.TraceWriter
import com.datadog.android.tracing.internal.domain.event.DdSpanToSpanEventMapper
import com.datadog.android.tracing.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.tracing.internal.domain.event.SpanEventSerializer
import com.datadog.android.v2.api.EnvironmentProvider
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.tracing.internal.net.TracesRequestFactory
import com.datadog.trace.common.writer.Writer
import java.util.concurrent.atomic.AtomicBoolean

internal class TracingFeature(
    endpointUrl: String,
    internal val spanEventMapper: SpanEventMapper
) : StorageBackedFeature {

    internal var dataWriter: Writer = NoOpWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = Feature.TRACING_FEATURE_NAME

    override fun onInitialize(
        sdkCore: SdkCore,
        appContext: Context,
        environmentProvider: EnvironmentProvider
    ) {
        dataWriter = createDataWriter(sdkCore)
        initialized.set(true)
    }

    override val requestFactory: RequestFactory = TracesRequestFactory(endpointUrl)
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
        return TraceWriter(
            sdkCore,
            legacyMapper = DdSpanToSpanEventMapper(),
            eventMapper = SpanEventMapperWrapper(spanEventMapper),
            serializer = SpanEventSerializer(),
            internalLogger = internalLogger
        )
    }

    /**
     * A Builder class for a [TracingFeature].
     */
    class Builder {
        private var endpointUrl = DatadogEndpoint.TRACES_US1
        private var spanEventMapper: SpanEventMapper = NoOpSpanEventMapper()

        /**
         * Let the Tracing feature target your preferred Datadog's site.
         */
        fun useSite(site: DatadogSite): Builder {
            endpointUrl = site.tracesEndpoint()
            return this
        }

        /**
         * Let the Tracing feature target a custom server.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            endpointUrl = endpoint
            return this
        }

        /**
         * Sets the [SpanEventMapper] for the Trace [com.datadog.android.tracing.model.SpanEvent].
         * You can use this interface implementation to modify the
         * [com.datadog.android.tracing.model.SpanEvent] attributes before serialisation.
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
                endpointUrl = endpointUrl,
                spanEventMapper = spanEventMapper
            )
        }
    }
}
