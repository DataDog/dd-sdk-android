/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.example.forgery.ForgeryConfiguration
import com.google.gson.Gson
import fr.xgouchet.elmyr.junit4.ForgeRule
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
        val gson = Gson()
        val type = Class.forName("com.example.model.$className")
        val schema = loadSchema(schemaResourcePath)
        val validator = SchemaLoader.load(schema)

        repeat(10) {
            val entity = forge.getForgery(type)
            val json = gson.toJson(entity, type)
            println("Validating $schemaResourcePath:\n$entity\n$json\n")
            validator.validate(JSONObject(json))
        }
    }

    private fun loadSchema(schemaResName: String): JSONObject {
        return javaClass.getResourceAsStream("/input/$schemaResName.json").use {
            JSONObject(JSONTokener(it))
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("minimal", "Person"),
                arrayOf("required", "Product"),
                arrayOf("nested", "Book"),
                arrayOf("arrays", "Article"),
                arrayOf("sets", "Video"),
                arrayOf("definition", "Customer"),
                arrayOf("definition_with_id", "Customer"),
                arrayOf("enum", "Style"),
                arrayOf("constant", "Location"),
                arrayOf("nested_enum", "DateTime"),
                arrayOf("description", "Opus"),
                arrayOf("top_level_definition", "Foo"),
                arrayOf("types", "Demo"),
                arrayOf("all_of", "User")
            )
        }
    }
}
