/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import android.databinding.tool.ext.joinToCamelCaseAsVar
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.NOTHING
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
                    .build()
            )
        }

        enumBuilder.addFunction(generateEnumSerializer(definition))

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
        val nullable = property.optional || property.type is TypeDefinition.Null
        val type = property.type.asKotlinTypeName()
            .copy(nullable = nullable)

        val constructorParamBuilder = ParameterSpec.builder(varName, type)
        if (nullable) {
            constructorParamBuilder.defaultValue("null")
        }
        constructorBuilder.addParameter(constructorParamBuilder.build())

        typeBuilder.addProperty(
            PropertySpec.builder(varName, type)
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

        typeBuilder.addFunction(generateClassSerializer(definition))
    }

    // endregion

    // region Serialization

    /**
     * Generates a function serializing the type to Json
     * @param definition the class definition
     */
    private fun generateClassSerializer(definition: TypeDefinition.Class): FunSpec {
        val funBuilder = FunSpec.builder(TO_JSON)
            .returns(JSON_ELEMENT)

        funBuilder.addStatement("val json = %T()", JSON_OBJECT)

        definition.properties.forEach { p ->
            appendPropertySerialization(p, funBuilder)
        }

        funBuilder.addStatement("return json")

        return funBuilder.build()
    }

    /**
     * Generates a function serializing the type to Json
     * @param definition the enum class definition
     */
    private fun generateEnumSerializer(definition: TypeDefinition.Enum): FunSpec {
        val funBuilder = FunSpec.builder(TO_JSON)
            .returns(JSON_ELEMENT)

        funBuilder.beginControlFlow("return when (this)")
        definition.values.forEach { value ->
            funBuilder.addStatement(
                "%L -> %T(%S)",
                value.toUpperCase(Locale.US),
                JSON_PRIMITIVE,
                value
            )
        }
        funBuilder.endControlFlow()

        return funBuilder.build()
    }

    /**
     * Appends a property serialization to a [FunSpec.Builder].
     * @param property the property definition
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendPropertySerialization(
        property: TypeProperty,
        funBuilder: FunSpec.Builder
    ) {

        val varName = property.name.variableName()
        when (property.type) {
            is TypeDefinition.Constant -> appendConstantSerialization(
                property.name,
                property.type,
                funBuilder
            )
            is TypeDefinition.Primitive -> appendPrimitiveSerialization(
                property,
                property.type,
                varName,
                funBuilder
            )
            is TypeDefinition.Null -> if (!property.optional) {
                funBuilder.addStatement("json.add(%S, null)", property.name)
            }
            is TypeDefinition.Array -> appendArraySerialization(
                property,
                property.type,
                varName,
                funBuilder
            )
            is TypeDefinition.Class,
            is TypeDefinition.Enum -> appendObjectSerialization(property, varName, funBuilder)
        }
    }

    private fun appendObjectSerialization(
        property: TypeProperty,
        varName: String,
        funBuilder: FunSpec.Builder
    ) {
        if (property.optional) {
            funBuilder.addStatement(
                "if (%L != null) json.add(%S, %L.%L())",
                varName,
                property.name,
                varName,
                TO_JSON
            )
        } else {
            funBuilder.addStatement(
                "json.add(%S, %L.%L())",
                property.name,
                varName, TO_JSON
            )
        }
    }

    private fun appendArraySerialization(
        property: TypeProperty,
        type: TypeDefinition.Array,
        varName: String,
        funBuilder: FunSpec.Builder
    ) {
        if (property.optional) {
            funBuilder.beginControlFlow("if (%L != null)", varName)
        }

        funBuilder.addStatement("val %LArray = %T(%L.size)", varName, JSON_ARRAY, varName)
        when (type.items) {
            is TypeDefinition.Primitive -> funBuilder.addStatement(
                "%L.forEach { %LArray.add(it) }",
                varName,
                varName
            )
            is TypeDefinition.Class,
            is TypeDefinition.Enum -> funBuilder.addStatement(
                "%L.forEach { %LArray.add(it.%L()) }",
                varName,
                varName,
                TO_JSON
            )
        }

        funBuilder.addStatement("json.add(%S, %LArray)", property.name, varName)

        if (property.optional) {
            funBuilder.endControlFlow()
        }
    }

    /**
     * Appends a primitive property serialization to a [FunSpec.Builder].
     * @param property the property definition
     * @param type the primitive type
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    @Suppress("NON_EXHAUSTIVE_WHEN")
    private fun appendPrimitiveSerialization(
        property: TypeProperty,
        type: TypeDefinition.Primitive,
        varName: String,
        funBuilder: FunSpec.Builder
    ) {
        when (type.type) {
            JsonType.BOOLEAN,
            JsonType.NUMBER,
            JsonType.STRING,
            JsonType.INTEGER ->
                if (property.optional) {
                    funBuilder.addStatement(
                        "if (%L != null) json.addProperty(%S, %L)",
                        varName,
                        property.name,
                        varName
                    )
                } else {
                    funBuilder.addStatement(
                        "json.addProperty(%S, %L)",
                        property.name,
                        varName
                    )
                }
        }
    }

    /**
     * Appends a property serialization to a [FunSpec.Builder], with a constant default value.
     * @param name the property json name
     * @param definition the property definition
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendConstantSerialization(
        name: String,
        definition: TypeDefinition.Constant,
        funBuilder: FunSpec.Builder
    ) {
        val constantValue = definition.value
        if (constantValue is String || constantValue is Number) {
            funBuilder.addStatement("json.addProperty(%S, %L)", name, name.variableName())
        } else {
            throw IllegalStateException("Unable to generate serialization for constant type $definition")
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
            is TypeDefinition.Null -> NOTHING
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
            JsonType.NULL -> NOTHING_NULLABLE
            JsonType.BOOLEAN -> BOOLEAN
            JsonType.NUMBER -> DOUBLE
            JsonType.STRING -> STRING
            JsonType.INTEGER -> LONG
            else -> TODO()
        }
    }

    // endregion

    companion object {

        private val NOTHING_NULLABLE = NOTHING.copy(nullable = true)
        private val ANY_NULLABLE = ANY.copy(nullable = true)

        private val TO_JSON = "toJson"

        private val JSON_ELEMENT = ClassName.bestGuess("com.google.gson.JsonElement")
        private val JSON_OBJECT = ClassName.bestGuess("com.google.gson.JsonObject")
        private val JSON_ARRAY = ClassName.bestGuess("com.google.gson.JsonArray")
        private val JSON_PRIMITIVE = ClassName.bestGuess("com.google.gson.JsonPrimitive")
    }
}
