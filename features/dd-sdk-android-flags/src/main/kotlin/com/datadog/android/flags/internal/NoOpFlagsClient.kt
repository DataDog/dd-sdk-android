/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.FlagsStateListener
import com.datadog.android.flags.StateObservable
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.model.ResolutionDetails
import org.json.JSONObject

/**
 * No-operation implementation of [FlagsClient] used as a fallback when the real client
 * cannot be initialized or is unavailable.
 *
 * All flag resolution methods return their provided default values without performing
 * any operations. The [setEvaluationContext] method silently ignores context updates.
 * This implementation is thread-safe and designed for graceful degradation scenarios.
 * Logging behavior is determined by the graceful mode policy passed via [logWithPolicy].
 *
 * @param name The client name this NoOpClient represents.
 * @param reason The reason why this client is a NoOp (e.g., "Flags feature not enabled").
 * @param logWithPolicy Function to log messages according to the graceful mode policy.
 */
internal class NoOpFlagsClient(
    private val name: String,
    private val reason: String,
    private val logWithPolicy: LogWithPolicy
) : FlagsClient {

    override val state: StateObservable = object : StateObservable {
        override fun getCurrentState(): FlagsClientState = FlagsClientState.Error(null)
        override fun addListener(listener: FlagsStateListener) { /* no-op */ }
        override fun removeListener(listener: FlagsStateListener) { /* no-op */ }
    }

    /**
     * No-op implementation that ignores context updates and logs a warning.
     * @param context Ignored evaluation context.
     */
    override fun setEvaluationContext(context: EvaluationContext) {
        logOperation("setEvaluationContext", InternalLogger.Level.WARN)
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean {
        logOperation("resolveBooleanValue for flag '$flagKey'", InternalLogger.Level.WARN)
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveStringValue(flagKey: String, defaultValue: String): String {
        logOperation("resolveStringValue for flag '$flagKey'", InternalLogger.Level.WARN)
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double {
        logOperation("resolveDoubleValue for flag '$flagKey'", InternalLogger.Level.WARN)
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int {
        logOperation("resolveIntValue for flag '$flagKey'", InternalLogger.Level.WARN)
        return defaultValue
    }

    /**
     * Returns resolution details with the provided default value without any flag evaluation.
     *
     * Always returns [ErrorCode.PROVIDER_NOT_READY] to indicate the provider is not available.
     *
     * @param T The type of the flag value. Must be non-null.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return [ResolutionDetails] containing the default value with PROVIDER_NOT_READY error.
     */
    override fun <T : Any> resolve(flagKey: String, defaultValue: T): ResolutionDetails<T> {
        logOperation("resolve for flag '$flagKey'", InternalLogger.Level.WARN)
        return ResolutionDetails(
            value = defaultValue,
            errorCode = ErrorCode.PROVIDER_NOT_READY,
            errorMessage = "Provider not ready - using fallback client"
        )
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject {
        logOperation("resolveStructureValue for flag '$flagKey'", InternalLogger.Level.WARN)
        return defaultValue
    }

    /**
     * Logs an operation call on this NoOpFlagsClient using the policy-aware logging function.
     * This ensures visibility in both debug builds (MAINTAINER) and production (USER, if verbosity allows).
     *
     * @param operation The operation being called (e.g., "resolveBooleanValue for flag 'my-flag'")
     * @param level The log level for the message
     */
    private fun logOperation(operation: String, level: InternalLogger.Level) {
        logWithPolicy(
            "$operation called on NoOpFlagsClient for client '$name' " +
                "(reason: $reason). Ensure that a FlagsClient named '$name' is created " +
                "with FlagsClient.Builder(\"$name\").build() before using it.",
            level
        )
    }
}
