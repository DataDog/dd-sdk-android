/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal

import org.json.JSONObject
import java.util.Locale

/**
 * Handles type conversion for feature flag values.
 *
 * This object provides stateless type conversion utilities. It encapsulates all type conversion logic:
 * - Type compatibility checking
 * - String to typed value conversion
 * - JSON object parsing
 */
internal object FlagValueConverter {

    /**
     * Converts a flag's string value to the expected type.
     *
     * @param T The expected type of the flag value
     * @param variationValue The raw string value from the flag
     * @param variationType The type specified in the flag metadata
     * @param defaultValue The default value (used to infer the target type)
     * @return The converted value, or null if types are incompatible or conversion fails
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> convert(variationValue: String, variationType: String, defaultValue: T): T? {
        // First check if types are compatible
        if (!isTypeCompatible(variationType, defaultValue)) {
            return null
        }

        // Get the appropriate converter and apply it
        val converter = getConverterForType(defaultValue)
        return try {
            converter(variationValue)
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            e: Exception
        ) {
            // Catch all conversion exceptions (including JSONException) and return null
            // The caller is responsible for logging errors as appropriate
            null
        }
    }

    /**
     * Checks if the variation type from the flag is compatible with the requested type.
     *
     * @param T The expected type
     * @param variationType The type specified in the flag metadata (e.g., "boolean", "string", "integer")
     * @param defaultValue The default value (used to determine the expected type)
     * @return true if types are compatible, false otherwise
     */
    fun <T> isTypeCompatible(variationType: String, defaultValue: T): Boolean = when (defaultValue) {
        is Boolean -> variationType == "boolean"
        is String -> variationType == "string"
        is Int -> variationType == "integer"
        is Double -> variationType == "number" || variationType == "float"
        is JSONObject -> variationType == "object"
        else -> false
    }

    /**
     * Gets the appropriate converter function for the given type.
     * Handles special cases like JSONObject parsing with error logging.
     *
     * @param T The expected type
     * @param defaultValue The default value (used to determine the target type)
     * @return A function that converts a string to type T, or null if conversion fails
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> getConverterForType(defaultValue: T): (String) -> T? = when (defaultValue) {
        is Boolean -> { s: String -> s.lowercase(Locale.US).toBooleanStrictOrNull() as? T }
        is String -> { s: String -> s as? T }
        is Int -> { s: String -> s.toIntOrNull() as? T }
        is Double -> { s: String -> s.toDoubleOrNull() as? T }
        is JSONObject -> { s: String -> JSONObject(s) as? T }
        else -> { _ -> null }
    }

    /**
     * Gets a human-readable name for the given type.
     *
     * @param T The type to get the name for
     * @param defaultValue The default value (used to determine the type)
     * @return The type name as a string (e.g., "Boolean", "String", "Int")
     */
    fun <T> getTypeName(defaultValue: T): String = when (defaultValue) {
        is Boolean -> "Boolean"
        is String -> "String"
        is Int -> "Int"
        is Double -> "Double"
        is JSONObject -> "JSONObject"
        else -> defaultValue!!::class.simpleName ?: "Unknown"
    }
}
