/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import android.databinding.tool.ext.toCamelCase
import android.databinding.tool.ext.toCamelCaseAsVar
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
import kotlin.IllegalStateException

class PokoGenerator(
    internal val schemaFile: File,
    internal val outputDir: File,
    internal val packageName: String
) {

    private lateinit var rootSchema: Definition
    private lateinit var rootTypeName: String
    private lateinit var rootDefinition: Definition

    private val nestedDefinitions: MutableList<Pair<String, Definition>> = mutableListOf()
    private val nestedEnums: MutableList<Pair<String, List<String>>> = mutableListOf()
    private val identifiedDefinitions: MutableMap<String, String> = mutableMapOf()

    // region PokoGenerator

    fun generate() {
        println("Generating class for schema ${schemaFile.name} with package name $packageName")
        val gson = Gson()
        rootSchema = gson.fromJson(
            schemaFile.inputStream().reader(Charsets.UTF_8),
            Definition::class.java
        )

        rootSchema.definitions?.forEach { (name, definition) ->
            definition.id?.let { identifiedDefinitions[it] = name }
        }

        rootDefinition = rootSchema

        generateFile(outputDir)
    }

    // endregion

    // region Internal

    private fun generateFile(
        outputDir: File
    ) {
        require(rootSchema.type == Type.OBJECT) {
            "Top level schema with type ${rootSchema.type} is not supported."
        }
        rootTypeName = (rootSchema.title ?: schemaFile.nameWithoutExtension).toCamelCase()

        val fileBuilder = FileSpec.builder(packageName, rootTypeName)

        val typeBuilder = generateTopLevelType(rootSchema)

        rootSchema.definitions?.forEach { (name, definition) ->
            if (definition != rootDefinition) {
                typeBuilder.addType(generateType(name.toCamelCase(), definition).build())
            }
        }

        while (nestedDefinitions.isNotEmpty()) {
            val definitions = nestedDefinitions.toList()
            definitions.forEach { (name, definition) ->
                typeBuilder.addType(generateType(name.toCamelCase(), definition).build())
            }
            nestedDefinitions.removeAll(definitions)
        }

        nestedEnums.forEach { (name, values) ->
            typeBuilder.addType(generateEnum(name.toCamelCase(), values))
        }

        fileBuilder
            .addType(typeBuilder.build())
            .indent("    ")
            .build()
            .writeTo(outputDir)
    }

    private fun generateTopLevelType(schema: Definition): TypeSpec.Builder {
        return if (!schema.properties.isNullOrEmpty()) {
            generateType(rootTypeName, schema)
        } else if (!schema.allOf.isNullOrEmpty()) {
            generateTypeAllOf(rootTypeName, schema)
        } else if (!schema.ref.isNullOrBlank()) {
            val refDefinition = getRefDefinition(schema.ref)
            if (refDefinition != null) {
                rootDefinition = refDefinition
                generateType(rootTypeName, refDefinition)
            } else {
                throw IllegalStateException(
                    "Top level definition reference not found: ${schema.ref}."
                )
            }
        } else {
            println("UNSUPPORTED\n$schema")
            TODO()
        }
    }

    private fun getRefDefinition(ref: String): Definition? {
        val definition = REF_DEFINITION_REGEX.matchEntire(ref)
        val id = REF_ID_REGEX.matchEntire(ref)

        return rootSchema.definitions?.entries?.firstOrNull {
            if (definition != null) {
                it.key == definition.groupValues[1]
            } else if (id != null) {
                it.value.id == ref
            } else false
        }?.value
    }

    private fun generateEnum(
        typeName: String,
        values: List<String>
    ): TypeSpec {
        val enumBuilder = TypeSpec.enumBuilder(typeName)

        values.forEach { value ->
            enumBuilder.addEnumConstant(
                value.toUpperCase(Locale.US),
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
    ): TypeSpec.Builder {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addModifiers(KModifier.DATA)
        val docBuilder = CodeBlock.builder()

        appendTypeDefinition(
            definition,
            docBuilder,
            constructorBuilder,
            typeBuilder
        )

        typeBuilder.primaryConstructor(constructorBuilder.build())
            .addKdoc(docBuilder.build())

        return typeBuilder
    }

    private fun generateTypeAllOf(
        typeName: String,
        definition: Definition
    ): TypeSpec.Builder {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(typeName)
            .addModifiers(KModifier.DATA)
        val docBuilder = CodeBlock.builder()

        definition.allOf?.forEach { subDef ->
            if (!subDef.ref.isNullOrBlank()) {
                val subRef = getRefDefinition(subDef.ref)
                if (subRef != null) {
                    appendTypeDefinition(subRef, docBuilder, constructorBuilder, typeBuilder)
                } else {
                    throw IllegalStateException(
                        "AllOf definition reference not found: ${subDef.ref}."
                    )
                }
            } else if (!subDef.properties.isNullOrEmpty()) {
                appendTypeDefinition(subDef, docBuilder, constructorBuilder, typeBuilder)
            }
        }

        typeBuilder.primaryConstructor(constructorBuilder.build())
            .addKdoc(docBuilder.build())

        return typeBuilder
    }

    private fun appendTypeDefinition(
        definition: Definition,
        docBuilder: CodeBlock.Builder,
        constructorBuilder: FunSpec.Builder,
        typeBuilder: TypeSpec.Builder
    ) {
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
    }

    @Suppress("LongParameterList")
    private fun generateProperty(
        name: String,
        required: Boolean,
        propertyDef: Definition,
        constructorBuilder: FunSpec.Builder,
        typeBuilder: TypeSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {
        val varName = name.toCamelCaseAsVar()
        val type = if (required) {
            propertyDef.asKotlinTypeName(name, false)
        } else {
            propertyDef.asKotlinTypeName(name, false).copy(nullable = true)
        }
        constructorBuilder.addParameter(varName, type)
        typeBuilder.addProperty(
            PropertySpec.builder(varName, type)
                .initializer(varName)
                .addAnnotation(name.serializedAnnotation())
                .build()
        )

        if (!propertyDef.description.isNullOrBlank()) {
            docBuilder.add("@param $varName ${propertyDef.description}\n")
        }
    }

    // endregion

    // region Extensions

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
            name.toCamelCase().singular()
        } else {
            name.toCamelCase()
        }

        if (!enum.isNullOrEmpty()) {
            nestedEnums.add(name to enum)
            return ClassName(packageName, rootTypeName, typeName)
        }

        if (!constant.isNullOrBlank()) {
            nestedEnums.add(name to listOf(constant))
            return ClassName(packageName, rootTypeName, typeName)
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
            ClassName(packageName, rootTypeName, definition.groupValues[1].toCamelCase())
        } else if (id != null) {
            val typeName = identifiedDefinitions[this]
            if (typeName.isNullOrBlank()) {
                ANY_NULLABLE
            } else {
                ClassName(packageName, rootTypeName, typeName.toCamelCase())
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
                ClassName(packageName, rootTypeName, typeName)
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
