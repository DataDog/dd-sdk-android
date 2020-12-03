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
        val testedReader = JsonSchemaReader(mapOf("all_of_merged.json" to "UserMerged"))

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
                arrayOf("minimal", Person),
                arrayOf("required", Product),
                arrayOf("nested", Book),
                arrayOf("arrays", Article),
                arrayOf("sets", Video),
                arrayOf("definition", Customer),
                arrayOf("definition_with_id", Customer),
                arrayOf("enum", Style),
                arrayOf("constant", Location),
                arrayOf("constant_number", Version),
                arrayOf("nested_enum", DateTime),
                arrayOf("description", Opus),
                arrayOf("top_level_definition", Foo),
                arrayOf("types", Demo),
                arrayOf("all_of", User),
                arrayOf("all_of_merged", UserMerged),
                arrayOf("external_description", Delivery),
                arrayOf("external_nested_description", Shipping),
                arrayOf("definition_name_conflict", Conflict),
                arrayOf("read_only", Message)
            )
        }
    }
}
