/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
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
        @IntForgery value2: Int
    ) {
        // Given
        val jsonObject = JSONObject().apply {
            put(key1, value1)
            put("number", value2)
            put("bool", true)
            put("double", 3.14)
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result).hasSize(4)
        assertThat(result[key1]).isEqualTo(value1)
        assertThat(result["number"]).isEqualTo(value2)
        assertThat(result["bool"]).isEqualTo(true)
        assertThat(result["double"]).isEqualTo(3.14)
    }

    @Test
    fun `M convert nested object W toMap() {nested JSONObject}`() {
        // Given
        val jsonObject = JSONObject().apply {
            put("name", "Alice")
            put(
                "details",
                JSONObject().apply {
                    put("age", 30)
                    put("city", "NYC")
                }
            )
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result["name"]).isEqualTo("Alice")
        assertThat(result["details"]).isInstanceOf(Map::class.java)
        val details = result["details"] as Map<*, *>
        assertThat(details["age"]).isEqualTo(30)
        assertThat(details["city"]).isEqualTo("NYC")
    }

    @Test
    fun `M convert array W toMap() {JSONArray value}`() {
        // Given
        val jsonObject = JSONObject().apply {
            put(
                "items",
                JSONArray().apply {
                    put("item1")
                    put("item2")
                    put(42)
                }
            )
        }

        // When
        val result = jsonObject.toMap()

        // Then
        assertThat(result["items"]).isInstanceOf(List::class.java)
        val items = result["items"] as List<*>
        assertThat(items).hasSize(3)
        assertThat(items[0]).isEqualTo("item1")
        assertThat(items[1]).isEqualTo("item2")
        assertThat(items[2]).isEqualTo(42)
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
    fun `M handle deeply nested structures W toMap() {complex nesting}`() {
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
                                            put("value", "deep")
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
        assertThat(item["value"]).isEqualTo("deep")
    }

    // endregion

    // region JSONArray.toList()

    @Test
    fun `M convert primitives W toList() {simple array}`() {
        // Given
        val jsonArray = JSONArray().apply {
            put("string")
            put(42)
            put(true)
            put(3.14)
        }

        // When
        val result = jsonArray.toList()

        // Then
        assertThat(result).hasSize(4)
        assertThat(result[0]).isEqualTo("string")
        assertThat(result[1]).isEqualTo(42)
        assertThat(result[2]).isEqualTo(true)
        assertThat(result[3]).isEqualTo(3.14)
    }

    @Test
    fun `M convert nested array W toList() {nested JSONArray}`() {
        // Given
        val jsonArray = JSONArray().apply {
            put("first")
            put(
                JSONArray().apply {
                    put(1)
                    put(2)
                    put(3)
                }
            )
        }

        // When
        val result = jsonArray.toList()

        // Then
        assertThat(result[0]).isEqualTo("first")
        assertThat(result[1]).isInstanceOf(List::class.java)
        val nested = result[1] as List<*>
        assertThat(nested).containsExactly(1, 2, 3)
    }

    @Test
    fun `M convert object in array W toList() {JSONObject in array}`() {
        // Given
        val jsonArray = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("name", "Alice")
                    put("age", 30)
                }
            )
        }

        // When
        val result = jsonArray.toList()

        // Then
        assertThat(result[0]).isInstanceOf(Map::class.java)
        val obj = result[0] as Map<*, *>
        assertThat(obj["name"]).isEqualTo("Alice")
        assertThat(obj["age"]).isEqualTo(30)
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
    fun `M convert primitives W toJSONObject() {simple map}`(@StringForgery key: String, @StringForgery value: String) {
        // Given
        val map = mapOf(
            key to value,
            "number" to 42,
            "bool" to true,
            "double" to 3.14
        )

        // When
        val result = map.toJSONObject()

        // Then
        assertThat(result.getString(key)).isEqualTo(value)
        assertThat(result.getInt("number")).isEqualTo(42)
        assertThat(result.getBoolean("bool")).isTrue()
        assertThat(result.getDouble("double")).isEqualTo(3.14)
    }

    @Test
    fun `M convert nested map W toJSONObject() {nested Map}`() {
        // Given
        val map = mapOf(
            "user" to mapOf(
                "name" to "Alice",
                "age" to 30
            )
        )

        // When
        val result = map.toJSONObject()

        // Then
        val user = result.getJSONObject("user")
        assertThat(user).isNotNull
        assertThat(user.getString("name")).isEqualTo("Alice")
        assertThat(user.getInt("age")).isEqualTo(30)
    }

    @Test
    fun `M convert list W toJSONObject() {List value}`() {
        // Given
        val map = mapOf(
            "items" to listOf("a", "b", "c")
        )

        // When
        val result = map.toJSONObject()

        // Then
        val items = result.getJSONArray("items")
        assertThat(items).isNotNull
        assertThat(items.length()).isEqualTo(3)
        assertThat(items.getString(0)).isEqualTo("a")
        assertThat(items.getString(1)).isEqualTo("b")
        assertThat(items.getString(2)).isEqualTo("c")
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
    fun `M convert primitives W toJSONArray() {simple list}`() {
        // Given
        val list = listOf("string", 42, true, 3.14)

        // When
        val result = list.toJSONArray()

        // Then
        assertThat(result.length()).isEqualTo(4)
        assertThat(result.getString(0)).isEqualTo("string")
        assertThat(result.getInt(1)).isEqualTo(42)
        assertThat(result.getBoolean(2)).isTrue()
        assertThat(result.getDouble(3)).isEqualTo(3.14)
    }

    @Test
    fun `M convert nested list W toJSONArray() {nested List}`() {
        // Given
        val list = listOf(
            "first",
            listOf(1, 2, 3)
        )

        // When
        val result = list.toJSONArray()

        // Then
        assertThat(result.getString(0)).isEqualTo("first")
        val nested = result.getJSONArray(1)
        assertThat(nested.length()).isEqualTo(3)
        assertThat(nested.getInt(0)).isEqualTo(1)
        assertThat(nested.getInt(1)).isEqualTo(2)
        assertThat(nested.getInt(2)).isEqualTo(3)
    }

    @Test
    fun `M convert map W toJSONArray() {Map in list}`() {
        // Given
        val list = listOf(
            mapOf("name" to "Alice", "age" to 30)
        )

        // When
        val result = list.toJSONArray()

        // Then
        val obj = result.getJSONObject(0)
        assertThat(obj.getString("name")).isEqualTo("Alice")
        assertThat(obj.getInt("age")).isEqualTo(30)
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
    fun `M preserve structure W toMap then toJSONObject() {round trip}`(forge: Forge) {
        // Given
        val original = mapOf(
            "string" to forge.anAlphabeticalString(),
            "number" to forge.anInt(),
            "nested" to mapOf(
                "bool" to forge.aBool(),
                "deep" to mapOf(
                    "value" to forge.anInt()
                )
            ),
            "array" to listOf(1, 2, 3)
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
        assertThat(array).containsExactly(1, 2, 3)
    }

    // endregion
}
