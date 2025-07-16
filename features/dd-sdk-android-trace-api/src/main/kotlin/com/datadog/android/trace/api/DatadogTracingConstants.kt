/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api

/**
 * Contains constants related to Datadog tracing. This object groups constants into several
 * nested objects for better organization, such as [Tags], [PrioritySampling],
 * [TracerConfig], [LogAttributes], and [ErrorPriorities].
 */
object DatadogTracingConstants {
    /**
     * Represents the default value for enabling or disabling asynchronous context propagation
     * in Datadog's tracing implementation.
     */
    const val DEFAULT_ASYNC_PROPAGATING: Boolean = true

    /**
     * Contains constants used for tagging spans in the Datadog tracer.
     */
    object Tags {
        /**  The URL of the HTTP request. */
        const val KEY_HTTP_URL: String = "http.url"

        /**
         * Represents the key used for tagging the kind of a span in the Datadog tracer.
         * It is used to indicate the role or type of a span, such as client, server, producer, or consumer.
         */
        const val KEY_SPAN_KIND: String = "span.kind"

        /** Indicates the desired action to be performed for a given resource. */
        const val KEY_HTTP_METHOD: String = "http.method"

        /** The HTTP response status code. */
        const val KEY_HTTP_STATUS: String = "http.status_code"

        /** String representing the error message. */
        const val KEY_ERROR_MSG: String = "error.msg"

        /** String representing the type of the error. */
        const val KEY_ERROR_TYPE: String = "error.type"

        /** Human readable version of the stack. */
        const val KEY_ERROR_STACK: String = "error.stack"

        /**
         * Represents the value used for tagging a span as a "client" in the Datadog tracer with [KEY_SPAN_KIND] key.
         */
        const val VALUE_SPAN_KIND_CLIENT: String = "client"

        /**
         * Represents the value used for tagging a span as a "server" in the Datadog tracer with [KEY_SPAN_KIND] key.
         */
        const val VALUE_SPAN_KIND_SERVER: String = "server"

        /**
         * Represents the value used for tagging a span as a "producer" in the Datadog tracer.
         */
        const val VALUE_SPAN_KIND_PRODUCER: String = "producer"

        /**
         * Represents the value used for tagging a span as a "consumer" in the Datadog tracer.
         */
        const val VALUE_SPAN_KIND_CONSUMER: String = "consumer"

        /**
         * Represents the key used for tagging the analytics sample rate in the Datadog tracer.
         */
        const val KEY_ANALYTICS_SAMPLE_RATE: String = "_dd1.sr.eausr"

        /**
         * Represents the resource name tag used for tracing spans.
         */
        const val RESOURCE_NAME: String = "resource.name"

        /**
         *  String representing the error message.
         */
        const val ERROR_MSG: String = "error.message"

        /**
         *  String representing the type of the error.
         */
        const val ERROR_TYPE: String = "error.type"
    }

    /**
     * [PrioritySampling] class defines the priority sampling decisions used
     * for tracing purposes. These constants represent both system-driven
     * and user-driven sampling decisions to manage trace propagation.
     */
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

    /**
     * Configuration constants for the [com.datadog.android.trace.api.tracer.DatadogTracer] implementation.
     * These constants define the keys used to configure various tracer settings using [java.util.Properties].
     */
    object TracerConfig {
        /** The configuration key used to set span-level tags in the tracer. */
        const val SPAN_TAGS: String = "trace.span.tags"

        /**  Configuration key for setting the trace rate limit in the tracer. */
        const val TRACE_RATE_LIMIT: String = "trace.rate.limit"

        /**
         * A configuration key used to set the minimum number of spans required to trigger a partial flush of trace data.
         */
        const val PARTIAL_FLUSH_MIN_SPANS: String = "trace.partial.flush.min.spans"

        /** The configuration key used to set the trace sample rate for the tracer.*/
        const val TRACE_SAMPLE_RATE: String = "trace.sample.rate"

        /**Constant used to specify the propagation style for extracting context headers during distributed tracing. */
        const val PROPAGATION_STYLE_EXTRACT: String = "propagation.style.extract"

        /** Defines the property key for configuring the propagation style during the injection phase. */
        const val PROPAGATION_STYLE_INJECT: String = "propagation.style.inject"

        /**
         * A constant key used to retrieve or set the service name configuration
         * in the tracer's properties or context.
         */
        const val SERVICE_NAME: String = "service.name"

        /**
         * A constant representing the configuration key for enabling or disabling the rule
         * that uses URLs as resource names in tracing.
         */
        const val URL_AS_RESOURCE_NAME: String = "trace.URLAsResourceNameRule.enabled"

        /**
         * A constant representing the property key used for setting custom global tracer tags.
         */
        const val TAGS: String = "tags"
    }

    /**
     * Defines a set of constant log attributes used for tracing and error reporting within the Datadog tracing library.
     */
    object LogAttributes {
        /**
         * The type or "kind" of an error (only for event="error" logs). E.g., "Exception", "OSError"
         */
        const val ERROR_KIND: String = "error.kind"

        /**
         * The actual Throwable/Exception/Error object instance itself. E.g., a [UnsupportedOperationException] instance.
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

        /**
         * Represents the key for a log attribute used to specify the status of a log entry.
         */
        const val STATUS: String = "status"
    }

    /**
     * Defines constants representing the priorities of error recording in the Datadog tracing library.
     */
    object ErrorPriorities {
        /**
         * Represents the unset value for error prioritization within the Datadog tracing system.
         */
        const val UNSET: Byte = Byte.MIN_VALUE

        /**
         * Represents the HTTP server decorator value.
         */
        const val HTTP_SERVER_DECORATOR: Byte = -1

        /**
         * Represents the default value used in contexts where a specific value is not provided.
         */
        const val DEFAULT: Byte = 0
    }
}
