/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import org.gradle.api.logging.Logger

class FileGenerator(
    private val outputDir: File,
    private val packageName: String,
    private val logger: Logger
) {

    private val nestedTypes: MutableSet<TypeDefinition> = mutableSetOf()
    private val knownTypeNames: MutableSet<String> = mutableSetOf()

    private val classGenerator = ClassGenerator(packageName, nestedTypes, knownTypeNames)
    private val enumGenerator = EnumClassGenerator(packageName, nestedTypes, knownTypeNames)
    private val multiClassGenerator = MultiClassGenerator(packageName, nestedTypes, knownTypeNames)

    // region FileGenerator

    /**
     * Generate a Kotlin file based on the input schema file
     */
    fun generate(typeDefinition: TypeDefinition) {
        logger.info("Generating class for type $typeDefinition with package name $packageName")
        knownTypeNames.clear()
        nestedTypes.clear()
        generateFile(typeDefinition)
    }

    // endregion

    // region Internal

    private val isClass: (TypeDefinition) -> Boolean = { type ->
        type is TypeDefinition.Class || type is TypeDefinition.MultiClass
    }
    private val isEnum: (TypeDefinition) -> Boolean = { type ->
        type is TypeDefinition.Enum
    }

    /**
     *  Generate a Kotlin file based on the root schema definition
     */
    private fun generateFile(definition: TypeDefinition) {
        val rootTypeName = when (definition) {
            is TypeDefinition.Class -> definition.name
            is TypeDefinition.MultiClass -> definition.name
            else -> throw IllegalStateException("Top level type $definition is not supported")
        }

        val fileBuilder = FileSpec.builder(packageName, rootTypeName)

        val topLevelTypeBuilder = generateTypeSpec(definition, rootTypeName)

        while (nestedTypes.any(isClass)) {
            val nestedClasses = nestedTypes.filter(isClass).toSet()

            nestedClasses.forEach {
                topLevelTypeBuilder.addType(generateTypeSpec(it, rootTypeName).build())
            }

            nestedTypes.removeAll(nestedClasses)
        }

        while (nestedTypes.any(isEnum)) {
            val nestedEnums = nestedTypes.filter(isEnum).toSet()

            nestedEnums.forEach {
                topLevelTypeBuilder.addType(generateTypeSpec(it, rootTypeName).build())
            }

            nestedTypes.removeAll(nestedEnums)
        }

        fileBuilder.addType(topLevelTypeBuilder.build())
        fileBuilder
            .indent("    ")
            .build()
            .writeTo(outputDir)
    }

    private fun generateTypeSpec(
        definition: TypeDefinition,
        rootTypeName: String
    ): TypeSpec.Builder {
        return when (definition) {
            is TypeDefinition.Class -> classGenerator.generate(definition, rootTypeName)
            is TypeDefinition.Enum -> enumGenerator.generate(definition, rootTypeName)
            is TypeDefinition.MultiClass -> multiClassGenerator.generate(definition, rootTypeName)
            else -> throw IllegalArgumentException("Can't generate a file for type $definition")
        }
    }
}