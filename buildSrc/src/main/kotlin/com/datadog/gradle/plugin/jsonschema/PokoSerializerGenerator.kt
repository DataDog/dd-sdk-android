/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec

class PokoSerializerGenerator {

    /**
     * Generates a function serializing the type to Json
     * @param definition the class definition
     */
    fun generateClassSerializer(definition: TypeDefinition.Class): FunSpec {
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
    fun generateEnumSerializer(definition: TypeDefinition.Enum): FunSpec {
        val funBuilder = FunSpec.builder(TO_JSON)
            .returns(JSON_ELEMENT)
        funBuilder.addStatement(
            "return %T(%L)",
            JSON_PRIMITIVE,
            PokoGenerator.ENUM_CONSTRUCTOR_JSON_VALUE_NAME
        )

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
                "%L?.let { json.add(%S, it.%L()) }",
                varName,
                property.name,
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
        val arrayVar = if (property.optional) {
            funBuilder.beginControlFlow("%L?.let { temp ->", varName)
            "temp"
        } else {
            varName
        }

        funBuilder.addStatement(
            "val %LArray = %T(%L.size)", varName,
            JSON_ARRAY, arrayVar
        )
        when (type.items) {
            is TypeDefinition.Primitive -> funBuilder.addStatement(
                "%L.forEach { %LArray.add(it) }",
                arrayVar,
                varName
            )
            is TypeDefinition.Class,
            is TypeDefinition.Enum -> funBuilder.addStatement(
                "%L.forEach { %LArray.add(it.%L()) }",
                arrayVar,
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
                        "%L?.let { json.addProperty(%S, it) }",
                        varName,
                        property.name
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

    companion object {

        private val TO_JSON = "toJson"
        private val JSON_ELEMENT = ClassName.bestGuess("com.google.gson.JsonElement")
        private val JSON_OBJECT = ClassName.bestGuess("com.google.gson.JsonObject")
        private val JSON_ARRAY = ClassName.bestGuess("com.google.gson.JsonArray")
        private val JSON_PRIMITIVE = ClassName.bestGuess("com.google.gson.JsonPrimitive")
    }
}