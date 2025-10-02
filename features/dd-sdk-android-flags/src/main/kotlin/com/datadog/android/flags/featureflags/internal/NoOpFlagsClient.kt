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
 * @param logger Optional logger for warning messages and debug assertions.
 */
internal class NoOpFlagsClient(private val logger: InternalLogger? = null) : FlagsClient {

    /**
     * No-op implementation that ignores context updates.
     * @param context Ignored evaluation context.
     */
    override fun setEvaluationContext(context: EvaluationContext) {
        logWarning("setContext called on NoOpFlagsClient")
        assertDebug { "setContext called on NoOpFlagsClient - ensure FlagsClient.create() was called successfully" }
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean {
        logWarning("resolveBooleanValue called on NoOpFlagsClient for flag '$flagKey'")
        assertDebug {
            "resolveBooleanValue called on NoOpFlagsClient - ensure FlagsClient.create() was called successfully"
        }
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveStringValue(flagKey: String, defaultValue: String): String {
        logWarning("resolveStringValue called on NoOpFlagsClient for flag '$flagKey'")
        assertDebug {
            "resolveStringValue called on NoOpFlagsClient - ensure FlagsClient.create() was called successfully"
        }
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double {
        logWarning("resolveDoubleValue called on NoOpFlagsClient for flag '$flagKey'")
        assertDebug {
            "resolveDoubleValue called on NoOpFlagsClient - ensure FlagsClient.create() was called successfully"
        }
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int {
        logWarning("resolveIntValue called on NoOpFlagsClient for flag '$flagKey'")
        assertDebug {
            "resolveIntValue called on NoOpFlagsClient - ensure FlagsClient.create() was called successfully"
        }
        return defaultValue
    }

    /**
     * Returns the provided default value without any flag evaluation.
     * @param flagKey Ignored flag key.
     * @param defaultValue The value to return.
     * @return The provided default value.
     */
    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject {
        logWarning("resolveStructureValue called on NoOpFlagsClient for flag '$flagKey'")
        assertDebug {
            "resolveStructureValue called on NoOpFlagsClient - ensure FlagsClient.create() was called successfully"
        }
        return defaultValue
    }

    private fun logWarning(message: String) {
        logger?.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { message }
        )
    }

    private inline fun assertDebug(crossinline lazyMessage: () -> String) {
        // In Android, assertions are typically checked using BuildConfig.DEBUG
        // Since we don't have direct access to BuildConfig here, we rely on the fact that
        // InternalLogger implementations may choose to throw exceptions or trigger breakpoints
        // in debug builds when logging at ERROR level with TELEMETRY target
        if (logger != null) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.TELEMETRY,
                { "DEBUG ASSERTION: ${lazyMessage()}" }
            )
        }
    }
}
