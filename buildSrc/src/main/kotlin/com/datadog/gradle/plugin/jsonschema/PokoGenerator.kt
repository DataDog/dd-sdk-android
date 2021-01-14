/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import java.io.File

class PokoGenerator(
    internal val outputDir: File,
    internal val packageName: String
) {

    private lateinit var rootTypeName: String
    private val knownTypes: MutableList<String> = mutableListOf()
    private val nestedClasses: MutableSet<TypeDefinition.Class> = mutableSetOf()

    private val nestedEnums: MutableSet<TypeDefinition.Enum> = mutableSetOf()
    private val deserializerGenerator =
        PokoDeserializerGenerator(packageName, knownTypes, nestedClasses, nestedEnums)
    private val serializerGenerator = PokoSerializerGenerator()

    // region PokoGenerator

    /**
     * Generate a POKO file based on the input schema file
     */
    fun generate(typeDefinition: TypeDefinition) {
        println("Generating class for type $typeDefinition with package name $packageName")
        knownTypes.clear()
        nestedClasses.clear()
        nestedEnums.clear()
        generateFile(typeDefinition)
    }

    // endregion

    // region Code Generation

    /**
     *  Generate a POKO file based on the root schema definition
     */
    private fun generateFile(definition: TypeDefinition) {
        check(definition is TypeDefinition.Class)

        rootTypeName = definition.name
        val fileBuilder = FileSpec.builder(packageName, definition.name)
        val typeBuilder = generateClass(definition)

        while (nestedClasses.isNotEmpty()) {
            val definitions = nestedClasses.toList()
            definitions.forEach {
                typeBuilder.addType(generateClass(it).build())
            }
            nestedClasses.removeAll(definitions)
        }

        nestedEnums.forEach {
            typeBuilder.addType(generateEnumClass(it))
        }

        fileBuilder
            .addType(typeBuilder.build())
            .indent("    ")
            .build()
            .writeTo(outputDir)
    }

    /**
     * Generates the `class` [TypeSpec.Builder] for the given definition.
     * @param definition the definition of the type
     */
    private fun generateClass(
        definition: TypeDefinition.Class
    ): TypeSpec.Builder {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(definition.name)
        val docBuilder = CodeBlock.builder()

        appendTypeDefinition(
            definition,
            typeBuilder,
            constructorBuilder,
            docBuilder
        )
        val companion = TypeSpec.companionObjectBuilder()
            .addFunction(deserializerGenerator.generateClassDeserializer(definition, rootTypeName))
            .build()

        typeBuilder.primaryConstructor(constructorBuilder.build())
            .addKdoc(docBuilder.build())
            .addType(companion)

        return typeBuilder
    }

    /**
     * Generates the `enum class` [TypeSpec.Builder] for the given definition.
     * @param definition the enum class definition
     */
    private fun generateEnumClass(
        definition: TypeDefinition.Enum
    ): TypeSpec {
        val enumBuilder = TypeSpec.enumBuilder(definition.name)
        enumBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ENUM_CONSTRUCTOR_JSON_VALUE_NAME, String::class)
                .build()
        )
        val docBuilder = CodeBlock.builder()

        if (definition.description.isNotBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }
        enumBuilder.addProperty(
            PropertySpec.builder(ENUM_CONSTRUCTOR_JSON_VALUE_NAME, String::class, KModifier.PRIVATE)
                .initializer(ENUM_CONSTRUCTOR_JSON_VALUE_NAME)
                .build()
        )

        definition.values.forEach { value ->
            enumBuilder.addEnumConstant(
                value.enumConstantName(),
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%S", value)
                    .build()
            )
        }

        val companion = TypeSpec.companionObjectBuilder()
            .addFunction(deserializerGenerator.generateEnumDeserializer(definition, rootTypeName))
            .build()
        enumBuilder.addFunction(serializerGenerator.generateEnumSerializer(definition))

        return enumBuilder
            .addKdoc(docBuilder.build())
            .addType(companion)
            .build()
    }

    /**
     * Appends a property to a [TypeSpec.Builder].
     * @param property the property definition
     * @param typeBuilder the `data class` [TypeSpec] builder.
     * @param constructorBuilder the `data class` constructor builder.
     * @param docBuilder the `data class` KDoc builder.
     */
    private fun appendProperty(
        property: TypeProperty,
        typeBuilder: TypeSpec.Builder,
        constructorBuilder: FunSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {
        val varName = property.name.variableName()
        val nullable = property.optional || property.type is TypeDefinition.Null
        val type = property.type.asKotlinTypeName(
            nestedEnums,
            nestedClasses,
            knownTypes,
            packageName,
            rootTypeName
        ).copy(nullable = nullable)

        val constructorParamBuilder = ParameterSpec.builder(varName, type)
        if (nullable) {
            constructorParamBuilder.defaultValue("null")
        }
        constructorBuilder.addParameter(constructorParamBuilder.build())

        typeBuilder.addProperty(
            PropertySpec.builder(varName, type)
                .mutable(!property.readOnly)
                .initializer(varName)
                .build()
        )

        if (property.type.description.isNotBlank()) {
            docBuilder.add("@param $varName ${property.type.description}\n")
        }
    }

    /**
     * Appends a property to a [TypeSpec.Builder], with a constant default value.
     * @param name the property json name
     * @param definition the property definition
     * @param typeBuilder the `data class` [TypeSpec] builder.
     */
    private fun appendConstant(
        name: String,
        definition: TypeDefinition.Constant,
        typeBuilder: TypeSpec.Builder
    ) {
        val varName = name.variableName()
        val constantValue = definition.value
        val propertyBuilder = if (constantValue is String) {
            PropertySpec.builder(varName, STRING)
                .initializer("\"$constantValue\"")
        } else if (constantValue is Double && definition.type == JsonType.INTEGER) {
            PropertySpec.builder(varName, LONG)
                .initializer("${constantValue.toLong()}L")
        } else if (constantValue is Double) {
            PropertySpec.builder(varName, DOUBLE)
                .initializer("$constantValue")
        } else if (constantValue is Boolean) {
            PropertySpec.builder(varName, BOOLEAN)
                .initializer("$constantValue")
        } else {
            throw IllegalStateException("Unable to generate constant type $definition")
        }

        if (definition.description.isNotBlank()) {
            propertyBuilder.addKdoc(definition.description)
        }

        typeBuilder.addProperty(propertyBuilder.build())
    }

    /**
     * Appends all properties to a [TypeSpec.Builder] from the given definition.
     * @param definition the definition to use.
     * @param typeBuilder the `data class` [TypeSpec] builder.
     * @param constructorBuilder the `data class` constructor builder.
     * @param docBuilder the `data class` KDoc builder.
     */
    private fun appendTypeDefinition(
        definition: TypeDefinition.Class,
        typeBuilder: TypeSpec.Builder,
        constructorBuilder: FunSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {
        if (definition.description.isNotBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }

        var nonConstants = 0

        definition.properties.forEach { p ->
            if (p.type is TypeDefinition.Constant) {
                appendConstant(p.name, p.type, typeBuilder)
            } else {
                nonConstants++
                appendProperty(
                    p,
                    typeBuilder,
                    constructorBuilder,
                    docBuilder
                )
            }
        }

        if (nonConstants > 0) {
            typeBuilder.addModifiers(KModifier.DATA)
        }

        typeBuilder.addFunction(serializerGenerator.generateClassSerializer(definition))
    }

    // endregion

    companion object {
        const val ENUM_CONSTRUCTOR_JSON_VALUE_NAME = "jsonValue"
    }
}
