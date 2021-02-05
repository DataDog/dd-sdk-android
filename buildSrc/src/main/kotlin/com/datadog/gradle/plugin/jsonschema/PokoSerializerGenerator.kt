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

        if (definition.additionalProperties != null) {
            appendAdditionalPropertiesSerialization(definition.additionalProperties, funBuilder)
        }

        funBuilder.addStatement("return json")

        return funBuilder.build()
    }

    private fun appendAdditionalPropertiesSerialization(
        additionalProperties: TypeDefinition,
        funBuilder: FunSpec.Builder
    ) {
        funBuilder.beginControlFlow(
            "%L.forEach { (k, v) ->",
            PokoGenerator.ADDITIONAL_PROPERTIES_NAME
        )

        when (additionalProperties) {
            is TypeDefinition.Primitive -> funBuilder.addStatement("json.addProperty(k, v)")
            is TypeDefinition.Class,
            is TypeDefinition.Enum -> funBuilder.addStatement("json.add(k, v.%L()) }", TO_JSON)
            is TypeDefinition.Null -> funBuilder.addStatement("json.add(k, null) }")
            is TypeDefinition.Array -> throw IllegalStateException(
                "Unable to generate serialization for Array type $additionalProperties"
            )
            is TypeDefinition.Constant -> throw IllegalStateException(
                "Unable to generate serialization for constant type $additionalProperties"
            )
        }

        funBuilder.endControlFlow()
    }

    /**
     * Generates a function serializing the type to Json
     */
    fun generateEnumSerializer(): FunSpec {
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
            is TypeDefinition.Null,
            is TypeDefinition.Primitive,
            is TypeDefinition.Constant -> funBuilder.addStatement(
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
            is TypeDefinition.Array -> throw UnsupportedOperationException(
                "Unable to serialize an array of arrays: $type"
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
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendPrimitiveSerialization(
        property: TypeProperty,
        varName: String,
        funBuilder: FunSpec.Builder
    ) {
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
            throw IllegalStateException(
                "Unable to generate serialization for constant type $definition"
            )
        }
    }

    companion object {

        private const val TO_JSON = "toJson"
        private val JSON_ELEMENT = ClassName.bestGuess("com.google.gson.JsonElement")
        private val JSON_OBJECT = ClassName.bestGuess("com.google.gson.JsonObject")
        private val JSON_ARRAY = ClassName.bestGuess("com.google.gson.JsonArray")
        private val JSON_PRIMITIVE = ClassName.bestGuess("com.google.gson.JsonPrimitive")
    }
}
