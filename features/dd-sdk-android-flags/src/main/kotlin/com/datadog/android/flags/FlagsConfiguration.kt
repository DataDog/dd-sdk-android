/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Describes configuration to be used for the Flags feature.
 */
data class FlagsConfiguration internal constructor(
    internal val trackExposures: Boolean = true,
    internal val customExposureEndpoint: String? = null
) {
    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        private var trackExposures: Boolean = true
        private var customExposureEndpoint: String? = null

        /**
         * Sets whether exposures should be logged to the dedicated exposures intake endpoint. This is enabled by default.
         * @param enabled Whether to enable exposure logging.
         */
        fun trackExposures(enabled: Boolean): Builder {
            trackExposures = enabled
            return this
        }

        /**
         * Sets a custom endpoint URL for sending exposure events.
         *
         * By default, exposure events are sent to the standard Datadog intake endpoint.
         * Use this method to override the endpoint URL for testing or proxy purposes.
         *
         * @param endpoint The custom endpoint URL to use for exposure event uploads.
         * @return this Builder instance for method chaining.
         */
        fun useCustomExposureEndpoint(endpoint: String): Builder {
            customExposureEndpoint = endpoint
            return this
        }

        /**
         * Builds a [FlagsConfiguration] based on the current state of this Builder.
         * @return a new [FlagsConfiguration] instance.
         */
        fun build(): FlagsConfiguration = FlagsConfiguration(
            trackExposures = trackExposures,
            customExposureEndpoint = customExposureEndpoint
        )
    }

    /**
     * Companion object for [FlagsConfiguration] providing factory methods and default instances.
     */
    companion object {
        /**
         * The default [FlagsConfiguration] instance.
         *
         * This configuration has:
         * - Exposure tracking enabled
         * - No custom endpoint URL (uses standard Datadog intake)
         */
        internal val default = FlagsConfiguration()
    }
}
