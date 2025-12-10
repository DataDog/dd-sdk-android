/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.DoubleForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(BaseConfigurator::class)
internal class JsonExtensionsTest {

    // region JSONObject.toMap()

    @Test
    fun `M convert primitives W toMap() {simple object}`(
        @StringForgery key1: String,
        @StringForgery value1: String,
        @IntForgery value2: Int,
        @BoolForgery boolVal: Boolean,
        @DoubleForgery doubleVal: Double
    ) {
        // Given
        val jsonObject = JSONObject().apply {
            put(key1, value1)
            put("number", value2)
            put("bool", boolVal)
            put("double", doubleVal)    
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).hasSize(4)
        assertThat(result[key1]).isEqualTo(value1)
        assertThat(result["number"]).isEqualTo(value2)
        assertThat(result["bool"]).isEqualTo(boolVal)
        assertThat(result["double"]).isEqualTo(doubleVal)   
    }

    @Test
    fun `M convert nested object W toMap() {nested JSONObject}`(
        @StringForgery fakeName: String,
        @IntForgery fakeAge: Int,
        @StringForgery fakeCity: String
    ) {
        // Given
        val jsonObject = JSONObject().apply {
            put("name", fakeName)
            put(
                "details",
                JSONObject().apply {
                    put("age", fakeAge)
                    put("city", fakeCity)
                }
            )
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result["name"]).isEqualTo(fakeName)
        assertThat(result["details"]).isInstanceOf(Map::class.java)
        val details = result["details"] as Map<*, *>
        assertThat(details["age"]).isEqualTo(fakeAge)
        assertThat(details["city"]).isEqualTo(fakeCity)
    }

    @Test
    fun `M convert array W toMap() {JSONArray value}`(
        @StringForgery fakeItem1: String,
        @StringForgery fakeItem2: String,
        @IntForgery fakeItem3: Int
    ) {
        // Given
        val jsonObject = JSONObject().apply {
            put(
                "items",
                JSONArray().apply {
                    put(fakeItem1)
                    put(fakeItem2)
                    put(fakeItem3)
                }
            )
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result["items"]).isInstanceOf(List::class.java)
        val items = result["items"] as List<*>
        assertThat(items).hasSize(3)
        assertThat(items[0]).isEqualTo(fakeItem1)
        assertThat(items[1]).isEqualTo(fakeItem2)
        assertThat(items[2]).isEqualTo(fakeItem3)
    }

    @Test
    fun `M convert null W toMap() {JSONObject NULL}`() {
        // Given
        val jsonObject = JSONObject().apply {
            put("nullValue", JSONObject.NULL)
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result["nullValue"]).isNull()
    }

