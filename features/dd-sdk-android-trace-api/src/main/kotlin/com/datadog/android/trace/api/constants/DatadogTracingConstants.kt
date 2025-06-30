/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.constants

object DatadogTracingConstants {
    const val DEFAULT_ASYNC_PROPAGATING: Boolean = true

    object Tags {

        const val KEY_HTTP_URL: String = "http.url"
        const val KEY_SPAN_KIND: String = "span.kind"
        const val KEY_HTTP_METHOD: String = "http.method"
        const val KEY_HTTP_STATUS: String = "http.status_code"

        const val KEY_ERROR_MSG: String = "error.msg"
        const val KEY_ERROR_TYPE: String = "error.type"
        const val KEY_ERROR_STACK: String = "error.stack"

        const val VALUE_SPAN_KIND_CLIENT: String = "client"
        const val VALUE_SPAN_KIND_SERVER: String = "server"
        const val VALUE_SPAN_KIND_PRODUCER: String = "producer"
        const val VALUE_SPAN_KIND_CONSUMER: String = "consumer"

        const val KEY_ANALYTICS_SAMPLE_RATE: String = "_dd1.sr.eausr"
    }

    object PrioritySampling {
        /**
         * Implementation detail of the client. will not be sent to the agent or propagated.
         *
         *
         * Internal value used when the priority sampling flag has not been set on the span context.
         */
        const val UNSET: Int = Int.MIN_VALUE

        /** The sampler has decided to drop the trace.  */
        const val SAMPLER_DROP: Int = 0

        /** The sampler has decided to keep the trace.  */
        const val SAMPLER_KEEP: Int = 1

        /** The user has decided to drop the trace.  */
        const val USER_DROP: Int = -1

        /** The user has decided to keep the trace.  */

        const val USER_KEEP: Int = 2
    }

    object TracerConfig {
        const val SPAN_TAGS: String = "trace.span.tags"
        const val TRACE_RATE_LIMIT: String = "trace.rate.limit"
        const val TRACE_SAMPLE_RATE: String = "trace.sample.rate"
        const val PROPAGATION_STYLE_EXTRACT: String = "propagation.style.extract"
        const val PROPAGATION_STYLE_INJECT: String = "propagation.style.inject"
        const val SERVICE_NAME: String = "service.name"
        const val URL_AS_RESOURCE_NAME: String = "trace.URLAsResourceNameRule.enabled"
    }

    object LogAttributes {
        /**
         * The type or "kind" of an error (only for event="error" logs). E.g., "Exception", "OSError"
         */
        const val ERROR_KIND: String = "error.kind"

        /**
         * The actual Throwable/Exception/Error object instance itself. E.g., A java.lang.UnsupportedOperationException instance
         */
        const val ERROR_OBJECT: String = "error.object"

        /**
         * A stable identifier for some notable moment in the lifetime of a Span. For instance, a mutex
         * lock acquisition or release or the sorts of lifetime events in a browser page load described
         * in the Performance.timing specification. E.g., from Zipkin, "cs", "sr", "ss", or "cr". Or,
         * more generally, "initialized" or "timed out". For errors, "error"
         */
        const val EVENT: String = "event"

        /**
         * A concise, human-readable, one-line message explaining the event. E.g., "Could not connect
         * to backend", "Cache invalidation succeeded"
         */
        const val MESSAGE: String = "message"

        /**
         * A stack trace in platform-conventional format; may or may not pertain to an error.
         */
        const val STACK: String = "stack"

        const val STATUS: String = "status"
    }

    object ErrorPriorities {
        const val UNSET: Byte = Byte.MIN_VALUE
        const val HTTP_SERVER_DECORATOR: Byte = -1
        const val DEFAULT: Byte = 0
    }
}
