/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.FlagsClient
import com.datadog.android.flags.featureflags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.model.EvaluationContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Production implementation of [FlagsClient] that integrates with Datadog's flag evaluation system.
 *
 * This implementation fetches precomputed flag values from the local repository and handles
 * type conversion with appropriate fallback to default values. All methods are thread-safe
 * and designed for high-frequency usage in mobile applications.
 *
 * @param featureSdkCore the SDK core for logging and internal operations
 * @param evaluationsManager manages flag evaluations and network requests
 * @param flagsRepository local storage for precomputed flag values
 */
internal class DatadogFlagsClient(
    private val featureSdkCore: FeatureSdkCore,
    private val evaluationsManager: EvaluationsManager,
    private val flagsRepository: FlagsRepository
) : FlagsClient {

    /**
     * Sets the evaluation context and triggers a background fetch of precomputed flags.
     *
     * This method converts the public [EvaluationContext] to an internal format,
     * validates the context data, and asynchronously fetches updated flag evaluations
     * from the Datadog service. If context validation fails, the operation is logged
     * and silently ignored.
     *
     * This method is thread-safe and non-blocking. Flag updates will be available
     * for subsequent resolve calls once the background operation completes.
     *
     * @param context The evaluation context containing targeting key and attributes.
     * Must contain a valid targeting key; invalid contexts are logged and ignored.
     */
    override fun setEvaluationContext(context: EvaluationContext) {
        // Validate targeting key is not blank
        if (context.targetingKey.isBlank()) {
            featureSdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "Invalid evaluation context: targeting key cannot be blank or whitespace-only" }
            )
            return
        }

        // Pass to manager to handle network request and atomic storage
        evaluationsManager.updateEvaluationsForContext(context)
    }

    /**
     * Resolves a boolean flag value from the local repository.
     *
     * This method performs strict boolean parsing of the stored flag value.
     * Only "true" and "false" (case-sensitive) string values will be converted;
     * all other values result in the default being returned.
     *
     * This method is thread-safe and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The boolean value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean {
        val precomputedFlag = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedFlag?.variationValue?.toBooleanStrictOrNull() ?: defaultValue
    }

    /**
     * Resolves a string flag value from the local repository.
     *
     * This method returns the stored string value as-is without any type conversion.
     * This method is thread-safe and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved.
     * @return The string value of the flag, or the default value if unavailable.
     */
    override fun resolveStringValue(flagKey: String, defaultValue: String): String {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue ?: defaultValue
    }

    /**
     * Resolves an integer flag value from the local repository.
     *
     * This method attempts to parse the stored string value as an integer.
     * If parsing fails, the default value is returned. This method is thread-safe
     * and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The integer value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveIntValue(flagKey: String, defaultValue: Int): Int {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.toIntOrNull() ?: defaultValue
    }

    /**
     * Resolves a double flag value from the local repository.
     *
     * This method attempts to parse the stored string value as a double.
     * If parsing fails, the default value is returned. This method is thread-safe
     * and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The double value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveDoubleValue(flagKey: String, defaultValue: Double): Double {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.toDoubleOrNull() ?: defaultValue
    }

    /**
     * Resolves a structured flag value from the local repository.
     *
     * This method attempts to parse the stored string value as a JSON object.
     * If parsing fails, an error is logged and the default value is returned.
     * This method is thread-safe and performs no network operations.
     *
     * @param flagKey The name of the flag to query. Cannot be null.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The JSON object value of the flag, or the default value if unavailable or unparseable.
     */
    override fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject {
        val precomputedData = flagsRepository.getPrecomputedFlag(flagKey)
        return precomputedData?.variationValue?.let {
            try {
                JSONObject(it)
            } catch (e: JSONException) {
                featureSdkCore.internalLogger.log(
                    level = InternalLogger.Level.ERROR,
                    target = InternalLogger.Target.MAINTAINER,
                    messageBuilder = { "Failed to parse JSON for key: $flagKey" },
                    throwable = e
                )
                featureSdkCore.internalLogger.log(
                    level = InternalLogger.Level.WARN,
                    target = InternalLogger.Target.USER,
                    messageBuilder = { "Failed to parse feature flag" },
                    throwable = e
                )
                defaultValue
            }
        } ?: defaultValue
    }
}
