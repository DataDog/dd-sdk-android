/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

/**
 * Describes configuration to be used for the Profiling feature.
 */
data class ProfilingConfiguration internal constructor(
    internal val customEndpointUrl: String?
) {

    /**
     * A Builder class for a [ProfilingConfiguration].
     */
    class Builder {

        private var customEndpointUrl: String? = null

        /**
         * Let the Profiling feature target a custom server.
         * The provided url should be the full endpoint url.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            customEndpointUrl = endpoint
            return this
        }

        /**
         * Builds a [ProfilingConfiguration] based on the current state of this Builder.
         */
        fun build(): ProfilingConfiguration {
            return ProfilingConfiguration(
                customEndpointUrl = customEndpointUrl
            )
        }
    }

    companion object {

        /**
         * A default configuration for the Profiling feature.
         */
        val DEFAULT: ProfilingConfiguration = Builder().build()
    }
}
