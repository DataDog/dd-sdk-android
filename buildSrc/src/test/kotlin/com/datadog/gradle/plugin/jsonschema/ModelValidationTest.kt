/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.example.forgery.ForgeryConfiguration
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
        println(">> SCOPE PATH : file://$file")
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
                    "Failed to validate $schemaResourcePath:\n$entity\n$json\n", e
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
        val generatorFunction =
            type.getMethod("fromJson", String::class.java)
        repeat(10) {
            val entity = forge.getForgery(type)
            val json = toJson.invoke(entity).toString()
            val generatedModel =
                generatorFunction.invoke(null, json)

            assertThat(generatedModel)
                .overridingErrorMessage(
                    "Deserialized model was not the same " +
                        "with the serialized for type: [$type] and test iteration: [$it]"
                )
                .usingRecursiveComparison()
                .withComparatorForType(numberTypeComparator, Number::class.java)
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

    private fun loadSchema(schemaResName: String): JSONObject {
        return javaClass.getResourceAsStream("/input/$schemaResName.json").use {
            JSONObject(JSONTokener(it))
        }
    }

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("arrays", OutputInfo("Article")),
                arrayOf("nested", OutputInfo("Book")),
                arrayOf("additional_props", OutputInfo("Comment")),
                arrayOf("definition_name_conflict", OutputInfo("Conflict")),
                arrayOf("definition", OutputInfo("Customer")),
                arrayOf("definition_with_id", OutputInfo("Customer")),
                arrayOf("nested_enum", OutputInfo("DateTime")),
                arrayOf("external_description", OutputInfo("Delivery")),
                arrayOf("types", OutputInfo("Demo")),
                arrayOf("top_level_definition", OutputInfo("Foo")),
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
                arrayOf("defaults_with_optionals", OutputInfo("Bike"))
            )
        }
    }

    data class OutputInfo(val className: String, val isConstant: Boolean = false)
}
