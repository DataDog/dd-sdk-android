/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.jvm.throws
import java.lang.IllegalStateException

class PokoDeserializerGenerator(
    private val packageName: String,
    private val knownTypes: MutableList<String>,
    private val nestedClasses: MutableSet<TypeDefinition.Class>,
    private val nestedEnums: MutableSet<TypeDefinition.Enum>
) {

    private lateinit var rootTypeName: String

    /**
     * Generate the companion class for a Class Type.
     * @param definition the type definition
     * @param rootTypeName the type name of the root parent class
     */
    fun generateCompanionForClass(
        definition: TypeDefinition.Class,
        rootTypeName: String
    ): TypeSpec {
        val companionSpecBuilder = TypeSpec.companionObjectBuilder()

        if (definition.additionalProperties != null) {
            if (!definition.properties.isNullOrEmpty()) {
                companionSpecBuilder.addProperty(generateReservedPropertyNames(definition))
            }
        }

        companionSpecBuilder.addFunction(generateClassDeserializer(definition, rootTypeName))

        return companionSpecBuilder.build()
    }

    /**
     * Generate the companion class for a Class Type.
     * @param definition the type definition
     * @param rootTypeName the type name of the root parent class
     */
    fun generateCompanionForEnum(definition: TypeDefinition.Enum, rootTypeName: String): TypeSpec {
        val companionSpecBuilder = TypeSpec.companionObjectBuilder()

        companionSpecBuilder.addFunction(generateEnumDeserializer(definition, rootTypeName))

        return companionSpecBuilder.build()
    }

    // region Internal / Class

    /**
     * Generates a function that deserializes a Json into an instance of class [definition].
     * @param definition the class definition
     * @param rootTypeName the root Type name
     */
    private fun generateClassDeserializer(
        definition: TypeDefinition.Class,
        rootTypeName: String
    ): FunSpec {
        this.rootTypeName = rootTypeName
        val funBuilder = FunSpec.builder(FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .returns(ClassName.bestGuess(definition.name))
        funBuilder.throws(JSON_PARSE_EXCEPTION)
        funBuilder.addParameter(FROM_JSON_PARAM_NAME, STRING)
        funBuilder.beginControlFlow("try")

        appendDeserializerFunctionBlock(funBuilder, definition)

        funBuilder.nextControlFlow(
            "catch (%L: %T)",
            EXCEPTION_VAR_NAME,
            ILLEGAL_STATE_EXCEPTION
        )
        funBuilder.addStatement(
            "throw %T(%L.message)",
            JSON_PARSE_EXCEPTION,
            EXCEPTION_VAR_NAME
        )
        funBuilder.nextControlFlow(
            "catch (%L: %T)",
            EXCEPTION_VAR_NAME,
            NUMBER_FORMAT_EXCEPTION
        )
        funBuilder.addStatement(
            "throw %T(%L.message)",
            JSON_PARSE_EXCEPTION,
            EXCEPTION_VAR_NAME
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
            assignDeserializedProperty(
                propertyType = p.type,
                assignee = "val ${p.name.variableName()}",
                getter = "$ROOT_JSON_OBJECT_PARAM_NAME.get(\"${p.name}\")",
                nullable = p.optional,
                funBuilder = funBuilder
            )
        }
        if (definition.additionalProperties != null) {
            appendAdditionalPropertiesDeserialization(
                definition.additionalProperties,
                funBuilder,
                !definition.properties.isNullOrEmpty()
            )
        }

        val filteredProperties =
            definition.properties.filter { it.type !is TypeDefinition.Constant }
        val properties = filteredProperties.joinToString(", ") { it.name.variableName() }

        val additionalSuffix = if (definition.additionalProperties != null) {
            if (filteredProperties.isEmpty()) {
                PokoGenerator.ADDITIONAL_PROPERTIES_NAME
            } else {
                ", ${PokoGenerator.ADDITIONAL_PROPERTIES_NAME}"
            }
        } else {
            ""
        }

        funBuilder.addStatement("return %L($properties$additionalSuffix)", definition.name)
    }

    /**
     * Appends an additionalProperties deserialization to a [FunSpec.Builder].
     * @param additionalProperties the additional properties type definition
     * @param funBuilder the `fromJson()` [FunSpec] builder.
     */
    private fun appendAdditionalPropertiesDeserialization(
        additionalProperties: TypeDefinition,
        funBuilder: FunSpec.Builder,
        hasKnownProperties: Boolean
    ) {

        funBuilder.addStatement(
            "val %L = mutableMapOf<%T, %T>()",
            PokoGenerator.ADDITIONAL_PROPERTIES_NAME,
            STRING,
            additionalProperties.additionalPropertyType(
                nestedEnums,
                nestedClasses,
                knownTypes,
                packageName,
                rootTypeName
            )
        )
        funBuilder.beginControlFlow("for (entry in %L.entrySet())", ROOT_JSON_OBJECT_PARAM_NAME)

        if (hasKnownProperties) {
            funBuilder.beginControlFlow("if (entry.key !in %L)", RESERVED_PROPERTIES_NAME)
        }

        if (additionalProperties is TypeDefinition.Class) {
            funBuilder.addStatement(
                "%L[entry.key] = entry.value",
                PokoGenerator.ADDITIONAL_PROPERTIES_NAME
            )
        } else {
            assignDeserializedProperty(
                propertyType = additionalProperties,
                assignee = "${PokoGenerator.ADDITIONAL_PROPERTIES_NAME}[entry.key]",
                getter = "entry.value",
                nullable = false,
                funBuilder = funBuilder
            )
        }

        if (hasKnownProperties) {
            funBuilder.endControlFlow()
        }
        funBuilder.endControlFlow()
    }

    /**
     * Appends a property deserialization to a [FunSpec.Builder].
     * @param propertyType the property's type definition
     * @param assignee the assignee prefix
     * @param getter the code snippet to get the json value
     * @param nullable whether the value is nullable
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun assignDeserializedProperty(
        propertyType: TypeDefinition,
        assignee: String,
        getter: String,
        nullable: Boolean,
        funBuilder: FunSpec.Builder
    ) {
        when (propertyType) {
            is TypeDefinition.Null -> funBuilder.addStatement("$assignee = null")
            is TypeDefinition.Primitive -> appendPrimitiveDeserialization(
                propertyType,
                assignee,
                getter,
                nullable,
                funBuilder
            )
            is TypeDefinition.Array -> appendArrayDeserialization(
                propertyType,
                assignee,
                getter,
                nullable,
                funBuilder
            )
            is TypeDefinition.Class -> appendObjectDeserialization(
                propertyType,
                assignee,
                getter,
                nullable,
                funBuilder
            )
            is TypeDefinition.Enum -> appendEnumDeserialization(
                propertyType,
                assignee,
                getter,
                nullable,
                funBuilder
            )
            is TypeDefinition.Constant -> {
                // No Op
            }
        }
    }

    /**
     * Appends a primitive property deserialization to a [FunSpec.Builder].
     * @param type the primitive type
     * @param assignee the assignee prefix
     * @param getter the code snippet to get the json value
     * @param nullable whether the value is nullable
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendPrimitiveDeserialization(
        type: TypeDefinition.Primitive,
        assignee: String,
        getter: String,
        nullable: Boolean,
        funBuilder: FunSpec.Builder
    ) {
        val opt = if (nullable) "?" else ""
        funBuilder.addStatement("$assignee = $getter$opt.${type.asPrimitiveType()}")
    }

    /**
     * Appends an Array property deserialization to a [FunSpec.Builder].
     * @param arrayType the array's type
     * @param assignee the assignee prefix
     * @param getter the code snippet to get the json value
     * @param nullable whether the value is nullable
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendArrayDeserialization(
        arrayType: TypeDefinition.Array,
        assignee: String,
        getter: String,
        nullable: Boolean,
        funBuilder: FunSpec.Builder
    ) {
        val opt = if (nullable) "?" else ""
        funBuilder.beginControlFlow(
            "$assignee = $getter$opt.asJsonArray$opt.let { %L ->",
            JSON_ARRAY_VAR_NAME
        )
        val collectionClassName: ClassName = if (arrayType.uniqueItems) {
            MUTABLE_SET
        } else {
            MUTABLE_LIST
        }
        funBuilder.addStatement(
            "val %L = %T(%L.size())",
            ARRAY_COLLECTION_VAR_NAME,
            collectionClassName.parameterizedBy(
                arrayType.items.asKotlinTypeName(
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
        appendArrayItemDeserialization(arrayType, funBuilder)

        funBuilder.endControlFlow()
        funBuilder.addStatement("%L", ARRAY_COLLECTION_VAR_NAME)
        funBuilder.endControlFlow()
    }

    /**
     * Appends an array item deserialization to a [FunSpec.Builder].
     * @param arrayType the array's type
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendArrayItemDeserialization(
        arrayType: TypeDefinition.Array,
        funBuilder: FunSpec.Builder
    ) {
        when (arrayType.items) {
            is TypeDefinition.Primitive -> funBuilder.addStatement(
                "%L.add(it.${arrayType.items.asPrimitiveType()})",
                ARRAY_COLLECTION_VAR_NAME
            )
            is TypeDefinition.Class ->
                funBuilder.addStatement(
                    "%L.add(%T.fromJson(it.toString()))",
                    ARRAY_COLLECTION_VAR_NAME,
                    arrayType.items.asKotlinTypeName(
                        nestedEnums,
                        nestedClasses,
                        knownTypes,
                        packageName,
                        rootTypeName
                    )
                )
            is TypeDefinition.Enum -> funBuilder.addStatement(
                "%L.add(%T.fromJson(it.asString))",
                ARRAY_COLLECTION_VAR_NAME,
                arrayType.items.asKotlinTypeName(
                    nestedEnums,
                    nestedClasses,
                    knownTypes,
                    packageName,
                    rootTypeName
                )
            )
            is TypeDefinition.Constant,
            is TypeDefinition.Array,
            is TypeDefinition.Null -> throw IllegalStateException(
                "Unable to deserialize an array of ${arrayType.items}"
            )
        }
    }

    /**
     * Appends an Object property deserialization to a [FunSpec.Builder].
     * @param propertyType the property's type definition
     * @param assignee the assignee prefix
     * @param getter the code snippet to get the json value
     * @param nullable whether the value is nullable
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendObjectDeserialization(
        propertyType: TypeDefinition.Class,
        assignee: String,
        getter: String,
        nullable: Boolean,
        funBuilder: FunSpec.Builder
    ) {
        val opt = if (nullable) "?" else ""
        funBuilder.beginControlFlow("$assignee = $getter$opt.toString()$opt.let")
        funBuilder.addStatement(
            "%T.fromJson(it)",
            propertyType.asKotlinTypeName(
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
     * @param propertyType the property's type definition
     * @param assignee the assignee prefix
     * @param getter the code snippet to get the json value
     * @param nullable whether the value is nullable
     * @param funBuilder the `toJson()` [FunSpec] builder.
     */
    private fun appendEnumDeserialization(
        propertyType: TypeDefinition.Enum,
        assignee: String,
        getter: String,
        nullable: Boolean,
        funBuilder: FunSpec.Builder
    ) {
        val opt = if (nullable) "?" else ""
        funBuilder.beginControlFlow("$assignee = $getter$opt.asString$opt.let")

        funBuilder.addStatement(
            "%T.fromJson(it)",
            propertyType.asKotlinTypeName(
                nestedEnums,
                nestedClasses,
                knownTypes,
                packageName,
                rootTypeName
            )
        )
        funBuilder.endControlFlow()
    }

    private fun generateReservedPropertyNames(definition: TypeDefinition.Class): PropertySpec {

        val propertyNames = definition.properties
            .joinToString(", ") { "\"${it.name}\"" }

        val propertyBuilder = PropertySpec.builder(
            RESERVED_PROPERTIES_NAME,
            ARRAY.parameterizedBy(STRING),
            KModifier.PRIVATE
        )
            .initializer("arrayOf($propertyNames)")
        return propertyBuilder.build()
    }

    private fun TypeDefinition.Primitive.asPrimitiveType(): String {
        return when (type) {
            JsonPrimitiveType.BOOLEAN -> "asBoolean"
            JsonPrimitiveType.DOUBLE -> "asDouble"
            JsonPrimitiveType.STRING -> "asString"
            JsonPrimitiveType.INTEGER -> "asLong"
        }
    }

    // endregion

    // region Internal / Enum

    /**
     * Generates a function that deserializes a Json into an instance of class [definition].
     * @param definition the class definition
     * @param rootTypeName the root Type name
     */
    private fun generateEnumDeserializer(
        definition: TypeDefinition.Enum,
        rootTypeName: String
    ): FunSpec {
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
            "return values().first { it.%L == %L }",
            PokoGenerator.ENUM_CONSTRUCTOR_JSON_VALUE_NAME,
            FROM_JSON_PARAM_NAME
        )

        return funBuilder.build()
    }

    // endregion

    companion object {

        private const val FROM_JSON = "fromJson"
        private const val FROM_JSON_PARAM_NAME = "serializedObject"
        private const val ROOT_JSON_OBJECT_PARAM_NAME = "jsonObject"
        private const val EXCEPTION_VAR_NAME = "e"
        private const val ARRAY_COLLECTION_VAR_NAME = "collection"
        private const val JSON_ARRAY_VAR_NAME = "jsonArray"
        private val JSON_PARSE_EXCEPTION =
            ClassName.bestGuess("com.google.gson.JsonParseException")
        private val JSON_PARSER = ClassName.bestGuess("com.google.gson.JsonParser")
        private val ILLEGAL_STATE_EXCEPTION = ClassName.bestGuess("java.lang.IllegalStateException")
        private val NUMBER_FORMAT_EXCEPTION = ClassName.bestGuess("java.lang.NumberFormatException")
        private val MUTABLE_LIST = ClassName.bestGuess("kotlin.collections.ArrayList")
        private val MUTABLE_SET = ClassName.bestGuess("kotlin.collections.HashSet")

        private const val RESERVED_PROPERTIES_NAME = "RESERVED_PROPERTIES"
    }
}
