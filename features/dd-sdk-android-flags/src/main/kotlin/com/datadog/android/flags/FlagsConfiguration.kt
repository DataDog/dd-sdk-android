/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import okhttp3.Call

private const val DEFAULT_ASSIGNMENT_REQUEST_TIMEOUT_MS = 0L
private const val DEFAULT_ASSIGNMENT_REQUEST_RETRY_COUNT = 0
private const val MAX_ASSIGNMENT_REQUEST_RETRY_COUNT = 10

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
    internal val assignmentRequestTimeoutMs: Long,
    internal val assignmentRequestRetryCount: Int
) {
    /**
     * Copies this configuration while preserving assignment request transport policies.
     *
     * This overload retains the public JVM signature generated before assignment request settings were added to the
     * primary constructor. Keep its parameter list stable for binary compatibility.
     */
    @Suppress("LongParameterList")
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
        assignmentRequestTimeoutMs = assignmentRequestTimeoutMs,
        assignmentRequestRetryCount = assignmentRequestRetryCount
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
        private var assignmentRequestRetryCount: Int = DEFAULT_ASSIGNMENT_REQUEST_RETRY_COUNT
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
         * The SDK constructs each request, including its URL, method, body, and authentication headers, before passing
         * it to [Call.Factory.newCall]. The factory must preserve those request properties. Exposure and evaluation
         * uploads continue to use the SDK's own HTTP transport.
         *
         * The SDK does not take ownership of the factory or its resources. The configured assignment timeout and retry
         * policies are applied on top of calls created by this factory.
         *
         * @param callFactory Factory used to create precomputed assignment calls.
         * @return this [Builder] instance for method chaining.
         */
        fun assignmentRequestCallFactory(callFactory: Call.Factory): Builder {
            assignmentRequestCallFactory = callFactory
            return this
        }

        /**
         * Sets the timeout for each precomputed assignment request.
         * The timeout includes downloading the response body. A value of zero disables the SDK timeout and preserves
         * any timeout already configured on the HTTP client.
         *
         * @param timeoutMs The timeout for each request, in milliseconds.
         * @return this [Builder] instance for method chaining.
         * @throws IllegalArgumentException if [timeoutMs] is negative.
         */
        fun assignmentRequestTimeout(timeoutMs: Long): Builder {
            @Suppress("UnsafeThirdPartyFunctionCall") // Intentional public argument validation.
            require(timeoutMs >= 0) { "timeoutMs must be greater than or equal to 0" }
            assignmentRequestTimeoutMs = timeoutMs
            return this
        }

        /**
         * Sets the number of retries after a transient precomputed assignment request failure.
         * The default is zero (no retries). The retry count must be between zero and ten, inclusive.
         * The SDK retries network errors, timeouts, HTTP 408, and HTTP 5xx responses immediately.
         * The SDK does not retry HTTP 429 responses.
         *
         * @param retryCount The number of retries after the first attempt.
         * @return this [Builder] instance for method chaining.
         * @throws IllegalArgumentException if [retryCount] is outside the supported range.
         */
        fun assignmentRequestRetryCount(retryCount: Int): Builder {
            @Suppress("UnsafeThirdPartyFunctionCall") // Intentional public argument validation.
            require(retryCount in 0..MAX_ASSIGNMENT_REQUEST_RETRY_COUNT) {
                "retryCount must be between 0 and $MAX_ASSIGNMENT_REQUEST_RETRY_COUNT"
            }
            assignmentRequestRetryCount = retryCount
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
            assignmentRequestTimeoutMs = assignmentRequestTimeoutMs,
            assignmentRequestRetryCount = assignmentRequestRetryCount
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
