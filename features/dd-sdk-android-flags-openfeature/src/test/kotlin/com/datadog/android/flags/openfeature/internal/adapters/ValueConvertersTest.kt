/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

import com.datadog.android.api.InternalLogger
import com.datadog.tools.unit.forge.BaseConfigurator
import dev.openfeature.kotlin.sdk.Value
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal

@ExtendWith(ForgeExtension::class, MockitoExtension::class)
@ForgeConfiguration(BaseConfigurator::class)
internal class ValueConvertersTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // region convertToValue

    @Test
    fun `M return Value Null W convertToValue() {null input}`() {
        // When
        val result = convertToValue(null, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(Value.Null)
    }

    @Test
    fun `M convert Map W convertToValue() {Map input}`(@StringForgery key: String, @StringForgery value: String) {
        // Given
        val map = mapOf(key to value)

        // When
        val result = convertToValue(map, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Structure::class.java)
        val structure = checkNotNull(result.asStructure())
        assertThat(structure[key]).isInstanceOf(Value.String::class.java)
        assertThat((structure[key] as Value.String).asString()).isEqualTo(value)
    }

    @Test
    fun `M convert List W convertToValue() {List input}`(forge: Forge) {
        // Given
        val item1 = forge.anAlphabeticalString()
        val item2 = forge.anInt()
        val list = listOf(item1, item2)

        // When
        val result = convertToValue(list, mockInternalLogger)

        // Then
        val resultList = checkNotNull(result.asList())
        assertThat(resultList).hasSize(2)
        assertThat((resultList[0] as Value.String).asString()).isEqualTo(item1)
        assertThat((resultList[1] as Value.Integer).asInteger()).isEqualTo(item2)
    }

    @Test
    fun `M convert JSONObject to String W convertToValue() {JSONObject input}`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        val jsonObject = JSONObject().apply {
            put(key, value)
        }

        // When
        val result = convertToValue(jsonObject, mockInternalLogger)

        // Then - JSONObject is now treated as unexpected type and converted to string
        assertThat(result).isInstanceOf(Value.String::class.java)
        assertThat((result as Value.String).asString()).isEqualTo(jsonObject.toString())
    }

    @Test
    fun `M convert JSONArray to String W convertToValue() {JSONArray input}`(forge: Forge) {
        // Given
        val item1 = forge.anAlphabeticalString()
        val item2 = forge.anInt()
        val jsonArray = JSONArray().apply {
            put(item1)
            put(item2)
        }

        // When
        val result = convertToValue(jsonArray, mockInternalLogger)

        // Then - JSONArray is now treated as unexpected type and converted to string
        assertThat(result).isInstanceOf(Value.String::class.java)
        assertThat((result as Value.String).asString()).isEqualTo(jsonArray.toString())
    }

    // endregion

    // region Primitive Type Conversions

    @Test
    fun `M convert String W convertToValue() {string input}`(@StringForgery stringValue: String) {
        // When
        val result = convertToValue(stringValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.String::class.java)
        assertThat((result as Value.String).asString()).isEqualTo(stringValue)
    }

    @Test
    fun `M convert Boolean W convertToValue() {boolean input}`(@BoolForgery boolValue: Boolean) {
        // When
        val result = convertToValue(boolValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Boolean::class.java)
        assertThat((result as Value.Boolean).asBoolean()).isEqualTo(boolValue)
    }

    @Test
    fun `M convert Int W convertToValue() {int input}`(@IntForgery intValue: Int) {
        // When
        val result = convertToValue(intValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(intValue)
    }

    @Test
    fun `M convert Long to Integer W convertToValue() {long within Int range}`(forge: Forge) {
        // Given
        val longValue = forge.anInt().toLong()

        // When
        val result = convertToValue(longValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(longValue.toInt())
    }

    @Test
    fun `M convert Long to Double W convertToValue() {long exceeds Int MAX_VALUE}`(forge: Forge) {
        // Given
        val longValue = forge.aLong(min = Int.MAX_VALUE.toLong() + 1)

        // When
        val result = convertToValue(longValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(longValue.toDouble())
    }

    @Test
    fun `M convert Long to Double W convertToValue() {long below Int MIN_VALUE}`(forge: Forge) {
        // Given
        val longValue = forge.aLong(max = Int.MIN_VALUE.toLong())

        // When
        val result = convertToValue(longValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(longValue.toDouble())
    }

    @Test
    fun `M convert Long to Integer W convertToValue() {long at Int MIN_VALUE}`() {
        // Given
        val longValue = Int.MIN_VALUE.toLong()

        // When
        val result = convertToValue(longValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(Int.MIN_VALUE)
    }

    @Test
    fun `M convert Long to Integer W convertToValue() {long at Int MAX_VALUE}`() {
        // Given
        val longValue = Int.MAX_VALUE.toLong()

        // When
        val result = convertToValue(longValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `M convert Short W convertToValue() {short input}`(forge: Forge) {
        // Given
        val shortValue = forge.anInt(min = Short.MIN_VALUE.toInt(), max = Short.MAX_VALUE.toInt()).toShort()

        // When
        val result = convertToValue(shortValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(shortValue.toInt())
    }

    @Test
    fun `M convert Byte W convertToValue() {byte input}`(forge: Forge) {
        // Given
        val byteValue = forge.anInt(min = Byte.MIN_VALUE.toInt(), max = Byte.MAX_VALUE.toInt()).toByte()

        // When
        val result = convertToValue(byteValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(byteValue.toInt())
    }

    @Test
    fun `M convert Float W convertToValue() {float input}`(@FloatForgery floatValue: Float) {
        // When
        val result = convertToValue(floatValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(floatValue.toDouble())
    }

    @Test
    fun `M convert Double W convertToValue() {double input}`(@DoubleForgery doubleValue: Double) {
        // When
        val result = convertToValue(doubleValue, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(doubleValue)
    }

    @Test
    fun `M convert Number to Double W convertToValue() {BigDecimal input}`(forge: Forge) {
        // Given
        val bigDecimal = BigDecimal.valueOf(forge.aDouble())

        // When
        val result = convertToValue(bigDecimal, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(bigDecimal.toDouble())
    }

    @Test
    fun `M convert unknown type to String W convertToValue() {arbitrary object}`(forge: Forge) {
        // Given
        val customObject = CustomTestObject(forge.anAlphabeticalString())

        // When
        val result = convertToValue(customObject, mockInternalLogger)

        // Then - Converts to string via toString() and logs warning
        assertThat(result).isInstanceOf(Value.String::class.java)
        assertThat((result as Value.String).asString()).isEqualTo(customObject.toString())
        // Note: Warning logged to Android Log: "Unexpected type CustomTestObject converted to string"
    }

    // endregion

    // region Edge Cases

    @Test
    fun `M handle deeply nested structures W convertToValue() {complex nesting}`(forge: Forge) {
        // Given - Create a deeply nested structure: map -> list -> map -> value
        val deepValue = forge.anAlphabeticalString()
        val level3 = mapOf("deepKey" to deepValue)
        val level2 = listOf(level3)
        val level1 = mapOf("arrayKey" to level2)

        // When
        val result = convertToValue(level1, mockInternalLogger)

        // Then
        val structure = result.asStructure()
        checkNotNull(structure)
        val array = (structure["arrayKey"] as Value.List).asList()
        checkNotNull(array)
        val nestedStructure = (array[0] as Value.Structure).asStructure()
        checkNotNull(nestedStructure)
        assertThat((nestedStructure["deepKey"] as Value.String).asString()).isEqualTo(deepValue)
    }

    @Test
    fun `M handle special number values W convertToValue() {special doubles}`() {
        // When/Then - Infinity
        val infinityResult = convertToValue(Double.POSITIVE_INFINITY, mockInternalLogger)
        assertThat(infinityResult).isInstanceOf(Value.Double::class.java)
        assertThat((infinityResult as Value.Double).asDouble()).isEqualTo(Double.POSITIVE_INFINITY)

        // When/Then - Negative Infinity
        val negInfinityResult = convertToValue(Double.NEGATIVE_INFINITY, mockInternalLogger)
        assertThat(negInfinityResult).isInstanceOf(Value.Double::class.java)
        assertThat((negInfinityResult as Value.Double).asDouble()).isEqualTo(Double.NEGATIVE_INFINITY)

        // When/Then - NaN
        val nanResult = convertToValue(Double.NaN, mockInternalLogger)
        assertThat(nanResult).isInstanceOf(Value.Double::class.java)
        assertThat((nanResult as Value.Double).asDouble()).isNaN()
    }

    @Test
    fun `M handle empty string W convertToValue() {empty string}`() {
        // When
        val result = convertToValue("", mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.String::class.java)
        assertThat((result as Value.String).asString()).isEmpty()
    }

    // endregion

    private data class CustomTestObject(val value: String) {
        override fun toString(): String = "CustomTestObject($value)"
    }
}
