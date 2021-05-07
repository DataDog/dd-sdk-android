/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.datadog.android.core.internal.utils.toJsonArray
import com.example.forgery.ForgeryConfiguration
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.junit4.ForgeRule
import java.util.Date
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ModelWithAdditionalAttributesTest {

    @get:Rule
    val forge = ForgeRule()

    @Before
    fun `set up`() {
        ForgeryConfiguration().configure(forge)
    }

    @Test
    fun `validate model for additionalProperties with optional Any value type`() {
        val type = Class.forName("com.example.model.Company")
        val toJson = type.getMethod("toJson")
        val fromJson = type.getMethod("fromJson", String::class.java)
        repeat(10) {
            val expectedModel = forge.getForgery(type)
            val json = toJson.invoke(expectedModel).toString()
            val generatedModel = fromJson.invoke(null, json)
            val mapClass = Map::class.java
            assertThat(generatedModel)
                .overridingErrorMessage(
                    "Deserialized model was not the same " +
                        "with the serialized for type: [$type] and test iteration: [$it]"
                )
                .usingRecursiveComparison()
                .withComparatorForType(additionalPropertiesComparator, mapClass)
                .ignoringFields("information")
                .isEqualTo(expectedModel)
            // Assertj does not apply the custom comparator recursively :(
            val expectedInformation: Any? = expectedModel.getFieldValue("information")
            val generatedInformation: Any? = generatedModel.getFieldValue("information")
            if (expectedInformation == null && generatedInformation == null) {
                return
            }
            assertThat(generatedInformation)
                .overridingErrorMessage(
                    "Deserialized information model was not the same " +
                        "with the serialized one at test iteration: [$it]"
                )
                .usingRecursiveComparison()
                .withComparatorForType(additionalPropertiesComparator, mapClass)
                .isEqualTo(expectedInformation)
        }
    }

    private val additionalPropertiesComparator = Comparator<Map<*, *>> { t1, t2 ->
        if (t1.size == t2.size) {
            t1.forEach {
                val expectedValue = t2[it.key]
                val currentValue = it.value
                val isEqual = if (currentValue is JsonElement) {
                    when (expectedValue) {
                        null -> currentValue == JsonNull.INSTANCE
                        is Boolean -> currentValue.asBoolean == expectedValue
                        is Int -> currentValue.asInt == expectedValue
                        is Long -> currentValue.asLong == expectedValue
                        is Float -> currentValue.asFloat == expectedValue
                        is Double -> currentValue.asDouble == expectedValue
                        is String -> currentValue.asString == expectedValue
                        is Date -> currentValue.asLong == expectedValue
                        is JsonObject -> currentValue.asJsonObject.toString() ==
                            expectedValue.toString()
                        is JsonArray -> currentValue.asJsonArray == expectedValue
                        is Iterable<*> -> currentValue.asJsonArray == expectedValue.toJsonArray()
                        else -> currentValue.asString == expectedValue.toString()
                    }
                } else {
                    expectedValue == currentValue
                }
                if (!isEqual) {
                    return@Comparator 1
                }
            }
            0
        } else {
            1
        }
    }

    /**
     * Gets the field value from the target instance.
     * @param fieldName the name of the field
     */
    inline fun <reified T, R : Any> R.getFieldValue(
        fieldName: String,
        enclosingClass: Class<R> = this.javaClass
    ): T {
        val field = enclosingClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(this) as T
    }
}
