/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.constants

object DatadogTracingConstants {
    object Tags {
        const val KEY_HTTP_URL: String = "http.url"
        const val KEY_SPAN_KIND: String = "span.kind"
        const val KEY_HTTP_METHOD: String = "http.method"
        const val KEY_HTTP_STATUS: String = "http.status_code"

        const val KEY_ERROR_MSG: String = "error.msg"
        const val KEY_ERROR_TYPE: String = "error.type"
        const val KEY_ERROR_STACK: String = "error.stack"

        const val VALUE_SPAN_KIND_CLIENT: String = "client"
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
}
