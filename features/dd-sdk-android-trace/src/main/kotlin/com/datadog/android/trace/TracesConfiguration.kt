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
data class TracesConfiguration internal constructor(
    internal val customEndpointUrl: String?,
    internal val eventMapper: SpanEventMapper
) {

    /**
     * A Builder class for a [TracesConfiguration].
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
         * Builds a [TracesConfiguration] based on the current state of this Builder.
         */
        fun build(): TracesConfiguration {
            return TracesConfiguration(
                customEndpointUrl = customEndpointUrl,
                eventMapper = spanEventMapper
            )
        }
    }
}
