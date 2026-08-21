/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.logging.Logger
import java.io.File

class FileGenerator(
    private val outputDir: File,
    private val packageName: String,
    private val logger: Logger
) {

    private val knownTypes: MutableSet<KotlinTypeWrapper> = mutableSetOf()

    private val classGenerator = ClassGenerator(packageName, knownTypes)
    private val enumGenerator = EnumClassGenerator(packageName, knownTypes)
    private val oneOfPrimitiveOptionGenerator = OneOfPrimitiveOptionGenerator(packageName)

    private val multiClassGenerator = MultiClassGenerator(
        classGenerator = classGenerator,
        oneOfPrimitiveOptionGenerator = oneOfPrimitiveOptionGenerator,
        packageName = packageName,
        knownTypes = knownTypes
    )

    // region FileGenerator

    /**
     * Generate a Kotlin file based on the input schema file
     */
    fun generate(typeDefinition: TypeDefinition) {
        logger.info("Generating class for type $typeDefinition with package name $packageName")
        knownTypes.clear()
        generateFile(typeDefinition)
    }

    // endregion

    // region Internal

    private val isUnwrittenClass: (KotlinTypeWrapper) -> Boolean = { k ->
        (k.type is TypeDefinition.Class || k.type is TypeDefinition.OneOfClass) &&
            !k.written
    }
    private val isEnum: (KotlinTypeWrapper) -> Boolean = { k ->
        (k.type is TypeDefinition.Enum) && !k.written
    }

    /**
     *  Generate a Kotlin file based on the root schema definition
     */
    private fun generateFile(definition: TypeDefinition) {
        val rootTypeName = when (definition) {
            is TypeDefinition.Class -> definition.name
            is TypeDefinition.OneOfClass -> definition.name
            else -> error("Top level type $definition is not supported")
        }

        val fileBuilder = FileSpec.builder(packageName, rootTypeName)

        knownTypes.add(
            KotlinTypeWrapper(
                rootTypeName,
                ClassName(packageName, rootTypeName),
                definition
            ).apply { written = true }
        )
        val topLevelTypeBuilder = generateTypeSpec(definition, rootTypeName)

        while (knownTypes.any(isUnwrittenClass)) {
            val nestedClasses = knownTypes.filter(isUnwrittenClass).toSet()
            nestedClasses.forEach {
                topLevelTypeBuilder.addType(generateTypeSpec(it.type, rootTypeName).build())
                it.written = true
            }
        }

        while (knownTypes.any(isEnum)) {
            val nestedEnums = knownTypes.filter(isEnum).toSet()
            nestedEnums.forEach {
                topLevelTypeBuilder.addType(generateTypeSpec(it.type, rootTypeName).build())
                it.written = true
            }
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
            is TypeDefinition.OneOfClass -> multiClassGenerator.generate(definition, rootTypeName)
            else -> throw IllegalArgumentException("Can't generate a file for type $definition")
        }
    }
}
