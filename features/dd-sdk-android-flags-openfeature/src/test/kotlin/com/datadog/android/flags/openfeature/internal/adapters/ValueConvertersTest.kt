/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal.adapters

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
import java.math.BigDecimal

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(BaseConfigurator::class)
internal class ValueConvertersTest {

    // region convertToValue

    @Test
    fun `M return Value Null W convertToValue() {null input}`() {
        // When
        val result = convertToValue(null)

        // Then
        assertThat(result).isEqualTo(Value.Null)
    }

    @Test
    fun `M convert JSONObject W convertToValue() {JSONObject input}`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        val jsonObject = JSONObject().apply {
            put(key, value)
        }

        // When
        val result = convertToValue(jsonObject)

        // Then
        assertThat(result).isInstanceOf(Value.Structure::class.java)
        val structure = checkNotNull(result.asStructure())
        assertThat(structure[key]).isInstanceOf(Value.String::class.java)
        assertThat((structure[key] as Value.String).asString()).isEqualTo(value)
    }

    @Test
    fun `M convert JSONArray W convertToValue() {JSONArray input}`(forge: Forge) {
        // Given
        val item1 = forge.anAlphabeticalString()
        val item2 = forge.anInt()
        val jsonArray = JSONArray().apply {
            put(item1)
            put(item2)
        }

        // When
        val result = convertToValue(jsonArray)

        // Then
        val list = checkNotNull(result.asList())
        assertThat(list).hasSize(2)
        assertThat((list[0] as Value.String).asString()).isEqualTo(item1)
        assertThat((list[1] as Value.Integer).asInteger()).isEqualTo(item2)
    }

    // endregion

    // region convertObjectToValue

    @Test
    fun `M convert empty JSONObject W convertObjectToValue() {empty object}`() {
        // Given
        val jsonObject = JSONObject()

        // When
        val result = convertObjectToValue(jsonObject)

        // Then
        assertThat(result).isInstanceOf(Value.Structure::class.java)
        val structure = result.asStructure()
        assertThat(structure).isEmpty()
    }

    @Test
    fun `M convert simple JSONObject W convertObjectToValue() {primitive values}`(
        @StringForgery stringKey: String,
        @StringForgery stringValue: String,
        @IntForgery intValue: Int,
        @BoolForgery boolValue: Boolean,
        @DoubleForgery doubleValue: Double
    ) {
        // Given
        val jsonObject = JSONObject().apply {
            put(stringKey, stringValue)
            put("intKey", intValue)
            put("boolKey", boolValue)
            put("doubleKey", doubleValue)
        }

        // When
        val result = convertObjectToValue(jsonObject)

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
    fun `M convert nested JSONObject W convertObjectToValue() {nested structure}`(
        @StringForgery topKey: String,
        @StringForgery topValue: String,
        @StringForgery nestedKey: String,
        @StringForgery nestedValue: String
    ) {
        // Given
        val nestedObject = JSONObject().apply {
            put(nestedKey, nestedValue)
        }
        val jsonObject = JSONObject().apply {
            put(topKey, topValue)
            put("nested", nestedObject)
        }

        // When
        val result = convertObjectToValue(jsonObject)

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
    fun `M handle JSONArray in object W convertObjectToValue() {array value}`(forge: Forge) {
        // Given
        val arrayKey = forge.anAlphabeticalString()
        val arrayItem = forge.anAlphabeticalString()
        val jsonArray = JSONArray().apply { put(arrayItem) }
        val jsonObject = JSONObject().apply {
            put(arrayKey, jsonArray)
        }

        // When
        val result = convertObjectToValue(jsonObject)

        // Then
        val structure = result.asStructure()
        checkNotNull(structure)
        assertThat(structure[arrayKey]).isInstanceOf(Value.List::class.java)
        val list = (structure[arrayKey] as Value.List).asList()
        checkNotNull(list)
        assertThat(list).hasSize(1)
        assertThat((list[0] as Value.String).asString()).isEqualTo(arrayItem)
    }

    // endregion

    // region convertArrayToValue

    @Test
    fun `M convert empty JSONArray W convertArrayToValue() {empty array}`() {
        // Given
        val jsonArray = JSONArray()

        // When
        val result = convertArrayToValue(jsonArray)

        // Then
        assertThat(result).isInstanceOf(Value.List::class.java)
        assertThat(checkNotNull(result.asList())).isEmpty()
    }

    @Test
    fun `M convert JSONArray with primitives W convertArrayToValue() {mixed types}`(forge: Forge) {
        // Given
        val stringValue = forge.anAlphabeticalString()
        val intValue = forge.anInt()
        val boolValue = forge.aBool()
        val doubleValue = forge.aDouble()
        val jsonArray = JSONArray().apply {
            put(stringValue)
            put(intValue)
            put(boolValue)
            put(doubleValue)
        }

        // When
        val result = convertArrayToValue(jsonArray)

        // Then
        val list = checkNotNull(result.asList())
        assertThat(list).hasSize(4)
        assertThat((list[0] as Value.String).asString()).isEqualTo(stringValue)
        assertThat((list[1] as Value.Integer).asInteger()).isEqualTo(intValue)
        assertThat((list[2] as Value.Boolean).asBoolean()).isEqualTo(boolValue)
        assertThat((list[3] as Value.Double).asDouble()).isEqualTo(doubleValue)
    }

    @Test
    fun `M convert nested arrays W convertArrayToValue() {array of arrays}`(forge: Forge) {
        // Given
        val innerValue = forge.anAlphabeticalString()
        val innerArray = JSONArray().apply { put(innerValue) }
        val outerArray = JSONArray().apply { put(innerArray) }

        // When
        val result = convertArrayToValue(outerArray)

        // Then
        val list = checkNotNull(result.asList())
        assertThat(list).hasSize(1)
        assertThat(list[0]).isInstanceOf(Value.List::class.java)
        val innerList = (list[0] as Value.List).asList()
        checkNotNull(innerList)
        assertThat(innerList).hasSize(1)
        assertThat((innerList[0] as Value.String).asString()).isEqualTo(innerValue)
    }

    @Test
    fun `M convert array with objects W convertArrayToValue() {array of objects}`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        val jsonObject = JSONObject().apply { put(key, value) }
        val jsonArray = JSONArray().apply { put(jsonObject) }

        // When
        val result = convertArrayToValue(jsonArray)

        // Then
        val list = checkNotNull(result.asList())
        assertThat(list).hasSize(1)
        assertThat(list[0]).isInstanceOf(Value.Structure::class.java)
        val structure = (list[0] as Value.Structure).asStructure()
        checkNotNull(structure)
        assertThat((structure[key] as Value.String).asString()).isEqualTo(value)
    }

    @Test
    fun `M convert JSONObject NULL to Value Null W convertArrayToValue() {array with JSONObject NULL}`(forge: Forge) {
        // Given
        val stringValue = forge.anAlphabeticalString()
        val jsonArray = JSONArray().apply {
            put(stringValue)
            put(JSONObject.NULL)
            put(stringValue)
        }

        // When
        val result = convertArrayToValue(jsonArray)

        // Then - JSONObject.NULL is a sentinel object that should be converted to Value.Null
        val list = checkNotNull(result.asList())
        assertThat(list).hasSize(3)
        assertThat(list[0]).isInstanceOf(Value.String::class.java)
        assertThat((list[0] as Value.String).asString()).isEqualTo(stringValue)
        assertThat(list[1]).isEqualTo(Value.Null)
        assertThat(list[2]).isInstanceOf(Value.String::class.java)
        assertThat((list[2] as Value.String).asString()).isEqualTo(stringValue)
    }

    @Test
    fun `M convert JSONObject NULL to Value Null W convertToValue() {direct JSONObject NULL}`() {
        // Given
        val value = JSONObject.NULL

        // When
        val result = convertToValue(value)

        // Then
        assertThat(result).isEqualTo(Value.Null)
    }

    // endregion

    // region Primitive Type Conversions

    @Test
    fun `M convert String W convertToValue() {string input}`(@StringForgery stringValue: String) {
        // When
        val result = convertToValue(stringValue)

        // Then
        assertThat(result).isInstanceOf(Value.String::class.java)
        assertThat((result as Value.String).asString()).isEqualTo(stringValue)
    }

    @Test
    fun `M convert Boolean W convertToValue() {boolean input}`(@BoolForgery boolValue: Boolean) {
        // When
        val result = convertToValue(boolValue)

        // Then
        assertThat(result).isInstanceOf(Value.Boolean::class.java)
        assertThat((result as Value.Boolean).asBoolean()).isEqualTo(boolValue)
    }

    @Test
    fun `M convert Int W convertToValue() {int input}`(@IntForgery intValue: Int) {
        // When
        val result = convertToValue(intValue)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(intValue)
    }

    @Test
    fun `M convert Long to Integer W convertToValue() {long within Int range}`(forge: Forge) {
        // Given
        val longValue = forge.anInt().toLong()

        // When
        val result = convertToValue(longValue)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(longValue.toInt())
    }

    @Test
    fun `M convert Long to Double W convertToValue() {long exceeds Int MAX_VALUE}`(forge: Forge) {
        // Given
        val longValue = forge.aLong(min = Int.MAX_VALUE.toLong() + 1)

        // When
        val result = convertToValue(longValue)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(longValue.toDouble())
    }

    @Test
    fun `M convert Long to Double W convertToValue() {long below Int MIN_VALUE}`(forge: Forge) {
        // Given
        val longValue = forge.aLong(max = Int.MIN_VALUE.toLong())

        // When
        val result = convertToValue(longValue)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(longValue.toDouble())
    }

    @Test
    fun `M convert Long to Integer W convertToValue() {long at Int MIN_VALUE}`() {
        // Given
        val longValue = Int.MIN_VALUE.toLong()

        // When
        val result = convertToValue(longValue)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(Int.MIN_VALUE)
    }

    @Test
    fun `M convert Long to Integer W convertToValue() {long at Int MAX_VALUE}`() {
        // Given
        val longValue = Int.MAX_VALUE.toLong()

        // When
        val result = convertToValue(longValue)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `M convert Short W convertToValue() {short input}`(forge: Forge) {
        // Given
        val shortValue = forge.anInt(min = Short.MIN_VALUE.toInt(), max = Short.MAX_VALUE.toInt()).toShort()

        // When
        val result = convertToValue(shortValue)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(shortValue.toInt())
    }

    @Test
    fun `M convert Byte W convertToValue() {byte input}`(forge: Forge) {
        // Given
        val byteValue = forge.anInt(min = Byte.MIN_VALUE.toInt(), max = Byte.MAX_VALUE.toInt()).toByte()

        // When
        val result = convertToValue(byteValue)

        // Then
        assertThat(result).isInstanceOf(Value.Integer::class.java)
        assertThat((result as Value.Integer).asInteger()).isEqualTo(byteValue.toInt())
    }

    @Test
    fun `M convert Float W convertToValue() {float input}`(@FloatForgery floatValue: Float) {
        // When
        val result = convertToValue(floatValue)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(floatValue.toDouble())
    }

    @Test
    fun `M convert Double W convertToValue() {double input}`(@DoubleForgery doubleValue: Double) {
        // When
        val result = convertToValue(doubleValue)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(doubleValue)
    }

    @Test
    fun `M convert Number to Double W convertToValue() {BigDecimal input}`(forge: Forge) {
        // Given
        val bigDecimal = BigDecimal.valueOf(forge.aDouble())

        // When
        val result = convertToValue(bigDecimal)

        // Then
        assertThat(result).isInstanceOf(Value.Double::class.java)
        assertThat((result as Value.Double).asDouble()).isEqualTo(bigDecimal.toDouble())
    }

    @Test
    fun `M convert unknown type to String W convertToValue() {arbitrary object}`(forge: Forge) {
        // Given
        val customObject = CustomTestObject(forge.anAlphabeticalString())

        // When
        val result = convertToValue(customObject)

        // Then - Converts to string via toString() and logs warning
        assertThat(result).isInstanceOf(Value.String::class.java)
        assertThat((result as Value.String).asString()).isEqualTo(customObject.toString())
        // Note: Warning logged to Android Log: "Unexpected type CustomTestObject converted to string"
    }

    // endregion

    // region Edge Cases

    @Test
    fun `M handle deeply nested structures W convertToValue() {complex nesting}`(forge: Forge) {
        // Given - Create a deeply nested structure: object -> array -> object -> value
        val deepValue = forge.anAlphabeticalString()
        val level3 = JSONObject().apply { put("deepKey", deepValue) }
        val level2 = JSONArray().apply { put(level3) }
        val level1 = JSONObject().apply { put("arrayKey", level2) }

        // When
        val result = convertToValue(level1)

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
        val infinityResult = convertToValue(Double.POSITIVE_INFINITY)
        assertThat(infinityResult).isInstanceOf(Value.Double::class.java)
        assertThat((infinityResult as Value.Double).asDouble()).isEqualTo(Double.POSITIVE_INFINITY)

        // When/Then - Negative Infinity
        val negInfinityResult = convertToValue(Double.NEGATIVE_INFINITY)
        assertThat(negInfinityResult).isInstanceOf(Value.Double::class.java)
        assertThat((negInfinityResult as Value.Double).asDouble()).isEqualTo(Double.NEGATIVE_INFINITY)

        // When/Then - NaN
        val nanResult = convertToValue(Double.NaN)
        assertThat(nanResult).isInstanceOf(Value.Double::class.java)
        assertThat((nanResult as Value.Double).asDouble()).isNaN()
    }

    @Test
    fun `M handle empty string W convertToValue() {empty string}`() {
        // When
        val result = convertToValue("")

        // Then
        assertThat(result).isInstanceOf(Value.String::class.java)
        assertThat((result as Value.String).asString()).isEmpty()
    }

    @Test
    fun `M handle large array W convertArrayToValue() {many elements}`(forge: Forge) {
        // Given
        val jsonArray = JSONArray()
        repeat(100) {
            jsonArray.put(forge.anInt())
        }

        // When
        val result = convertArrayToValue(jsonArray)

        // Then
        val list = checkNotNull(result.asList())
        assertThat(list).hasSize(100)
        list.forEach { value ->
            assertThat(value).isInstanceOf(Value.Integer::class.java)
        }
    }

    @Test
    fun `M handle large object W convertObjectToValue() {many keys}`(forge: Forge) {
        // Given
        val jsonObject = JSONObject()
        val keys = (1..100).map { "key$it" }
        keys.forEach { key ->
            jsonObject.put(key, forge.anInt())
        }

        // When
        val result = convertObjectToValue(jsonObject)

        // Then
        val structure = result.asStructure()
        checkNotNull(structure)
        assertThat(structure).hasSize(100)
        keys.forEach { key ->
            assertThat(structure[key]).isInstanceOf(Value.Integer::class.java)
        }
    }

    // endregion

    // region convertValueToJson

    @Test
    fun `M convert Value Integer to Int W convertValueToJson()`(forge: Forge) {
        // Given
        val intValue = forge.anInt()
        val value = Value.Integer(intValue)

        // When
        val result = convertValueToJson(value)

        // Then
        assertThat(result).isEqualTo(intValue)
    }

    @Test
    fun `M convert Value Double to Double W convertValueToJson()`(forge: Forge) {
        // Given
        val doubleValue = forge.aDouble()
        val value = Value.Double(doubleValue)

        // When
        val result = convertValueToJson(value)

        // Then
        assertThat(result).isEqualTo(doubleValue)
    }

    @Test
    fun `M convert Value Boolean to Boolean W convertValueToJson()`(forge: Forge) {
        // Given
        val boolValue = forge.aBool()
        val value = Value.Boolean(boolValue)

        // When
        val result = convertValueToJson(value)

        // Then
        assertThat(result).isEqualTo(boolValue)
    }

    @Test
    fun `M convert Value String to String W convertValueToJson()`(forge: Forge) {
        // Given
        val stringValue = forge.anAlphabeticalString()
        val value = Value.String(stringValue)

        // When
        val result = convertValueToJson(value)

        // Then
        assertThat(result).isEqualTo(stringValue)
    }

    @Test
    fun `M convert Value Null to null W convertValueToJson()`() {
        // When
        val result = convertValueToJson(Value.Null)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M convert Value Structure to JSONObject W convertValueToJson() {preserves types}`(forge: Forge) {
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
        val result = convertValueToJson(value)

        // Then
        assertThat(result).isInstanceOf(JSONObject::class.java)
        val jsonObject = result as JSONObject
        assertThat(jsonObject.get("intField")).isEqualTo(intValue)
        assertThat(jsonObject.get("stringField")).isEqualTo(stringValue)
        assertThat(jsonObject.get("boolField")).isEqualTo(boolValue)
    }

    @Test
    fun `M convert Value List to JSONArray W convertValueToJson() {preserves types}`(forge: Forge) {
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
        val result = convertValueToJson(value)

        // Then
        assertThat(result).isInstanceOf(JSONArray::class.java)
        val jsonArray = result as JSONArray
        assertThat(jsonArray.length()).isEqualTo(2)
        assertThat(jsonArray.get(0)).isEqualTo(intValue)
        assertThat(jsonArray.get(1)).isEqualTo(stringValue)
    }

    // endregion

    private data class CustomTestObject(val value: String) {
        override fun toString(): String = "CustomTestObject($value)"
    }
}
