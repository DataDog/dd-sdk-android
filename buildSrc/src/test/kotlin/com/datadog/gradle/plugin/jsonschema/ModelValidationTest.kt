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
    internal val className: String
) {

    @get:Rule
    val forge = ForgeRule()

    @Before
    fun `set up`() {
        ForgeryConfiguration().configure(forge)
    }

    @Test
    fun `validates model`() {
        val type = Class.forName("com.example.model.$className")
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
        val type = Class.forName("com.example.model.$className")
        val toJson = type.getMethod("toJson")
        val fromJson = type.getMethod("fromJson", String::class.java)
        repeat(10) {
            val entity = forge.getForgery(type)
            val json = toJson.invoke(entity).toString()
            val generatedModel = fromJson.invoke(null, json)
            assertThat(generatedModel)
                .overridingErrorMessage(
                    "Deserialized model was not the same " +
                        "with the serialized for type: [$type] and test iteration: [$it]"
                )
                .isEqualToComparingFieldByField(entity)
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
                arrayOf("arrays", "Article"),
                arrayOf("nested", "Book"),
                arrayOf("additional_props", "Comment"),
                arrayOf("definition_name_conflict", "Conflict"),
                arrayOf("definition", "Customer"),
                arrayOf("definition_with_id", "Customer"),
                arrayOf("nested_enum", "DateTime"),
                arrayOf("external_description", "Delivery"),
                arrayOf("types", "Demo"),
                arrayOf("top_level_definition", "Foo"),
                arrayOf("constant", "Location"),
                arrayOf("read_only", "Message"),
                arrayOf("enum_array", "Order"),
                arrayOf("description", "Opus"),
                arrayOf("minimal", "Person"),
                arrayOf("required", "Product"),
                arrayOf("external_nested_description", "Shipping"),
                arrayOf("enum", "Style"),
                arrayOf("all_of", "User"),
                arrayOf("all_of_merged", "UserMerged"),
                arrayOf("constant_number", "Version"),
                arrayOf("sets", "Video"),
                arrayOf("defaults_with_optionals", "Bike")
            )
        }
    }
}