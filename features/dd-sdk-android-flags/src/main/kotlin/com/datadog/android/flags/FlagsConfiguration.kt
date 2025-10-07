/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Describes configuration to be used for the Flags feature.
 * @param customExposureEndpoint Custom endpoint URL for uploading exposure events. If null, the default endpoint will be used.
 * @param customFlagEndpoint Custom endpoint URL for proxying precomputed flag assignment requests. If null, the default endpoint will be used.
 * @param enableExposureLogging Whether to enable exposure event logging. Defaults to true.
 */
data class FlagsConfiguration(
    val customExposureEndpoint: String? = null,
    val customFlagEndpoint: String? = null,
    val enableExposureLogging: Boolean = true
) {
    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        private var customExposureEndpoint: String? = null
        private var customFlagEndpoint: String? = null
        private var enableExposureLogging: Boolean = true

        /**
         * Sets a custom endpoint URL for uploading exposure events (flag evaluations).
         * If not called, exposure events will be sent to Datadog's default intake endpoint for the configured site.
         *
         * @param endpoint The full endpoint URL, e.g., https://example.com/exposure/upload.
         *                 If null, the default endpoint will be used.
         */
        fun useCustomExposureEndpoint(endpoint: String?): Builder {
            customExposureEndpoint = endpoint
            return this
        }

        /**
         * Sets a custom endpoint URL for fetching precomputed flag assignments (flagging proxy).
         * If not called, flag assignments will be fetched from Datadog's default endpoint.
         *
         * @param endpoint The full proxy endpoint URL, e.g., https://proxy.example.com/flags.
         *                 If null, the default endpoint will be used.
         */
        fun useCustomFlagEndpoint(endpoint: String?): Builder {
            customFlagEndpoint = endpoint
            return this
        }

        /**
         * Enables or disables exposure event tracking (via exposures event track).
         * @param enabled Whether to enable exposure event logging. Defaults to true.
         */
        fun setExposureLoggingEnabled(enabled: Boolean): Builder {
            enableExposureLogging = enabled
            return this
        }

        /**
         * Builds a [FlagsConfiguration] based on the current state of this Builder.
         * @return a new [FlagsConfiguration] instance.
         */
        fun build(): FlagsConfiguration = FlagsConfiguration(
            customExposureEndpoint = customExposureEndpoint,
            customFlagEndpoint = customFlagEndpoint,
            enableExposureLogging = enableExposureLogging
        )
    }

    /**
     * Companion object that provides factory methods for creating the default [FlagsConfiguration] instance.
     */
    companion object {
        /**
         * Creates a [FlagsConfiguration] with default settings.
         * @return a new [FlagsConfiguration] instance with default configuration values.
         */
        fun defaultConfiguration(): FlagsConfiguration = DEFAULT_FEATURE_FLAGS_CONFIG

        internal val DEFAULT_FEATURE_FLAGS_CONFIG = FlagsConfiguration(
            customExposureEndpoint = null,
            customFlagEndpoint = null,
            enableExposureLogging = true
        )
    }
}
