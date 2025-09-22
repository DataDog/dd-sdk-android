/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Describes configuration to be used for the Flags feature.
 * @param enableExposureLogging Log exposure events to RUM.
 * @param customExposureEndpoint If set, overrides the URL for uploading exposure events.
 * @param flaggingProxy If set, overrides the source for precomputed assignments.
 */
data class FlagsConfiguration(
    val enableExposureLogging: Boolean,
    val customExposureEndpoint: String? = null,
    val flaggingProxy: String? = null
) {
    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        private var flagsConfig = DEFAULT_FEATURE_FLAGS_CONFIG

        /**
         * Sets whether exposures should be logged to RUM. This is disabled by default.
         * @param enabled Whether to enable exposure logging.
         */
        fun setEnableExposureLogging(enabled: Boolean): Builder {
            flagsConfig = flagsConfig.copy(enableExposureLogging = enabled)
            return this
        }

        /**
         * Sets a custom endpoint URL for uploading exposure events.
         * @param url Custom exposure endpoint URL. Pass null to use default endpoint.
         */
        fun setCustomExposureEndpoint(url: String?): Builder {
            flagsConfig = flagsConfig.copy(customExposureEndpoint = url)
            return this
        }

        /**
         * Sets a custom proxy URL for fetching precomputed assignments.
         * @param url Custom flagging proxy URL. Pass null to use default source.
         */
        fun setFlaggingProxy(url: String?): Builder {
            flagsConfig = flagsConfig.copy(flaggingProxy = url)
            return this
        }

        /**
         * Builds a [FlagsConfiguration] based on the current state of this Builder.
         * @return a new [FlagsConfiguration] instance.
         */
        fun build(): FlagsConfiguration = FlagsConfiguration(
            enableExposureLogging = flagsConfig.enableExposureLogging,
            customExposureEndpoint = flagsConfig.customExposureEndpoint,
            flaggingProxy = flagsConfig.flaggingProxy
        )
    }

    internal companion object {
        internal val DEFAULT_FEATURE_FLAGS_CONFIG = FlagsConfiguration(
            enableExposureLogging = false,
            customExposureEndpoint = null,
            flaggingProxy = null
        )
    }
}
