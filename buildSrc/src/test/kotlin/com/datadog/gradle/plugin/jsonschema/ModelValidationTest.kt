/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.datadog.android.core.internal.utils.fromJsonElement
import com.example.forgery.ForgeryConfiguration
import com.example.model.Company
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.assertj.core.api.Assertions.assertThat
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Date

@RunWith(Parameterized::class)
class ModelValidationTest(
    internal val schemaResourcePath: String,
    internal val outputInfo: OutputInfo
) {

    @get:Rule
    val forge = ForgeRule()

    @Before
    fun `set up`() {
        ForgeryConfiguration().configure(forge)
    }

    @Test
    fun `validates model`() {
        val type = Class.forName("com.example.model.${outputInfo.className}")
        val toJson = type.getMethod("toJson")
        val schema = loadSchema(schemaResourcePath)
        val file = javaClass.getResource("/input/").file
        val schemaLoader = SchemaLoader.builder()
            .resolutionScope("file://$file")
            .schemaJson(schema)
            .build()
        val validator = schemaLoader.load().build()

        repeat(10) {
            val entity = forge.getForgery(type)
            val json = toJson.invoke(entity)
            try {
                validator.validate(JSONObject(json.toString()))
            } catch (e: Exception) {
                throw RuntimeException(
                    "Failed to validate $schemaResourcePath (#$it):\n$entity\n$json\n",
                    e
                )
            }
        }
    }

    @Test
    fun `validate model serialization and deserialization`() {
        val type = Class.forName("com.example.model.${outputInfo.className}")
        val toJson = type.getMethod("toJson")
        if (outputInfo.isConstant) {
            // skip this test as is not relevant anymore. We are just testing a constructor.
            return
        }
        val generatorFunction = type.getMethod("fromJson", String::class.java)
        repeat(10) {
            val entity = forge.getForgery(type)
            val json = toJson.invoke(entity).toString()
            val generatedModel = generatorFunction.invoke(null, json)

            assertThat(generatedModel)
                .overridingErrorMessage(
                    "Deserialized model was not the same " +
                        "with the serialized for type: [$type] and test iteration: [$it]\n" +
                        " -  input: $entity \n" +
                        " -   json: $json \n" +
                        " - output: $generatedModel"

                )
                .usingRecursiveComparison()
                .withComparatorForType(numberTypeComparator, Number::class.java)
                .withComparatorForType(mapTypeComparator, Map::class.java)
                .withComparatorForType(informationComparator, Company.Information::class.java)
                .ignoringCollectionOrder()
                .isEqualTo(entity)
        }
    }

    private val numberTypeComparator = Comparator<Number> { t1, t2 ->
        when (t2) {
            is Long -> t2.compareTo(t1.toLong())
            is Double -> t2.compareTo(t1.toDouble())
            is Float -> t2.compareTo(t1.toFloat())
            is Byte -> t2.compareTo(t1.toByte())
            else -> (t2 as Int).compareTo(t1.toInt())
        }
    }

    private val mapTypeComparator = Comparator<Map<*, *>> { t1, t2 ->
        if (t2.size == t1.size) {
            val mismatches = t1.filter { (k, v1) ->
                val v2 = t2[k]
                compareMapValues(v1, v2)
            }
            mismatches.size
        } else {
            1
        }
    }

    private val informationComparator = Comparator<Company.Information> { t1, t2 ->
        if (t1.date != t2.date) {
            -1
        } else if (t1.priority != t2.priority) {
            -2
        } else {
            mapTypeComparator.compare(t1.additionalProperties, t2.additionalProperties)
        }
    }

    private fun compareMapValues(v1: Any?, v2: Any?): Boolean {
        return if (v1 is JsonElement) {
            compareJsonElement(v1, v2)
        } else if (v1 is Map<*, *> && v2 is Map<*, *>) {
            mapTypeComparator.compare(v1, v2) == 0
        } else {
            v2 != v1
        }
    }

    private fun compareJsonElement(
        v1: JsonElement,
        v2: Any?
    ): Boolean {
        return when (v2) {
            null -> v1 != JsonNull.INSTANCE
            is Boolean -> v1.asBoolean != v2
            is Int -> v1.asInt != v2
            is Long -> v1.asLong != v2
            is Float -> v1.asFloat != v2
            is Double -> v1.asDouble != v2
            is String -> v1.asString != v2
            is Date -> v1.asLong != v2.time
            is JsonObject -> v1.asJsonObject.toString() != v2.toString()
            is JsonArray -> v1.asJsonArray != v2
            is Iterable<*> -> v1.asJsonArray.toList() != v2
            is Map<*, *> -> mapTypeComparator.compare(v1.asJsonObject.asDeepMap(), v2) == 0
            else -> v1.asString != v2.toString()
        }
    }

    private fun loadSchema(schemaResName: String): JSONObject {
        return javaClass.getResourceAsStream("/input/$schemaResName.json").use {
            JSONObject(JSONTokener(it))
        }
    }

    internal fun JsonObject.asDeepMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        entrySet().forEach {
            map[it.key] = it.value.fromJsonElement()
        }
        return map
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("arrays", OutputInfo("Article")),
                arrayOf("one_of", OutputInfo("Animal")),
                arrayOf("defaults_with_optionals", OutputInfo("Bike")),
                arrayOf("nested", OutputInfo("Book")),
                arrayOf("additional_props", OutputInfo("Comment")),
                arrayOf("additional_props_any", OutputInfo("Company")),
                arrayOf("definition_name_conflict", OutputInfo("Conflict")),
                arrayOf("definition", OutputInfo("Customer")),
                arrayOf("definition_with_id", OutputInfo("Customer")),
                arrayOf("nested_enum", OutputInfo("DateTime")),
                arrayOf("external_description", OutputInfo("Delivery")),
                arrayOf("types", OutputInfo("Demo")),
                arrayOf("top_level_definition", OutputInfo("Foo")),
                arrayOf("one_of_ref", OutputInfo("Household")),
                arrayOf("enum_number", OutputInfo("Jacket")),
                arrayOf("constant", OutputInfo("Location", true)),
                arrayOf("read_only", OutputInfo("Message")),
                arrayOf("enum_array", OutputInfo("Order")),
                arrayOf("description", OutputInfo("Opus")),
                arrayOf("minimal", OutputInfo("Person")),
                arrayOf("required", OutputInfo("Product")),
                arrayOf("external_nested_description", OutputInfo("Shipping")),
                arrayOf("enum", OutputInfo("Style")),
                arrayOf("all_of", OutputInfo("User")),
                arrayOf("all_of_merged", OutputInfo("UserMerged")),
                arrayOf("constant_number", OutputInfo("Version")),
                arrayOf("sets", OutputInfo("Video")),
                arrayOf("one_of_nested", OutputInfo("WeirdCombo")),
                arrayOf("path_array_with_integer", OutputInfo("PathArrayWithInteger")),
                arrayOf("path_array_with_number", OutputInfo("PathArrayWithNumber"))
            )
        }
    }

    data class OutputInfo(val className: String, val isConstant: Boolean = false)
}
