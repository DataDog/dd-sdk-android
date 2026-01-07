/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import androidx.annotation.FloatRange

/**
 * Describes configuration to be used for the Profiling feature.
 */
data class ProfilingConfiguration internal constructor(
    internal val customEndpointUrl: String?,
    internal val sampleRate: Float
) {

    /**
     * A Builder class for a [ProfilingConfiguration].
     */
    class Builder {

        private var customEndpointUrl: String? = null
        private var sampleRate: Float = DEFAULT_SAMPLE_RATE

        /**
         * Sets the sampling rate for profiling.
         *
         * @param sampleRate The sampling rate, expressed as a percentage between 0 and 100 (inclusive).
         * A value of 0 disables profiling entirely. A value of 100 enables profiling for all eligible
         * requests, subject to rate limiting enforced by [android.os.ProfilingManager].
         */
        fun setSampleRate(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float): Builder {
            this.sampleRate = sampleRate
            return this
        }

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
                customEndpointUrl = customEndpointUrl,
                sampleRate = sampleRate
            )
        }
    }

    companion object {

        private const val DEFAULT_SAMPLE_RATE = 100f

        /**
         * A default configuration for the Profiling feature.
         */
        val DEFAULT: ProfilingConfiguration = Builder().build()
    }
}
