/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import android.databinding.tool.ext.joinToCamelCaseAsVar
import com.google.gson.annotations.SerializedName
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import java.util.Locale

class PokoGenerator(
    internal val outputDir: File,
    internal val packageName: String
) {

    private lateinit var rootTypeName: String
    private val knownTypes: MutableList<String> = mutableListOf()

    private val nestedClasses: MutableSet<TypeDefinition.Class> = mutableSetOf()
    private val nestedEnums: MutableSet<TypeDefinition.Enum> = mutableSetOf()

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
            .addModifiers(KModifier.INTERNAL)

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

        typeBuilder.primaryConstructor(constructorBuilder.build())
            .addKdoc(docBuilder.build())

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
        val docBuilder = CodeBlock.builder()

        if (definition.description.isNotBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }

        definition.values.forEach { value ->
            enumBuilder.addEnumConstant(
                value.toUpperCase(Locale.US),
                TypeSpec.anonymousClassBuilder()
                    .addAnnotation(value.serializedAnnotation())
                    .build()
            )
        }

        return enumBuilder
            .addKdoc(docBuilder.build())
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
        val type = property.type.asKotlinTypeName().copy(nullable = property.nullable)

        val constructorParamBuilder = ParameterSpec.builder(varName, type)
        if (property.nullable) {
            constructorParamBuilder.defaultValue("null")
        }
        constructorBuilder.addParameter(constructorParamBuilder.build())

        typeBuilder.addProperty(
            PropertySpec.builder(varName, type)
                .initializer(varName)
                .addAnnotation(property.name.serializedAnnotation())
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

        propertyBuilder.addAnnotation(name.serializedAnnotation())

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
    }

    // endregion

    // region Extensions

    private fun String.variableName(): String {
        val split = this.split("_").filter { it.isNotBlank() }
        if (split.isEmpty()) return ""
        if (split.size == 1) return split[0]
        return split.joinToCamelCaseAsVar()
    }

    private fun String.uniqueClassName(): String {
        var uniqueName = this
        var tries = 0
        while (uniqueName in knownTypes) {
            tries++
            uniqueName = "${this}$tries"
        }
        knownTypes.add(uniqueName)
        return uniqueName
    }

    private fun TypeDefinition.Enum.withUniqueTypeName(): TypeDefinition.Enum {
        val matchingEnum = nestedEnums.firstOrNull { it.values == values }
        return matchingEnum ?: copy(name = name.uniqueClassName())
    }

    private fun TypeDefinition.Class.withUniqueTypeName(): TypeDefinition.Class {
        val matchingClass = nestedClasses.firstOrNull { it.properties == properties }
        return matchingClass ?: copy(name = name.uniqueClassName())
    }

    private fun TypeDefinition.asKotlinTypeName(): TypeName {
        return when (this) {
            is TypeDefinition.Null -> ANY_NULLABLE
            is TypeDefinition.Primitive -> type.asKotlinTypeName()
            is TypeDefinition.Constant -> type.asKotlinTypeName()
            is TypeDefinition.Class -> {
                val def = withUniqueTypeName()
                nestedClasses.add(def)
                ClassName(packageName, rootTypeName, def.name)
            }
            is TypeDefinition.Array -> {
                if (uniqueItems) {
                    SET.parameterizedBy(items.asKotlinTypeName())
                } else {
                    LIST.parameterizedBy(items.asKotlinTypeName())
                }
            }
            is TypeDefinition.Enum -> {
                val def = withUniqueTypeName()
                nestedEnums.add(def)
                ClassName(packageName, rootTypeName, def.name)
            }
        }
    }

    private fun JsonType?.asKotlinTypeName(): TypeName {
        return when (this) {
            JsonType.NULL -> ANY_NULLABLE
            JsonType.BOOLEAN -> BOOLEAN
            JsonType.NUMBER -> DOUBLE
            JsonType.STRING -> STRING
            JsonType.INTEGER -> LONG
            else -> {
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

        private val ANY_NULLABLE = ANY.copy(nullable = true)
    }
}
