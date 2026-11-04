/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.internal.model.VariationType
import org.json.JSONObject
import java.math.BigDecimal
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

    private val INT_MIN = BigDecimal(Int.MIN_VALUE)
    private val INT_MAX = BigDecimal(Int.MAX_VALUE)

    // The longest decimal value that Int reads, and Double reads at or above 2^53, parse with BigDecimal.
    // This bounds the BigDecimal work on each read.
    private const val MAX_DECIMAL_LENGTH = 64

    // 2^53. Below this magnitude, Double holds every whole value exactly.
    private const val EXACT_DOUBLE_LIMIT = 9_007_199_254_740_992.0

    /**
     * Converts a flag's string value to the expected type.
     *
     * @param T The expected type of the flag value
     * @param variationValue The raw string value from the flag
     * @param variationType The type specified in the flag metadata
     * @param targetType The target type class to convert to
     * @return Result containing the converted value on success,
     *         or a Failure with TypeMismatchException if types are incompatible
     *         or a numeric value can't be read as Int without loss,
     *         or a whole value matches neither the Double nor its printed form,
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
                Int::class -> parseLosslessInt(variationValue, variationType) as? T
                Double::class -> parseLosslessDouble(variationValue, variationType) as? T
                Map::class -> variationValue.toMap() as? T
                JSONObject::class -> {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: wrapped in runCatching
                    val json = JSONObject(variationValue)
                    @Suppress("UNCHECKED_CAST")
                    json as? T
                }
                else -> {
                    // Check if targetType is a Map implementation
                    // Safe: isAssignableFrom is a standard Java reflection call
                    @Suppress("UnsafeThirdPartyFunctionCall")
                    if (Map::class.java.isAssignableFrom(targetType.java)) {
                        variationValue.toMap() as? T
                    } else {
                        null
                    }
                }
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
        Int::class, Double::class -> isNumeric(variationType)
        JSONObject::class -> variationType == VariationType.OBJECT.value
        Map::class -> variationType == VariationType.OBJECT.value
        else -> {
            // Check if targetType is a Map implementation (e.g., LinkedHashMap, HashMap, etc.)
            // Safe: isAssignableFrom is a standard Java reflection call
            @Suppress("UnsafeThirdPartyFunctionCall")
            if (Map::class.java.isAssignableFrom(targetType.java)) {
                variationType == VariationType.OBJECT.value
            } else {
                false
            }
        }
    }

    fun getTypeName(targetType: KClass<*>): String = when (targetType) {
        Boolean::class -> "Boolean"
        String::class -> "String"
        Int::class -> "Int"
        Double::class -> "Double"
        JSONObject::class -> "JSONObject"
        Map::class -> "Map"
        else -> targetType.simpleName ?: "Unknown"
    }

    private fun isNumeric(variationType: String): Boolean = variationType == VariationType.INTEGER.value ||
        variationType == VariationType.NUMBER.value ||
        variationType == VariationType.FLOAT.value

    /**
     * Int reads are lossless: a whole value within Int range, such as `5` or `5.0`, reads as Int whatever the
     * numeric type. A fractional or out-of-range value is a type mismatch. A value that can't be parsed returns null,
     * which [convert] reports as a parse error. So does a value that is not a plain Int and is longer than
     * [MAX_DECIMAL_LENGTH] characters.
     */
    @Throws(TypeMismatchException::class)
    private fun parseLosslessInt(variationValue: String, variationType: String): Int? = variationValue.toIntOrNull()
        ?: variationValue.takeIf { it.length <= MAX_DECIMAL_LENGTH }
            ?.toBigDecimalOrNull()
            ?.let { requireLosslessInt(it, variationValue, variationType) }

    // Compare with the Int bounds instead of calling BigDecimal.intValueExact(): on Android 9, intValueExact()
    // expands the power of ten, so a short value such as `1e10000000` takes seconds.
    @Throws(TypeMismatchException::class)
    private fun requireLosslessInt(decimal: BigDecimal, variationValue: String, variationType: String): Int {
        val problem = when {
            isFractional(decimal) -> "fractional value '$variationValue'"
            decimal < INT_MIN || decimal > INT_MAX -> "value '$variationValue' outside Int range"
            else -> null
        }
        if (problem != null) {
            @Suppress("ThrowingInternalException") // Caught by runCatching in convert()
            throw TypeMismatchException("Flag has type '$variationType' with $problem but Int was requested")
        }
        return decimal.toInt()
    }

    /**
     * A whole value reads as Double only when the Double equals it, or when the Double prints as the same value,
     * such as `1e300`. So `9007199254740993` is a type mismatch. A fractional value reads as the nearest Double,
     * such as `1.2`. A value that can't be parsed returns null, which [convert] reports as a parse error. So does
     * a value whose parsed Double is 2^53 or more in magnitude, when the value is longer than [MAX_DECIMAL_LENGTH]
     * characters or [BigDecimal] can't parse it.
     */
    @Throws(TypeMismatchException::class)
    private fun parseLosslessDouble(variationValue: String, variationType: String): Double? =
        variationValue.toDoubleOrNull()?.let { double ->
            if (double > -EXACT_DOUBLE_LIMIT && double < EXACT_DOUBLE_LIMIT) {
                double
            } else {
                variationValue.takeIf { it.length <= MAX_DECIMAL_LENGTH }
                    ?.toBigDecimalOrNull()
                    ?.let { requireLosslessDouble(it, double, variationValue, variationType) }
            }
        }

    @Throws(TypeMismatchException::class)
    private fun requireLosslessDouble(
        decimal: BigDecimal,
        double: Double,
        variationValue: String,
        variationType: String
    ): Double {
        if (!isFractional(decimal) && !isExactDouble(decimal, double)) {
            @Suppress("ThrowingInternalException") // Caught by runCatching in convert()
            throw TypeMismatchException(
                "Flag has type '$variationType' with whole value '$variationValue' that Double can't hold exactly " +
                    "but Double was requested"
            )
        }
        return double
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: BigDecimal(Double) and valueOf() only run for a finite Double
    private fun isExactDouble(decimal: BigDecimal, double: Double): Boolean =
        double != Double.POSITIVE_INFINITY && double != Double.NEGATIVE_INFINITY &&
            (decimal.compareTo(BigDecimal(double)) == 0 || decimal.compareTo(BigDecimal.valueOf(double)) == 0)

    /**
     * A value with a scale of 0 or less is whole. Check the scale first: it also prevents
     * [BigDecimal.stripTrailingZeros] from overflowing the scale for a value such as `100e2147483647`.
     * Zero is whole at any scale: some older Android versions return a zero such as `0.0` unchanged from
     * [BigDecimal.stripTrailingZeros].
     */
    private fun isFractional(decimal: BigDecimal): Boolean {
        if (decimal.scale() <= 0 || decimal.signum() == 0) return false
        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: the scale is positive, so it can't overflow
        val stripped = decimal.stripTrailingZeros()
        return stripped.scale() > 0
    }
}
