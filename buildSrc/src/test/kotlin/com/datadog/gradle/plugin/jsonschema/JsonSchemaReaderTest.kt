/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class JsonSchemaReaderTest(
    internal val inputSchema: String,
    internal val outputType: TypeDefinition
) {

    @get:Rule
    val tempFolderRule = TemporaryFolder()

    lateinit var tempDir: File

    @Test
    fun `reads a Schema file`() {
        tempDir = tempFolderRule.newFolder()
        val clazz = JsonSchemaReaderTest::class.java
        val inputPath = clazz.getResource("/input/$inputSchema.json").file
        val testedReader = JsonSchemaReader(
            mapOf("all_of_merged.json" to "UserMerged"),
            NoOpLogger()
        )

        val generatedType = testedReader.readSchema(File(inputPath))

        assertThat(generatedType)
            .overridingErrorMessage(
                "Expected definition:\n$outputType\nbut was:\n$generatedType"
            )
            .isEqualTo(outputType)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("arrays", Article),
                arrayOf("one_of", Animal),
                arrayOf("defaults_with_optionals", Bike),
                arrayOf("nested", Book),
                arrayOf("additional_props", Comment),
                arrayOf("additional_props_any", Company),
                arrayOf("definition_name_conflict", Conflict),
                arrayOf("definition", Customer),
                arrayOf("definition_with_id", Customer),
                arrayOf("nested_enum", DateTime),
                arrayOf("external_description", Delivery),
                arrayOf("types", Demo),
                arrayOf("external_description_complex_path", Employee),
                arrayOf("top_level_definition", Foo),
                arrayOf("one_of_ref", Household),
                arrayOf("enum_number", Jacket),
                arrayOf("constant", Location),
                arrayOf("read_only", Message),
                arrayOf("enum_array", Order),
                arrayOf("description", Opus),
                arrayOf("minimal", Person),
                arrayOf("required", Product),
                arrayOf("external_nested_description", Shipping),
                arrayOf("enum", Style),
                arrayOf("all_of", User),
                arrayOf("all_of_merged", UserMerged),
                arrayOf("constant_number", Version),
                arrayOf("sets", Video),
                arrayOf("root_schema_with_no_type", Country)
            )
        }
    }
}
