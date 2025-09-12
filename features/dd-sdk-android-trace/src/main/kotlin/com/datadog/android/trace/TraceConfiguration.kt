/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.event.NoOpSpanEventMapper
import com.datadog.android.trace.event.SpanEventMapper

/**
 * Describes configuration to be used for the Traces feature.
 */
data class TraceConfiguration internal constructor(
    internal val customEndpointUrl: String?,
    internal val eventMapper: SpanEventMapper,
    internal val networkInfoEnabled: Boolean
) {

    /**
     * A Builder class for a [TraceConfiguration].
     */
    class Builder {
        private var customEndpointUrl: String? = null
        private var spanEventMapper: SpanEventMapper = NoOpSpanEventMapper()
        private var networkInfoEnabled: Boolean = true

        /**
         * Let the Tracing feature target a custom server.
         * The provided url should be the full endpoint url, e.g.: https://example.com/trace/upload
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
        fun setEventMapper(eventMapper: SpanEventMapper): Builder {
            spanEventMapper = eventMapper
            return this
        }

        /**
         * Enables network information to be automatically added in your logs.
         * @param enabled true by default
         */
        fun setNetworkInfoEnabled(enabled: Boolean): Builder {
            networkInfoEnabled = enabled
            return this
        }

        /**
         * Builds a [TraceConfiguration] based on the current state of this Builder.
         */
        fun build(): TraceConfiguration {
            return TraceConfiguration(
                customEndpointUrl = customEndpointUrl,
                eventMapper = spanEventMapper,
                networkInfoEnabled = networkInfoEnabled
            )
        }
    }
}
