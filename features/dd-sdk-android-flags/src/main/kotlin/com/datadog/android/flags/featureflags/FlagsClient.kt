/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags

import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.featureflags.model.EvaluationDetails
import org.json.JSONObject

/**
 * An interface for defining Flags clients.
 */
@Suppress("TooManyFunctions") // Required by mobile API spec
interface FlagsClient {
    /**
     * Set the evaluation context for the client.
     * @param evaluationContext The evaluation context to use for flag evaluation.
     */
    fun setEvaluationContext(evaluationContext: EvaluationContext)

    /**
     * Get a Boolean flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun getBooleanValue(flagKey: String, defaultValue: Boolean): Boolean

    /**
     * Get a String flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun getStringValue(flagKey: String, defaultValue: String): String

    /**
     * Get a Number flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun getNumberValue(flagKey: String, defaultValue: Number): Number

    /**
     * Get an Int flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun getIntValue(flagKey: String, defaultValue: Int): Int

    /**
     * Get a Structure flag value.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The value of the flag, or the default value if it cannot be retrieved.
     */
    fun getStructureValue(flagKey: String, defaultValue: JSONObject): JSONObject

    /**
     * Get a Boolean flag value with evaluation details.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The evaluation details, including flag value or default value with reason.
     */
    fun getBooleanDetails(flagKey: String, defaultValue: Boolean): EvaluationDetails

    /**
     * Get a String flag value with evaluation details.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The evaluation details, including flag value or default value with reason.
     */
    fun getStringDetails(flagKey: String, defaultValue: String): EvaluationDetails

    /**
     * Get a Number flag value with evaluation details.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The evaluation details, including flag value or default value with reason.
     */
    fun getNumberDetails(flagKey: String, defaultValue: Number): EvaluationDetails

    /**
     * Get an Int flag value with evaluation details.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The evaluation details, including flag value or default value with reason.
     */
    fun getIntDetails(flagKey: String, defaultValue: Int): EvaluationDetails

    /**
     * Get a Structure flag value with evaluation details.
     * @param flagKey The name of the key to query.
     * @param defaultValue The value to return if the key cannot be retrieved.
     * @return The evaluation details, including flag value or default value with reason.
     */
    fun getStructureDetails(flagKey: String, defaultValue: JSONObject): EvaluationDetails
}
