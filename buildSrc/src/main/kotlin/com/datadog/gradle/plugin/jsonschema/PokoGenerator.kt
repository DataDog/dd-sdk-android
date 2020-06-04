/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import java.util.Locale

class PokoGenerator(
    internal val schemaFile: File,
    internal val outputDir: File,
    internal val packageName: String
) {

    private val nestedDefinitions: MutableList<Pair<String, Definition>> = mutableListOf()
    private val nestedEnums: MutableList<Pair<String, List<String>>> = mutableListOf()
    private val identifiedDefinitions: MutableMap<String, String> = mutableMapOf()

    // region PokoGenerator

    fun generate() {
        println("Generating class for schema ${schemaFile.name} with package name $packageName")
        val gson = Gson()
        val schema = gson.fromJson(
            schemaFile.inputStream().reader(Charsets.UTF_8),
            Definition::class.java
        )

        schema.definitions?.forEach { (name, definition) ->
            definition.id?.let { identifiedDefinitions[it] = name }
        }

        generateFile(outputDir, schema)
    }

    // endregion

    // region Internal

    private fun generateFile(
        outputDir: File,
        schema: Definition
    ) {
        val className = (schema.title ?: schemaFile.nameWithoutExtension).asClassName()

        val fileBuilder = FileSpec.builder(packageName, className)

        if (schema.type == Type.OBJECT && !schema.properties.isNullOrEmpty()) {
            fileBuilder.addType(generateType(className, schema))
        }

        schema.definitions?.forEach { (name, definition) ->
            fileBuilder.addType(generateType(name.asClassName(), definition))
        }

        while (nestedDefinitions.isNotEmpty()) {
            val definitions = nestedDefinitions.toList()
            definitions.forEach { (name, definition) ->
                fileBuilder.addType(generateType(name.asClassName(), definition))
            }
            nestedDefinitions.removeAll(definitions)
        }

        nestedEnums.forEach { (name, values) ->
            fileBuilder.addType(generateEnum(name.asClassName(), values))
        }

        fileBuilder.indent("    ")
            .build()
            .writeTo(outputDir)
    }

    private fun generateEnum(
        typeName: String,
        values: List<String>
    ): TypeSpec {
        val enumBuilder = TypeSpec.enumBuilder(typeName)

        values.forEach { value ->
            enumBuilder.addEnumConstant(
                value.asEnumValue(),
                TypeSpec.anonymousClassBuilder()
                    .addAnnotation(value.serializedAnnotation())
                    .build()
            )
        }

        return enumBuilder.build()
    }

    private fun generateType(
        typeName: String,
        definition: Definition
    ): TypeSpec {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addModifiers(KModifier.DATA)
        val docBuilder = CodeBlock.builder()

        if (!definition.description.isNullOrBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }

        definition.properties?.forEach { (name, property) ->
            val required = (definition.required != null) && (name in definition.required)
            generateProperty(
                name, required, property,
                constructorBuilder,
                typeBuilder,
                docBuilder
            )
        }

        typeBuilder.primaryConstructor(constructorBuilder.build())
            .addKdoc(docBuilder.build())

        return typeBuilder.build()
    }

    @Suppress("LongParameterList")
    private fun generateProperty(
        name: String,
        required: Boolean,
        property: Definition,
        constructorBuilder: FunSpec.Builder,
        typeBuilder: TypeSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {
        val type = if (required) {
            property.asKotlinTypeName(name, false)
        } else {
            property.asKotlinTypeName(name, false).copy(nullable = true)
        }
        constructorBuilder.addParameter(name, type)
        typeBuilder.addProperty(
            PropertySpec.builder(name, type)
                .initializer(name)
                .addAnnotation(name.serializedAnnotation())
                .build()
        )

        if (!property.description.isNullOrBlank()) {
            docBuilder.add("@param $name ${property.description}\n")
        }
    }

    // endregion

    // region Extensions

    private fun String.asClassName(): String {
        return capitalize()
    }

    private fun String.singular(): String {
        return if (endsWith("ies")) {
            substring(0, length - 3)
        } else if (endsWith("s")) {
            substring(0, length - 1)
        } else {
            this
        }
    }

    private fun Definition.asKotlinTypeName(
        name: String,
        withinCollection: Boolean
    ): TypeName {
        val typeName = if (withinCollection) {
            name.asClassName().singular()
        } else {
            name.asClassName()
        }

        if (!enum.isNullOrEmpty()) {
            nestedEnums.add(name to enum)
            return ClassName(packageName, typeName)
        }

        if (!constant.isNullOrBlank()) {
            nestedEnums.add(name to listOf(constant))
            return ClassName(packageName, typeName)
        }

        return if (ref.isNullOrBlank()) {
            type.asKotlinType(typeName, this)
        } else {
            ref.asKotlinTypeName()
        }
    }

    private fun String.asKotlinTypeName(): TypeName {
        val definition = REF_DEFINITION_REGEX.matchEntire(this)
        val id = REF_ID_REGEX.matchEntire(this)
        return if (definition != null) {
            ClassName(packageName, definition.groupValues[1].asClassName())
        } else if (id != null) {
            val typeName = identifiedDefinitions[this]
            if (typeName.isNullOrBlank()) {
                ANY_NULLABLE
            } else {
                ClassName(packageName, typeName.asClassName())
            }
        } else {
            ANY_NULLABLE
        }
    }

    private fun Type?.asKotlinType(typeName: String, definition: Definition): TypeName {
        return when (this) {
            Type.NULL -> ANY_NULLABLE
            Type.BOOLEAN -> BOOLEAN
            Type.OBJECT -> {
                nestedDefinitions.add(typeName to definition)
                ClassName(packageName, typeName)
            }
            Type.ARRAY -> {
                if (definition.uniqueItems == true) {
                    SET.parameterizedBy(
                        definition.items?.asKotlinTypeName(typeName, true) ?: STAR
                    )
                } else {
                    LIST.parameterizedBy(
                        definition.items?.asKotlinTypeName(typeName, true) ?: STAR
                    )
                }
            }
            Type.NUMBER -> DOUBLE
            Type.STRING -> STRING
            Type.INTEGER -> INT
            null -> {
                ANY_NULLABLE
            }
        }
    }

    private fun String.asEnumValue(): String {
        return toUpperCase(Locale.US)
    }

    private fun String.serializedAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(SerializedName::class.java)
            .addMember("%S", this)
            .build()
    }

    // endregion

    companion object {

        private val REF_DEFINITION_REGEX = Regex("#/definitions/([\\w]+)")
        private val REF_ID_REGEX = Regex("#[\\w]+")

        private val ANY_NULLABLE = ANY.copy(nullable = true)
    }
}
