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

    // region convertMapToValue

    @Test
    fun `M convert empty Map W convertMapToValue(, mockInternalLogger) {empty map}`() {
        // Given
        val map = emptyMap<String, Any>()

        // When
        val result = convertMapToValue(map, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Structure::class.java)
        val structure = result.asStructure()
        assertThat(structure).isEmpty()
    }

    @Test
    fun `M convert simple Map W convertMapToValue(, mockInternalLogger) {primitive values}`(
        @StringForgery stringKey: String,
        @StringForgery stringValue: String,
        @IntForgery intValue: Int,
        @BoolForgery boolValue: Boolean,
        @DoubleForgery doubleValue: Double
    ) {
        // Given
        val map = mapOf(
            stringKey to stringValue,
            "intKey" to intValue,
            "boolKey" to boolValue,
            "doubleKey" to doubleValue
        )

        // When
        val result = convertMapToValue(map, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Structure::class.java)
        val structure = result.asStructure()
        assertThat(structure).hasSize(4)
        checkNotNull(structure)
        assertThat((structure[stringKey] as Value.String).asString()).isEqualTo(stringValue)
        assertThat((structure["intKey"] as Value.Integer).asInteger()).isEqualTo(intValue)
        assertThat((structure["boolKey"] as Value.Boolean).asBoolean()).isEqualTo(boolValue)
        assertThat((structure["doubleKey"] as Value.Double).asDouble()).isEqualTo(doubleValue)
    }

    @Test
    fun `M convert nested Map W convertMapToValue(, mockInternalLogger) {nested structure}`(
        @StringForgery topKey: String,
        @StringForgery topValue: String,
        @StringForgery nestedKey: String,
        @StringForgery nestedValue: String
    ) {
        // Given
        val nestedMap = mapOf(nestedKey to nestedValue)
        val map = mapOf(
            topKey to topValue,
            "nested" to nestedMap
        )

        // When
        val result = convertMapToValue(map, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.Structure::class.java)
        val structure = result.asStructure()
        checkNotNull(structure)
        assertThat(structure[topKey]).isInstanceOf(Value.String::class.java)
        assertThat(structure["nested"]).isInstanceOf(Value.Structure::class.java)
        val nested = (structure["nested"] as Value.Structure).asStructure()
        checkNotNull(nested)
        assertThat((nested[nestedKey] as Value.String).asString()).isEqualTo(nestedValue)
    }

    @Test
    fun `M handle List in map W convertMapToValue(, mockInternalLogger) {list value}`(forge: Forge) {
        // Given
        val listKey = forge.anAlphabeticalString()
        val listItem = forge.anAlphabeticalString()
        val list = listOf(listItem)
        val map = mapOf(listKey to list)

        // When
        val result = convertMapToValue(map, mockInternalLogger)

        // Then
        val structure = result.asStructure()
        checkNotNull(structure)
        assertThat(structure[listKey]).isInstanceOf(Value.List::class.java)
        val resultList = (structure[listKey] as Value.List).asList()
        checkNotNull(resultList)
        assertThat(resultList).hasSize(1)
        assertThat((resultList[0] as Value.String).asString()).isEqualTo(listItem)
    }

    // endregion

    // region convertListToValue

    @Test
    fun `M convert empty List W convertListToValue(, mockInternalLogger) {empty list}`() {
        // Given
        val list = emptyList<Any>()

        // When
        val result = convertListToValue(list, mockInternalLogger)

        // Then
        assertThat(result).isInstanceOf(Value.List::class.java)
        assertThat(checkNotNull(result.asList())).isEmpty()
    }

    @Test
    fun `M convert List with primitives W convertListToValue(, mockInternalLogger) {mixed types}`(forge: Forge) {
        // Given
        val stringValue = forge.anAlphabeticalString()
        val intValue = forge.anInt()
        val boolValue = forge.aBool()
        val doubleValue = forge.aDouble()
        val list = listOf(stringValue, intValue, boolValue, doubleValue)

        // When
        val result = convertListToValue(list, mockInternalLogger)

        // Then
        val resultList = checkNotNull(result.asList())
        assertThat(resultList).hasSize(4)
        assertThat((resultList[0] as Value.String).asString()).isEqualTo(stringValue)
        assertThat((resultList[1] as Value.Integer).asInteger()).isEqualTo(intValue)
        assertThat((resultList[2] as Value.Boolean).asBoolean()).isEqualTo(boolValue)
        assertThat((resultList[3] as Value.Double).asDouble()).isEqualTo(doubleValue)
    }

    @Test
    fun `M convert nested lists W convertListToValue(, mockInternalLogger) {list of lists}`(forge: Forge) {
        // Given
        val innerValue = forge.anAlphabeticalString()
        val innerList = listOf(innerValue)
        val outerList = listOf(innerList)

        // When
        val result = convertListToValue(outerList, mockInternalLogger)

        // Then
        val resultList = checkNotNull(result.asList())
        assertThat(resultList).hasSize(1)
        assertThat(resultList[0]).isInstanceOf(Value.List::class.java)
        val innerResultList = (resultList[0] as Value.List).asList()
        checkNotNull(innerResultList)
        assertThat(innerResultList).hasSize(1)
        assertThat((innerResultList[0] as Value.String).asString()).isEqualTo(innerValue)
    }

    @Test
    fun `M convert list with maps W convertListToValue(, mockInternalLogger) {list of maps}`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        val map = mapOf(key to value)
        val list = listOf(map)

        // When
        val result = convertListToValue(list, mockInternalLogger)

        // Then
        val resultList = checkNotNull(result.asList())
        assertThat(resultList).hasSize(1)
        assertThat(resultList[0]).isInstanceOf(Value.Structure::class.java)
        val structure = (resultList[0] as Value.Structure).asStructure()
        checkNotNull(structure)
        assertThat((structure[key] as Value.String).asString()).isEqualTo(value)
    }

    @Test
    fun `M convert null to Value Null W convertListToValue(, mockInternalLogger) {list with null}`(forge: Forge) {
        // Given
        val stringValue = forge.anAlphabeticalString()
        val list = listOf(stringValue, null, stringValue)

        // When
        val result = convertListToValue(list, mockInternalLogger)

        // Then
        val resultList = checkNotNull(result.asList())
        assertThat(resultList).hasSize(3)
        assertThat(resultList[0]).isInstanceOf(Value.String::class.java)
        assertThat((resultList[0] as Value.String).asString()).isEqualTo(stringValue)
        assertThat(resultList[1]).isEqualTo(Value.Null)
        assertThat(resultList[2]).isInstanceOf(Value.String::class.java)
        assertThat((resultList[2] as Value.String).asString()).isEqualTo(stringValue)
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

    @Test
    fun `M handle large list W convertListToValue(, mockInternalLogger) {many elements}`(forge: Forge) {
        // Given
        val list = (1..100).map { forge.anInt() }

        // When
        val result = convertListToValue(list, mockInternalLogger)

        // Then
        val resultList = checkNotNull(result.asList())
        assertThat(resultList).hasSize(100)
        resultList.forEach { value ->
            assertThat(value).isInstanceOf(Value.Integer::class.java)
        }
    }

    @Test
    fun `M handle large map W convertMapToValue(, mockInternalLogger) {many keys}`(forge: Forge) {
        // Given
        val keys = (1..100).map { "key$it" }
        val map = keys.associateWith { forge.anInt() }

        // When
        val result = convertMapToValue(map, mockInternalLogger)

        // Then
        val structure = result.asStructure()
        checkNotNull(structure)
        assertThat(structure).hasSize(100)
        keys.forEach { key ->
            assertThat(structure[key]).isInstanceOf(Value.Integer::class.java)
        }
    }

    // endregion

    // region convertValueToMap

    @Test
    fun `M convert Value Integer to Int W convertValueToMap()`(forge: Forge) {
        // Given
        val intValue = forge.anInt()
        val value = Value.Integer(intValue)

        // When
        val result = convertValueToMap(value)

        // Then
        assertThat(result).isEqualTo(intValue)
    }

    @Test
    fun `M convert Value Double to Double W convertValueToMap()`(forge: Forge) {
        // Given
        val doubleValue = forge.aDouble()
        val value = Value.Double(doubleValue)

        // When
        val result = convertValueToMap(value)

        // Then
        assertThat(result).isEqualTo(doubleValue)
    }

    @Test
    fun `M convert Value Boolean to Boolean W convertValueToMap()`(forge: Forge) {
        // Given
        val boolValue = forge.aBool()
        val value = Value.Boolean(boolValue)

        // When
        val result = convertValueToMap(value)

        // Then
        assertThat(result).isEqualTo(boolValue)
    }

    @Test
    fun `M convert Value String to String W convertValueToMap()`(forge: Forge) {
        // Given
        val stringValue = forge.anAlphabeticalString()
        val value = Value.String(stringValue)

        // When
        val result = convertValueToMap(value)

        // Then
        assertThat(result).isEqualTo(stringValue)
    }

    @Test
    fun `M convert Value Null to null W convertValueToMap()`() {
        // When
        val result = convertValueToMap(Value.Null)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M convert Value Structure to Map W convertValueToMap() {preserves types}`(forge: Forge) {
        // Given
        val intValue = forge.anInt()
        val stringValue = forge.anAlphabeticalString()
        val boolValue = forge.aBool()
        val value = Value.Structure(
            mapOf(
                "intField" to Value.Integer(intValue),
                "stringField" to Value.String(stringValue),
                "boolField" to Value.Boolean(boolValue)
            )
        )

        // When
        val result = convertValueToMap(value)

        // Then
        assertThat(result).isInstanceOf(Map::class.java)
        val map = result as Map<*, *>
        assertThat(map["intField"]).isEqualTo(intValue)
        assertThat(map["stringField"]).isEqualTo(stringValue)
        assertThat(map["boolField"]).isEqualTo(boolValue)
    }

    @Test
    fun `M convert Value List to List W convertValueToMap() {preserves types}`(forge: Forge) {
        // Given
        val intValue = forge.anInt()
        val stringValue = forge.anAlphabeticalString()
        val value = Value.List(
            listOf(
                Value.Integer(intValue),
                Value.String(stringValue)
            )
        )

        // When
        val result = convertValueToMap(value)

        // Then
        assertThat(result).isInstanceOf(List::class.java)
        val list = result as List<*>
        assertThat(list.size).isEqualTo(2)
        assertThat(list[0]).isEqualTo(intValue)
        assertThat(list[1]).isEqualTo(stringValue)
    }

    // endregion

    private data class CustomTestObject(val value: String) {
        override fun toString(): String = "CustomTestObject($value)"
    }
}
