/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import okhttp3.Call

private const val DEFAULT_ASSIGNMENT_REQUEST_TIMEOUT_MS = 0L

/**
 * Describes configuration to be used for the Flags feature.
 */
@ExposedCopyVisibility
data class FlagsConfiguration internal constructor(
    internal val trackExposures: Boolean,
    internal val trackEvaluations: Boolean,
    internal val customExposureEndpoint: String?,
    internal val customEvaluationEndpoint: String?,
    internal val customFlagEndpoint: String?,
    internal val evaluationFlushIntervalMs: Long,
    internal val rumIntegrationEnabled: Boolean,
    internal val gracefulModeEnabled: Boolean,
    internal val assignmentRequestCallFactory: Call.Factory?,
    internal val assignmentRequestTimeoutMs: Long
) {
    init {
        @Suppress("UnsafeThirdPartyFunctionCall") // Enforce the public copy invariant.
        require(assignmentRequestTimeoutMs >= 0) {
            "assignmentRequestTimeoutMs must be greater than or equal to 0"
        }
    }

    /**
     * Copies this configuration while preserving assignment request settings.
     *
     * This overload retains the public JVM signature generated before assignment request settings were added.
     */
    fun copy(
        trackExposures: Boolean = this.trackExposures,
        trackEvaluations: Boolean = this.trackEvaluations,
        customExposureEndpoint: String? = this.customExposureEndpoint,
        customEvaluationEndpoint: String? = this.customEvaluationEndpoint,
        customFlagEndpoint: String? = this.customFlagEndpoint,
        evaluationFlushIntervalMs: Long = this.evaluationFlushIntervalMs,
        rumIntegrationEnabled: Boolean = this.rumIntegrationEnabled,
        gracefulModeEnabled: Boolean = this.gracefulModeEnabled
    ): FlagsConfiguration = FlagsConfiguration(
        trackExposures = trackExposures,
        trackEvaluations = trackEvaluations,
        customExposureEndpoint = customExposureEndpoint,
        customEvaluationEndpoint = customEvaluationEndpoint,
        customFlagEndpoint = customFlagEndpoint,
        evaluationFlushIntervalMs = evaluationFlushIntervalMs,
        rumIntegrationEnabled = rumIntegrationEnabled,
        gracefulModeEnabled = gracefulModeEnabled,
        assignmentRequestCallFactory = assignmentRequestCallFactory,
        assignmentRequestTimeoutMs = assignmentRequestTimeoutMs
    )

    /**
     * A Builder class for a [FlagsConfiguration].
     */
    @Suppress("TooManyFunctions")
    class Builder {
        private var trackExposures: Boolean = true
        private var trackEvaluations: Boolean = true
        private var customExposureEndpoint: String? = null
        private var customEvaluationEndpoint: String? = null
        private var customFlagEndpoint: String? = null
        private var assignmentRequestCallFactory: Call.Factory? = null
        private var assignmentRequestTimeoutMs: Long = DEFAULT_ASSIGNMENT_REQUEST_TIMEOUT_MS
        private var evaluationFlushIntervalMs: Long = DEFAULT_EVALUATION_FLUSH_INTERVAL_MS
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
         * Sets whether evaluations should be logged to the dedicated evaluations intake endpoint.
         *
         * Evaluation logging captures aggregated metrics about all flag evaluations, including
         * frequency, default values, and errors. This is enabled by default.
         *
         * @param enabled Whether to enable evaluation logging (default: true).
         * @return this [Builder] instance for method chaining.
         */
        fun trackEvaluations(enabled: Boolean): Builder {
            trackEvaluations = enabled
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
         * Sets a custom endpoint URL for sending evaluation events.
         *
         * By default, evaluation events are sent to the standard Datadog intake endpoint.
         * Use this method to override the endpoint URL for testing or proxy purposes.
         *
         * @param endpoint The custom endpoint URL to use for evaluation event uploads.
         * @return this [Builder] instance for method chaining.
         */
        fun useCustomEvaluationEndpoint(endpoint: String): Builder {
            customEvaluationEndpoint = endpoint
            return this
        }

        /**
         * Sets the flush interval for aggregated evaluation events.
         *
         * Evaluation events are aggregated and flushed periodically.
         * Values outside the valid range (1-60 seconds) will be coerced to the nearest bound.
         *
         * @param intervalMs The flush interval in milliseconds (default: 10,000ms = 10 seconds).
         * @return this [Builder] instance for method chaining.
         */
        fun evaluationFlushInterval(intervalMs: Long): Builder {
            evaluationFlushIntervalMs = intervalMs.coerceIn(
                MIN_EVALUATION_FLUSH_INTERVAL_MS,
                MAX_EVALUATION_FLUSH_INTERVAL_MS
            )
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
         * Sets the HTTP call factory used only for precomputed assignment requests.
         *
         * The SDK constructs the request before it calls [Call.Factory.newCall]. Exposure and evaluation uploads
         * continue to use the SDK transport. The application retains ownership of the factory and its resources.
         * The assignment request timeout applies to calls from this factory.
         *
         * @param callFactory Factory used to create precomputed assignment calls.
         * @return this [Builder] instance for method chaining.
         */
        fun assignmentRequestCallFactory(callFactory: Call.Factory): Builder {
            assignmentRequestCallFactory = callFactory
            return this
        }

        /**
         * Sets the timeout for a precomputed assignment request.
         *
         * The timeout includes the complete response-body download. A value of zero disables the SDK timeout and
         * preserves any timeout already configured on the HTTP call. If the call already has a nonzero timeout, the
         * shorter timeout applies. Negative values are coerced to zero.
         *
         * @param timeoutMs Request timeout in milliseconds.
         * @return this [Builder] instance for method chaining.
         */
        fun assignmentRequestTimeout(timeoutMs: Long): Builder {
            assignmentRequestTimeoutMs = timeoutMs.coerceAtLeast(0)
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
            trackEvaluations = trackEvaluations,
            customExposureEndpoint = customExposureEndpoint,
            customEvaluationEndpoint = customEvaluationEndpoint,
            customFlagEndpoint = customFlagEndpoint,
            evaluationFlushIntervalMs = evaluationFlushIntervalMs,
            rumIntegrationEnabled = rumIntegrationEnabled,
            gracefulModeEnabled = gracefulModeEnabled,
            assignmentRequestCallFactory = assignmentRequestCallFactory,
            assignmentRequestTimeoutMs = assignmentRequestTimeoutMs
        )

        internal companion object {
            private const val DEFAULT_EVALUATION_FLUSH_INTERVAL_MS = 10_000L // 10 seconds
            private const val MIN_EVALUATION_FLUSH_INTERVAL_MS = 1_000L // 1 second
            private const val MAX_EVALUATION_FLUSH_INTERVAL_MS = 60_000L // 60 seconds
        }
    }

    /**
     * Companion object for [FlagsConfiguration] providing factory methods and default instances.
     */
    internal companion object {
        /**
         * The default [FlagsConfiguration] instance.
         *
         * This configuration has:
         * - Exposure tracking enabled
         * - Evaluation tracking enabled
         * - No custom endpoint URL (uses standard Datadog intake)
         * - No custom flag endpoint URL (uses standard Datadog edge assignment endpoint)
         */
        internal val default = Builder().build()
    }
}
