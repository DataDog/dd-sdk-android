/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Describes configuration to be used for the Flags feature.
 * @param exposureProxyEndpoint Custom endpoint URL for uploading exposure events. If null, the default endpoint will be used.
 * @param flaggingProxyEndpoint Custom endpoint URL for proxying precomputed assignment requests. If null, the default endpoint will be used.
 */
data class FlagsConfiguration(val exposureProxyEndpoint: String? = null, val flaggingProxyEndpoint: String? = null) {
    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        private var flagsConfig = DEFAULT_FEATURE_FLAGS_CONFIG

        /**
         * Sets a custom endpoint URL for uploading exposure events.
         * @param endpointUrl The custom endpoint URL. If null, the default endpoint will be used.
         */
        fun useExposureEndpoint(endpointUrl: String?): Builder {
            flagsConfig = flagsConfig.copy(exposureProxyEndpoint = endpointUrl)
            return this
        }

        /**
         * Sets a custom endpoint URL for proxying precomputed assignment requests.
         * @param proxyUrl The custom proxy URL. If null, the default endpoint will be used.
         */
        fun useFlagEndpoint(proxyUrl: String?): Builder {
            flagsConfig = flagsConfig.copy(flaggingProxyEndpoint = proxyUrl)
            return this
        }

        /**
         * Builds a [FlagsConfiguration] based on the current state of this Builder.
         * @return a new [FlagsConfiguration] instance.
         */
        fun build(): FlagsConfiguration = FlagsConfiguration(
            exposureProxyEndpoint = flagsConfig.exposureProxyEndpoint,
            flaggingProxyEndpoint = flagsConfig.flaggingProxyEndpoint
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
            exposureProxyEndpoint = null,
            flaggingProxyEndpoint = null
        )
    }
}
