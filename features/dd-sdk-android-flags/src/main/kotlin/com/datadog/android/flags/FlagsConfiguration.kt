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
    internal val trackExposures: Boolean,
    internal val customExposureEndpoint: String?,
    internal val customFlagEndpoint: String?,
    internal val rumIntegrationEnabled: Boolean,
    internal val gracefulModeEnabled: Boolean
) {
    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        private var trackExposures: Boolean = true
        private var customExposureEndpoint: String? = null
        private var customFlagEndpoint: String? = null
        private var rumIntegrationEnabled: Boolean = true
        private var gracefulModeEnabled: Boolean = true

        /**
         * Sets whether exposures should be logged to the dedicated exposures intake endpoint.
         * This is enabled by default.
         * @param enabled Whether to enable exposure logging.
         * @return this [Builder] instance for method chaining.
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
         * @return this [Builder] instance for method chaining.
         */
        fun useCustomExposureEndpoint(endpoint: String): Builder {
            customExposureEndpoint = endpoint
            return this
        }

        /**
         * Sets a custom endpoint URL for fetching precomputed flag assignments.
         * If not called, flag assignments will be fetched from Datadog's default endpoint.
         *
         * @param endpoint The full endpoint URL, e.g., https://dd-flags-proxy.example.com/flags.
         *                 If null, the default endpoint will be used.
         * @return this [Builder] instance for method chaining.
         */
        fun useCustomFlagEndpoint(endpoint: String): Builder {
            customFlagEndpoint = endpoint
            return this
        }

        /**
         * Sets whether RUM evaluation logging is enabled.
         * This adds the result of evaluating a feature flag to the view.
         * Enabled by default.
         * @param enabled whether flag evaluations are added to views in RUM.
         * @return this [Builder] instance for method chaining.
         */
        fun rumIntegrationEnabled(enabled: Boolean): Builder {
            rumIntegrationEnabled = enabled
            return this
        }

        /**
         * Configures error handling behavior in debug builds.
         *
         * Controls how the SDK responds to misuse errors like duplicate client creation or
         * accessing non-existent clients.
         *
         * This setting has no impact on release builds. Release builds will always fail "gracefully".
         *
         * - **Debug (gracefulModeEnabled == false):** Crashes immediately to catch errors early
         * - **Debug (gracefulModeEnabled == true):** Logs to Android Logcat at ERROR level
         * - **Release:** Always uses graceful mode regardless of this setting
         *
         * @param enabled Whether to enable graceful mode in debug builds (default: true)
         * @return this [Builder] instance for method chaining.
         */
        fun gracefulModeEnabled(enabled: Boolean): Builder {
            gracefulModeEnabled = enabled
            return this
        }

        /**
         * Builds a [FlagsConfiguration] based on the current state of this Builder.
         * @return a new [FlagsConfiguration] instance.
         */
        fun build(): FlagsConfiguration = FlagsConfiguration(
            trackExposures = trackExposures,
            customExposureEndpoint = customExposureEndpoint,
            customFlagEndpoint = customFlagEndpoint,
            rumIntegrationEnabled = rumIntegrationEnabled,
            gracefulModeEnabled = gracefulModeEnabled
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
         * - No custom flag endpoint URL (uses standard Datadog edge assignment endpoint)
         */
        internal val default = Builder().build()
    }
}
