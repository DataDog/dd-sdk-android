/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.internal.model.VariationType
import org.json.JSONObject
import java.util.Locale
import kotlin.reflect.KClass

/**
 * Exception thrown when a flag's type doesn't match the requested type.
 *
 * @param message Description of the type mismatch
 */
internal class TypeMismatchException(message: String) : Exception(message)

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
     * @param targetType The target type class to convert to
     * @return Result containing the converted value on success,
     *         or a Failure with TypeMismatchException if types are incompatible,
     *         or a Failure with the parse exception if conversion failed
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> convert(variationValue: String, variationType: String, targetType: KClass<T>): Result<T> {
        if (!isTypeCompatible(variationType, targetType)) {
            val expectedType = getTypeName(targetType)
            return Result.failure(
                TypeMismatchException("Flag has type '$variationType' but $expectedType was requested")
            )
        }

        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val result: T? = when (targetType) {
                Boolean::class -> variationValue.lowercase(Locale.US).toBooleanStrictOrNull() as? T

                String::class -> variationValue as T

                Int::class -> variationValue.toIntOrNull() as? T

                Double::class -> variationValue.toDoubleOrNull() as? T

                JSONObject::class -> {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: wrapped in runCatching
                    JSONObject(variationValue) as? T
                }

                else -> null
            }

            result ?: throw IllegalArgumentException("Failed to parse value '$variationValue'")
        }
    }

    /**
     * Checks if the variation type from the flag is compatible with the requested type.
     *
     * @param variationType The type specified in the flag metadata (e.g., "boolean", "string", "integer")
     * @param targetType The target type class to check compatibility for
     * @return true if types are compatible, false otherwise
     */
    fun isTypeCompatible(variationType: String, targetType: KClass<*>): Boolean = when (targetType) {
        Boolean::class -> variationType == VariationType.BOOLEAN.value

        String::class -> variationType == VariationType.STRING.value

        Int::class -> variationType == VariationType.INTEGER.value || variationType == VariationType.NUMBER.value

        Double::class ->
            variationType == VariationType.NUMBER.value || variationType == VariationType.FLOAT.value ||
                variationType == VariationType.INTEGER.value

        JSONObject::class -> variationType == VariationType.OBJECT.value

        else -> false
    }

    fun getTypeName(targetType: KClass<*>): String = when (targetType) {
        Boolean::class -> "Boolean"
        String::class -> "String"
        Int::class -> "Int"
        Double::class -> "Double"
        JSONObject::class -> "JSONObject"
        else -> targetType.simpleName ?: "Unknown"
    }
}
