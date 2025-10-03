/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.flags.featureflags.model.EvaluationContext
import org.json.JSONObject

/**
 * Provider interface for evaluating feature flags and experiments.
 *
 * This interface defines the public API that applications use to retrieve feature flag values.
 * It follows the OpenFeature specification closely, to simplify implementing the OpenFeature
 * Provider API on top.
 */
interface FlagsProvider {
    /**
     * Sets the [EvaluationContext] for flag resolution.
     *
     * The context is used to determine which flag values to return based on user targeting
     * rules. This method triggers a background fetch of updated flag evaluations.
     *
     * @param context The [EvaluationContext] containing targeting key and attributes.
     */
    fun setContext(context: EvaluationContext)

    /**
     * Resolves a boolean flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The boolean value of the flag, or the default value if unavailable.
     */
    fun resolveBooleanValue(flagKey: String, defaultValue: Boolean): Boolean

    /**
     * Resolves a string flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The string value of the flag, or the default value if unavailable.
     */
    fun resolveStringValue(flagKey: String, defaultValue: String): String

    /**
     * Resolves a numeric flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The numeric value of the flag, or the default value if unavailable.
     */
    fun resolveNumberValue(flagKey: String, defaultValue: Number): Number

    /**
     * Resolves an integer flag value.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The integer value of the flag, or the default value if unavailable.
     */
    fun resolveIntValue(flagKey: String, defaultValue: Int): Int

    /**
     * Resolves a structured flag value as a JSON object.
     *
     * @param flagKey The unique identifier of the flag to resolve.
     * @param defaultValue The value to return if the flag cannot be retrieved or parsed.
     * @return The JSON object value of the flag, or the default value if unavailable.
     */
    fun resolveStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject
}