    @Test
    fun `M handle deeply nested structures W toMap() {complex nesting}`(
        @StringForgery fakeValue: String
    ) {
        // Given
        val jsonObject = JSONObject().apply {
            put(
                "level1",
                JSONObject().apply {
                    put(
                        "level2",
                        JSONObject().apply {
                            put(
                                "level3",
                                JSONArray().apply {
                                    put(
                                        JSONObject().apply {
                                            put("value", fakeValue)
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }

        // When
        val result = jsonObject.toMap()

        // Then
        val level1 = result["level1"] as Map<*, *>
        val level2 = level1["level2"] as Map<*, *>
        val level3 = level2["level3"] as List<*>
        val item = level3[0] as Map<*, *>
        assertThat(item["value"]).isEqualTo(fakeValue)
    }

    // endregion

    // region JSONArray.toList()

    @Test
    fun `M convert primitives W toList() {simple array}`(
        @StringForgery fakeString: String,
        @IntForgery fakeInt: Int,
        @BoolForgery fakeBool: Boolean,
        @DoubleForgery fakeDouble: Double
    ) {
        // Given
        val jsonArray = JSONArray().apply {
            put(fakeString)
            put(fakeInt)
            put(fakeBool)
            put(fakeDouble)
        }

        // When
        val result = jsonArray.toList()

        // Then
        assertThat(result).hasSize(4)
        assertThat(result[0]).isEqualTo(fakeString)
        assertThat(result[1]).isEqualTo(fakeInt)
        assertThat(result[2]).isEqualTo(fakeBool)
        assertThat(result[3]).isEqualTo(fakeDouble)
    }

    @Test
    fun `M convert nested array W toList() {nested JSONArray}`(
        @StringForgery fakeFirst: String,
        @IntForgery fakeInt1: Int,
        @IntForgery fakeInt2: Int,
        @IntForgery fakeInt3: Int
    ) {
        // Given
        val jsonArray = JSONArray().apply {
            put(fakeFirst)
            put(
                JSONArray().apply {
                    put(fakeInt1)
                    put(fakeInt2)
                    put(fakeInt3)
                }
            )
        }

        // When
        val result = jsonArray.toList()

        // Then
        assertThat(result[0]).isEqualTo(fakeFirst)
        assertThat(result[1]).isInstanceOf(List::class.java)
        val nested = result[1] as List<*>
        assertThat(nested).containsExactly(fakeInt1, fakeInt2, fakeInt3)
    }

    @Test
    fun `M convert object in array W toList() {JSONObject in array}`(
        @StringForgery fakeName: String,
        @IntForgery fakeAge: Int
    ) {
        // Given
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("name", fakeName)
                    put("age", fakeAge)
                }
            )
        }

        // When
        val result = jsonArray.toList()

        // Then
        assertThat(result[0]).isInstanceOf(Map::class.java)
        val obj = result[0] as Map<*, *>
        assertThat(obj["name"]).isEqualTo(fakeName)
        assertThat(obj["age"]).isEqualTo(fakeAge)
    }

    @Test
    fun `M convert null W toList() {JSONObject NULL}`() {
        // Given
        val jsonArray = JSONArray().apply {
            put(JSONObject.NULL)
        }

        // When
        val result = jsonArray.toList()

        // Then
        assertThat(result[0]).isNull()
    }

    // endregion

    // region Map.toJSONObject()

    @Test
    fun `M convert primitives W toJSONObject() {simple map}`(
        @StringForgery key: String,
        @StringForgery value: String,
        @IntForgery fakeInt: Int,
        @BoolForgery fakeBool: Boolean,
        @DoubleForgery fakeDouble: Double
    ) {
        // Given
        val map = mapOf(
            key to value,
            "number" to fakeInt,
            "bool" to fakeBool,
            "double" to fakeDouble
        )

        // When
        val result = map.toJSONObject()

        // Then
        assertThat(result.getString(key)).isEqualTo(value)
        assertThat(result.getInt("number")).isEqualTo(fakeInt)
        assertThat(result.getBoolean("bool")).isEqualTo(fakeBool)
        assertThat(result.getDouble("double")).isEqualTo(fakeDouble)
    }

    @Test
    fun `M convert nested map W toJSONObject() {nested Map}`(
        @StringForgery fakeName: String,
        @IntForgery fakeAge: Int
    ) {
        // Given
        val map = mapOf(
            "user" to mapOf(
                "name" to fakeName,
                "age" to fakeAge
            )
        )

        // When
        val result = map.toJSONObject()

        // Then
        val user = result.getJSONObject("user")
        assertThat(user).isNotNull
        assertThat(user.getString("name")).isEqualTo(fakeName)
        assertThat(user.getInt("age")).isEqualTo(fakeAge)
    }

    @Test
    fun `M convert list W toJSONObject() {List value}`(
        @StringForgery fakeItem1: String,
        @StringForgery fakeItem2: String,
        @StringForgery fakeItem3: String
    ) {
        // Given
        val map = mapOf(
            "items" to listOf(fakeItem1, fakeItem2, fakeItem3)
        )

        // When
        val result = map.toJSONObject()

        // Then
        val items = result.getJSONArray("items")
        assertThat(items).isNotNull
        assertThat(items.length()).isEqualTo(3)
        assertThat(items.getString(0)).isEqualTo(fakeItem1)
        assertThat(items.getString(1)).isEqualTo(fakeItem2)
        assertThat(items.getString(2)).isEqualTo(fakeItem3)
    }

    @Test
    fun `M convert null W toJSONObject() {null value}`() {
        // Given
        val map = mapOf<String, Any?>("nullValue" to null)

        // When
        val result = map.toJSONObject()

        // Then
        assertThat(result.isNull("nullValue")).isTrue()
    }

    // endregion

    // region List.toJSONArray()

    @Test
    fun `M convert primitives W toJSONArray() {simple list}`(
        @StringForgery fakeString: String,
        @IntForgery fakeInt: Int,
        @BoolForgery fakeBool: Boolean,
        @DoubleForgery fakeDouble: Double
    ) {
        // Given
        val list = listOf(fakeString, fakeInt, fakeBool, fakeDouble)

        // When
        val result = list.toJSONArray()

        // Then
        assertThat(result.length()).isEqualTo(4)
        assertThat(result.getString(0)).isEqualTo(fakeString)
        assertThat(result.getInt(1)).isEqualTo(fakeInt)
        assertThat(result.getBoolean(2)).isEqualTo(fakeBool)
        assertThat(result.getDouble(3)).isEqualTo(fakeDouble)
    }

    @Test
    fun `M convert nested list W toJSONArray() {nested List}`(
        @StringForgery fakeFirst: String,
        @IntForgery fakeInt1: Int,
        @IntForgery fakeInt2: Int,
        @IntForgery fakeInt3: Int
    ) {
        // Given
        val list = listOf(
            fakeFirst,
            listOf(fakeInt1, fakeInt2, fakeInt3)
        )

        // When
        val result = list.toJSONArray()

        // Then
        assertThat(result.getString(0)).isEqualTo(fakeFirst)
        val nested = result.getJSONArray(1)
        assertThat(nested.length()).isEqualTo(3)
        assertThat(nested.getInt(0)).isEqualTo(fakeInt1)
        assertThat(nested.getInt(1)).isEqualTo(fakeInt2)
        assertThat(nested.getInt(2)).isEqualTo(fakeInt3)
    }

    @Test
    fun `M convert map W toJSONArray() {Map in list}`(
        @StringForgery fakeName: String,
        @IntForgery fakeAge: Int
    ) {
        // Given
        val list = listOf(
            mapOf("name" to fakeName, "age" to fakeAge)
        )

        // When
        val result = list.toJSONArray()

        // Then
        val obj = result.getJSONObject(0)
        assertThat(obj.getString("name")).isEqualTo(fakeName)
        assertThat(obj.getInt("age")).isEqualTo(fakeAge)
    }

    @Test
    fun `M preserve nulls W toJSONArray then toList() {null in list}`() {
        // Given
        val list = listOf("a", null, "b", null)

        // When
        val jsonArray = list.toJSONArray()
        val result = jsonArray.toList()

        // Then
        assertThat(result).containsExactly("a", null, "b", null)
    }

    // endregion

    // region Round-trip conversion

    @Test
    fun `M preserve structure W toMap then toJSONObject() {round trip}`(
        @StringForgery fakeString: String,
        @IntForgery fakeNumber: Int,
        @BoolForgery fakeBool: Boolean,
        @IntForgery fakeDeepValue: Int,
        @IntForgery fakeArrayItem1: Int,
        @IntForgery fakeArrayItem2: Int,
        @IntForgery fakeArrayItem3: Int
    ) {
        // Given
        val original = mapOf(
            "string" to fakeString,
            "number" to fakeNumber,
            "nested" to mapOf(
                "bool" to fakeBool,
                "deep" to mapOf(
                    "value" to fakeDeepValue
                )
            ),
            "array" to listOf(fakeArrayItem1, fakeArrayItem2, fakeArrayItem3)
        )

        // When - round trip: Map → JSON → Map
        val json = original.toJSONObject()
        val result = json.toMap()

        // Then - structure preserved
        assertThat(result["string"]).isEqualTo(original["string"])
        assertThat(result["number"]).isEqualTo(original["number"])

        val nested = result["nested"] as Map<*, *>
        val originalNested = original["nested"] as Map<*, *>
        assertThat(nested["bool"]).isEqualTo(originalNested["bool"])

        val deep = nested["deep"] as Map<*, *>
        val originalDeep = originalNested["deep"] as Map<*, *>
        assertThat(deep["value"]).isEqualTo(originalDeep["value"])

        val array = result["array"] as List<*>
        assertThat(array).containsExactly(fakeArrayItem1, fakeArrayItem2, fakeArrayItem3)
    }

    // endregion
}
