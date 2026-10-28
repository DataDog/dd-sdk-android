/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.internal.selection.FlagSelection
import com.datadog.android.flags.model.ErrorCode

private const val DEFAULT_INITIALIZATION_TIMEOUT_MS = 5_000L

/**
 * Describes configuration to be used for the Flags feature.
 */
@ConsistentCopyVisibility
data class FlagsConfiguration internal constructor(
    internal val trackExposures: Boolean,
    internal val trackEvaluations: Boolean,
    internal val customExposureEndpoint: String?,
    internal val customEvaluationEndpoint: String?,
    internal val customFlagEndpoint: String?,
    internal val evaluationFlushIntervalMs: Long,
    internal val rumIntegrationEnabled: Boolean,
    internal val gracefulModeEnabled: Boolean,
    internal val initializationTimeoutMs: Long?,
    internal val flagSelection: FlagSelection?
) {
    /**
     * Copies this configuration and preserves the initialization timeout and the flag selection.
     *
     * This overload keeps the public JVM signature that existed before the initialization timeout was added.
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
        initializationTimeoutMs = initializationTimeoutMs,
        flagSelection = flagSelection
    )

    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        private var trackExposures: Boolean = true
        private var trackEvaluations: Boolean = true
        private var customExposureEndpoint: String? = null
        private var customEvaluationEndpoint: String? = null
        private var customFlagEndpoint: String? = null
        private var evaluationFlushIntervalMs: Long = DEFAULT_EVALUATION_FLUSH_INTERVAL_MS
        private var rumIntegrationEnabled: Boolean = true
        private var gracefulModeEnabled: Boolean = true
        private var initializationTimeoutMs: Long? = DEFAULT_INITIALIZATION_TIMEOUT_MS
        private var flagSelection: FlagSelection? = null

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
         * Sets the maximum time to wait for the first evaluation context to become ready.
         *
         * This timeout covers the complete initialization operation. It includes loading cached data,
         * fetching assignments, reading the response body, decoding JSON, storing assignments, and publishing
         * the ready state. It does not change the HTTP client's timeout. The assignment operation continues after
         * this timeout and can update the client to [com.datadog.android.flags.model.FlagsClientState.Ready].
         *
         * The timeout applies to the first [FlagsClient.setEvaluationContext] call only. That call consumes the
         * timeout even if the operation fails or never starts. Later calls, including retries, have no timer.
         *
         * When the timeout expires, the client becomes
         * [com.datadog.android.flags.model.FlagsClientState.Stale] if matching cached assignments are available.
         * Otherwise, it becomes [com.datadog.android.flags.model.FlagsClientState.Error].
         *
         * A value of zero, or any negative value, disables the initialization timeout.
         *
         * @param timeoutMs The initialization timeout in milliseconds. The default is 5,000 milliseconds.
         * @return this [Builder] instance for method chaining.
         */
        fun initializationTimeout(timeoutMs: Long): Builder = apply {
            initializationTimeoutMs = timeoutMs.takeIf { it > 0 }
        }

        /**
         * Loads only the flags that match the given tags or key patterns.
         *
         * Tags match exactly and are case-sensitive; a flag matches if it has any of the tags. Key patterns are
         * whole-key globs where only `*` is special. Leading and trailing Unicode whitespace is trimmed from every
         * value, empty values are dropped, and duplicates are removed. A null list is treated as absent.
         *
         * Each list accepts at most 31 values after duplicates are removed, and each tag and key pattern is at most
         * 200 UTF-8 bytes after trimming. A selection over a limit, or one that contains a null value or an unpaired
         * UTF-16 surrogate, is not sent, and the full flag configuration is loaded. The server may also ignore a very
         * large selection (`too_many_values`), in which case the full flag configuration is loaded. Servers that do
         * not support flag selection, or have it disabled, return the full flag configuration without a warning.
         * A server reason that the SDK does not know is reported as `unknown`. With [useCustomFlagEndpoint], the proxy
         * must forward the `DD-Flag-Selection-Ignored` response header for an ignored selection to be reported.
         *
         * When the server applies the selection, flags outside it are not loaded, so evaluating them returns the
         * default value with [ErrorCode.FLAG_NOT_FOUND].
         *
         * The selection is fixed when the Flags feature is enabled. Calling this method again replaces the
         * previous selection.
         *
         * @param tags The flag tags to load, or null for none.
         * @param keyPatterns The flag key patterns to load, or null for none.
         * @return this [Builder] instance for method chaining.
         */
        fun flagSelection(tags: List<String>?, keyPatterns: List<String>?): Builder = apply {
            flagSelection = FlagSelection(tags?.toList(), keyPatterns?.toList())
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
            initializationTimeoutMs = initializationTimeoutMs,
            flagSelection = flagSelection
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
