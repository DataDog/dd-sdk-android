/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class JsonSchemaReaderTest(
    internal val inputSchema: String,
    internal val expectedName: String,
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
            mapOf(
                "all_of_merged.json" to "UserMerged",
                "additional_props_merged.json" to "AdditionalPropsMerged",
                "additional_props_single_merge.json" to "AdditionalPropsSingleMerge"
            ),
            NoOpLogger()
        )

        val generatedRoot = testedReader.readSchema(File(inputPath))

        val expectedRoot = RootSchema(expectedName, outputType)
        assertThat(generatedRoot)
            .overridingErrorMessage(
                "Expected root schema:\n$expectedRoot\nbut was:\n$generatedRoot"
            )
            .isEqualTo(expectedRoot)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {0}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("arrays", "Article", Article),
                arrayOf("one_of", "Animal", Animal),
                arrayOf("defaults_with_optionals", "Bike", Bike),
                arrayOf("nested", "Book", Book),
                arrayOf("additional_props", "Comment", Comment),
                arrayOf("additional_props_any", "Company", Company),
                arrayOf("additional_props_merged", "AdditionalPropsMerged", AdditionalPropsMerged),
                arrayOf("additional_props_single_merge", "AdditionalPropsSingleMerge", AdditionalPropsSingleMerge),
                arrayOf("definition_name_conflict", "Conflict", Conflict),
                arrayOf("root_schema_with_no_type", "Country", Country),
                arrayOf("definition", "Customer", Customer),
                arrayOf("definition_with_id", "Customer", Customer),
                arrayOf("nested_enum", "DateTime", DateTime),
                arrayOf("external_description", "Delivery", Delivery),
                arrayOf("types", "Demo", Demo),
                arrayOf("external_description_complex_path", "Employee", Employee),
                arrayOf("top_level_definition", "Foo", Foo),
                arrayOf("one_of_ref", "Household", Household),
                arrayOf("enum_number", "Jacket", Jacket),
                arrayOf("constant", "Location", Location),
                arrayOf("read_only", "Message", Message),
                arrayOf("enum_array", "Order", Order),
                arrayOf("description", "Opus", Opus),
                arrayOf("one_of_complex", "Paper", Paper),
                arrayOf("minimal", "Person", Person),
                arrayOf("required", "Product", Product),
                arrayOf("external_nested_description", "Shipping", Shipping),
                arrayOf("external_nested_description_properties", "Shipping", Shipping),
                arrayOf("enum", "Style", Style),
                arrayOf("all_of", "User", User),
                arrayOf("all_of_merged", "UserMerged", UserMerged),
                arrayOf("constant_number", "Version", Version),
                arrayOf("sets", "Video", Video),
                arrayOf("one_of_nested", "WeirdCombo", WeirdCombo),
                arrayOf("required_for_other_all_of", "RequiredForOtherAllOf", RequiredForOtherAllOf),
                arrayOf("path_array_with_integer", "PathArrayWithInteger", PathArrayWithInteger),
                arrayOf("path_array_with_number", "PathArrayWithNumber", PathArrayWithNumber),
                arrayOf("top_level_array", "Tasks", Tasks),
                arrayOf("top_level_unique_array", "TagSet", TagSet)
            )
        }
    }
}
