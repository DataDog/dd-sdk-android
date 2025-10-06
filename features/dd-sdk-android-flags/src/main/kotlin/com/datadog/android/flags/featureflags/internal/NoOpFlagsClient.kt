/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.model.EvaluationContext
import org.json.JSONObject

/**
 * No-operation implementation of [FlagsClient] used as a fallback when the real client
 * cannot be initialized or is unavailable.
 *
 * All flag resolution methods return their provided default values without performing
 * any operations. The [setEvaluationContext] method silently ignores context updates.
 * This implementation is thread-safe and designed for graceful degradation scenarios.
 *
 * @param name The client name this NoOpClient represents.
 * @param reason The reason why this client is a NoOp (e.g., "Flags feature not enabled").
 * @param logger Optional logger for critical error messages.
 */
internal class NoOpFlagsClient(
    private val name: String,
    private val reason: String,
    private val logger: InternalLogger? = null
) : FlagsClient {

    /**
     * No-op implementation that ignores context updates and logs a critical error.
     * @param context Ignored evaluation context.
     */
    override fun setEvaluationContext(context: EvaluationContext) {
        logCriticalError("setEvaluationContext")
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean {
        logCriticalError("resolveBooleanValue for flag '$flagKey'")
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveStringValue(flagKey: String, defaultValue: String): String {
        logCriticalError("resolveStringValue for flag '$flagKey'")
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double {
        logCriticalError("resolveDoubleValue for flag '$flagKey'")
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int {
        logCriticalError("resolveIntValue for flag '$flagKey'")
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject {
        logCriticalError("resolveStructureValue for flag '$flagKey'")
        return defaultValue
    }

    /**
     * Logs a critical error with both USER and MAINTAINER targets.
     * This ensures visibility in both debug builds (MAINTAINER) and production (USER, if verbosity allows).
     *
     * @param operation The operation being called (e.g., "resolveBooleanValue for flag 'my-flag'")
     */
    private fun logCriticalError(operation: String) {
        logger?.log(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.MAINTAINER),
            {
                "$operation called on NoOpFlagsClient for client '$name' " +
                    "(reason: $reason). NoOpFlagsClient always returns default values. " +
                    "Ensure FlagsClient.Builder(...).build() was called successfully."
            }
        )
    }
}
