/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.jvm.throws
import java.lang.IllegalArgumentException

class PokoDeserializerGenerator(
    private val packageName: String,
    private val knownTypes: MutableList<String>,
    private val nestedClasses: MutableSet<TypeDefinition.Class>,
    private val nestedEnums: MutableSet<TypeDefinition.Enum>
) {

    private lateinit var rootTypeName: String

    /**
     * Generates a function that deserializes a Json into an instance of class [definition].
     * @param definition the class definition
     * @param rootTypeName the root Type name
     */
    fun generateClassDeserializer(definition: TypeDefinition.Class, rootTypeName: String): FunSpec {
        this.rootTypeName = rootTypeName
        val funBuilder = FunSpec.builder(FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .returns(ClassName.bestGuess(definition.name))
        funBuilder.throws(JSON_PARSE_EXCEPTION)
        funBuilder.addParameter(FROM_JSON_PARAM_NAME, STRING)
        funBuilder.beginControlFlow("try")

        appendDeserializerFunctionBlock(funBuilder, definition)

        funBuilder.nextControlFlow(
            "catch(%L:%T)", EXEPTION_VAR_NAME, ILLEGAL_STATE_EXCEPTION
        )
        funBuilder.addStatement(
            "throw %T(%L.message)",
            JSON_PARSE_EXCEPTION, EXEPTION_VAR_NAME
        )
        funBuilder.nextControlFlow(
            "catch(%L:%T)", EXEPTION_VAR_NAME,
            NUMBER_FORMAT_EXCEPTION
        )
        funBuilder.addStatement(
            "throw %T(%L.message)",
            JSON_PARSE_EXCEPTION, EXEPTION_VAR_NAME
        )
        funBuilder.endControlFlow()
        return funBuilder.build()
    }

    private fun appendDeserializerFunctionBlock(
        funBuilder: FunSpec.Builder,
        definition: TypeDefinition.Class
    ) {
        funBuilder.addStatement(
            "val %L = %T.parseString(%L).asJsonObject",
            ROOT_JSON_OBJECT_PARAM_NAME,
            JSON_PARSER,
            FROM_JSON_PARAM_NAME
        )

        definition.properties.forEach { p ->
            appendPropertyDeserialization(p, funBuilder, ROOT_JSON_OBJECT_PARAM_NAME)
        }

        funBuilder.addStatement("return %L(", definition.name)
        val filteredProperties =
            definition.properties.filter { it.type !is TypeDefinition.Constant }
        val filteredPropertiesSize = filteredProperties.size
        filteredProperties
            .forEachIndexed { index, p ->
                if (index != filteredPropertiesSize - 1) {
                    funBuilder.addStatement("    %L,", p.name.variableName())
                } else {
                    funBuilder.addStatement("    %L", p.name.variableName())
                }
            }
        funBuilder.addStatement(")")
    }

    /**
     * Generates a function that deserializes a Json into an instance of class [definition].
     * @param definition the class definition
     * @param rootTypeName the root Type name
     */
    fun generateEnumDeserializer(definition: TypeDefinition.Enum, rootTypeName: String): FunSpec {
        this.rootTypeName = rootTypeName
        val funBuilder = FunSpec.builder(FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .addParameter(FROM_JSON_PARAM_NAME, String::class)
            .returns(
                definition.asKotlinTypeName(
                    nestedEnums,
                    nestedClasses,
                    knownTypes,
                    packageName,
                    rootTypeName
                )
            )
        funBuilder.addStatement(
            "return values().first{it.%L == %L}",
            PokoGenerator.ENUM_CONSTRUCTOR_JSON_VALUE_NAME,
            FROM_JSON_PARAM_NAME
        )

        return funBuilder.build()
    }

    /**
     * Appends a property deserialization to a [FunSpec.Builder].
     * @param property the property definition
     * @param funBuilder the `fromJson()` [FunSpec] builder.
     * @param rootJsonObjectVarName the root jsonObject var name
     */
    private fun appendPropertyDeserialization(
        property: TypeProperty,
        funBuilder: FunSpec.Builder,
        rootJsonObjectVarName: String
    ) {

        val varName = property.name.variableName()
        when (property.type) {
            is TypeDefinition.Primitive -> appendPrimitiveDeserialization(
                property,
                property.type.type,
                varName,
                rootJsonObjectVarName,
                funBuilder
            )
            is TypeDefinition.Null -> funBuilder.addStatement("val %L = null", property.name.trim())
            is TypeDefinition.Array -> appendArrayDeserialization(
                property,
                property.type,
                varName,
                rootJsonObjectVarName,
                funBuilder
            )
            is TypeDefinition.Enum -> appendEnumDeserialization(
                property,
                varName,
                rootJsonObjectVarName,
                funBuilder
            )
            is TypeDefinition.Class -> appendObjectDeserialization(
                property,
                varName,
                rootJsonObjectVarName,
                funBuilder
            )
        }
    }

    /**
     * Appends a primitive property deserialization to a [FunSpec.Builder].
     * @param property the property definition
     * @param type the primitive type
     * @param rootJsonObjectVarName the root jsonObject var name
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendPrimitiveDeserialization(
        property: TypeProperty,
        type: JsonType?,
        varName: String,
        rootJsonObjectVarName: String,
        funBuilder: FunSpec.Builder
    ) {
        val statementPrefix = if (property.optional) {
            "val %L = %L.getAsJsonPrimitive(%S)?"
        } else {
            "val %L = %L.getAsJsonPrimitive(%S)"
        }
        when (type) {
            JsonType.BOOLEAN -> {
                funBuilder.addStatement(
                    "$statementPrefix.asBoolean",
                    varName,
                    rootJsonObjectVarName,
                    property.name
                )
            }

            JsonType.NUMBER -> {
                funBuilder.addStatement(
                    "$statementPrefix.asDouble",
                    varName,
                    rootJsonObjectVarName,
                    property.name
                )
            }

            JsonType.STRING -> {
                funBuilder.addStatement(
                    "$statementPrefix.asString",
                    varName,
                    rootJsonObjectVarName,
                    property.name
                )
            }

            JsonType.INTEGER -> {
                funBuilder.addStatement(
                    "$statementPrefix.asLong",
                    varName,
                    rootJsonObjectVarName,
                    property.name
                )
            }
            else -> {
                throw IllegalArgumentException(
                    "We do not support deserialization for" +
                        " type: ${type?.name}"
                )
            }

        }
    }

    /**
     * Appends an Array property deserialization to a [FunSpec.Builder].
     * @param property the property definition
     * @param type the Array type
     * @param varName the constructor var name
     * @param rootJsonObjectVarName the root jsonObject var name
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendArrayDeserialization(
        property: TypeProperty,
        type: TypeDefinition.Array,
        varName: String,
        rootJsonObjectVarName: String,
        funBuilder: FunSpec.Builder
    ) {

        if (property.optional) {
            funBuilder.beginControlFlow(
                "val %L = %L.get(%S)?.asJsonArray?.let",
                varName,
                rootJsonObjectVarName,
                property.name
            )
        } else {
            funBuilder.beginControlFlow(
                "val %L = %L.get(%S).asJsonArray.let",
                varName,
                rootJsonObjectVarName,
                property.name
            )
        }
        funBuilder.addStatement("%L -> ", JSON_ARRAY_VAR_NAME)
        val collectionClassName: ClassName = if (type.uniqueItems) {
            MUTABLE_SET
        } else {
            MUTABLE_LIST
        }
        funBuilder.addStatement(
            "val %L = %T(%L.size())",
            ARRAY_COLLECTION_VAR_NAME,
            collectionClassName.parameterizedBy(
                type.items.asKotlinTypeName(
                    nestedEnums,
                    nestedClasses,
                    knownTypes,
                    packageName,
                    rootTypeName
                )
            ),
            JSON_ARRAY_VAR_NAME
        )
        funBuilder.beginControlFlow("%L.forEach", JSON_ARRAY_VAR_NAME)
        appendArrayItemDeserialization(type, funBuilder, ARRAY_COLLECTION_VAR_NAME)

        funBuilder.endControlFlow()
        funBuilder.addStatement("%L", ARRAY_COLLECTION_VAR_NAME)
        funBuilder.endControlFlow()
    }

    private fun appendArrayItemDeserialization(
        type: TypeDefinition.Array,
        funBuilder: FunSpec.Builder,
        collectionVarName: String
    ) {
        when (type.items) {

            is TypeDefinition.Primitive -> {
                when (type.items.type) {
                    JsonType.BOOLEAN -> {
                        funBuilder.addStatement("%L.add(it.asBoolean)", collectionVarName)
                    }

                    JsonType.NUMBER -> {
                        funBuilder.addStatement("%L.add(it.asDouble)", collectionVarName)
                    }
                    JsonType.STRING -> {
                        funBuilder.addStatement("%L.add(it.asString)", collectionVarName)
                    }

                    JsonType.INTEGER -> {
                        funBuilder.addStatement("%L.add(it.asLong)", collectionVarName)
                    }
                }
            }
            is TypeDefinition.Class ->
                funBuilder.addStatement(
                    "%L.add(%T.fromJson(it.toString()))",
                    collectionVarName,
                    type.items.asKotlinTypeName(
                        nestedEnums,
                        nestedClasses,
                        knownTypes,
                        packageName,
                        rootTypeName
                    )
                )
            is TypeDefinition.Enum -> funBuilder.addStatement(
                "%L.add(%T.fromJson(it.asString))",
                collectionVarName,
                type.items.asKotlinTypeName(
                    nestedEnums,
                    nestedClasses,
                    knownTypes,
                    packageName,
                    rootTypeName
                )
            )
        }
    }

    /**
     * Appends an Object property deserialization to a [FunSpec.Builder].
     * @param property the property definition
     * @param varName the constructor var name
     * @param rootJsonObjectVarName the root jsonObject var name
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendObjectDeserialization(
        property: TypeProperty,
        varName: String,
        rootJsonObjectVarName: String,
        funBuilder: FunSpec.Builder
    ) {

        if (property.optional) {
            funBuilder.beginControlFlow(
                "val %L = %L.getAsJsonObject(%S)?.toString()?.let",
                varName,
                rootJsonObjectVarName,
                property.name
            )
        } else {
            funBuilder.beginControlFlow(
                "val %L = %L.getAsJsonObject(%S).toString().let",
                varName,
                rootJsonObjectVarName,
                property.name
            )
        }

        funBuilder.addStatement(
            "%T.fromJson(it)",
            property.type.asKotlinTypeName(
                nestedEnums,
                nestedClasses,
                knownTypes,
                packageName,
                rootTypeName
            )
        )
        funBuilder.endControlFlow()
    }

    /**
     * Appends an Enum property deserialization to a [FunSpec.Builder].
     * @param property the property definition
     * @param varName the constructor var name
     * @param rootJsonObjectVarName the root jsonObject var name
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendEnumDeserialization(
        property: TypeProperty,
        varName: String,
        rootJsonObjectVarName: String,
        funBuilder: FunSpec.Builder
    ) {

        if (property.optional) {
            funBuilder.beginControlFlow(
                "val %L = %L.get(%S)?.asString?.let",
                varName,
                rootJsonObjectVarName,
                property.name
            )
        } else {
            funBuilder.beginControlFlow(
                "val %L = %L.get(%S).asString.let",
                varName,
                rootJsonObjectVarName,
                property.name
            )
        }
        funBuilder.addStatement(
            "%T.fromJson(it)",
            property.type.asKotlinTypeName(
                nestedEnums,
                nestedClasses,
                knownTypes,
                packageName,
                rootTypeName
            )
        )
        funBuilder.endControlFlow()
    }

    companion object {

        private const val FROM_JSON = "fromJson"
        private const val FROM_JSON_PARAM_NAME = "serializedObject"
        private const val ROOT_JSON_OBJECT_PARAM_NAME = "jsonObject"
        private const val EXEPTION_VAR_NAME = "e"
        private const val ARRAY_COLLECTION_VAR_NAME = "collection"
        private const val JSON_ARRAY_VAR_NAME = "jsonArray"
        private val JSON_PARSE_EXCEPTION =
            ClassName.bestGuess("com.google.gson.JsonParseException")
        private val JSON_PARSER = ClassName.bestGuess("com.google.gson.JsonParser")
        private val ILLEGAL_STATE_EXCEPTION = ClassName.bestGuess("java.lang.IllegalStateException")
        private val NUMBER_FORMAT_EXCEPTION = ClassName.bestGuess("java.lang.NumberFormatException")
        private val MUTABLE_LIST = ClassName.bestGuess("kotlin.collections.ArrayList")
        private val MUTABLE_SET = ClassName.bestGuess("kotlin.collections.HashSet")
    }
}