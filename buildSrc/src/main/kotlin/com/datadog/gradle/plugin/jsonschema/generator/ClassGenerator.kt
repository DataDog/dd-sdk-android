/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.JsonPrimitiveType
import com.datadog.gradle.plugin.jsonschema.JsonType
import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.datadog.gradle.plugin.jsonschema.TypeProperty
import com.datadog.gradle.plugin.jsonschema.variableName
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

class ClassGenerator(
    packageName: String,
    knownTypes: MutableSet<KotlinTypeWrapper>
) : TypeSpecGenerator<TypeDefinition.Class>(
    packageName,
    knownTypes
) {

    private val deserializer = ClassJsonElementDeserializerGenerator(packageName, knownTypes)
    private val stringDeserializer = ClassStringDeserializerGenerator(packageName, knownTypes)

    // region TypeSpecGenerator

    override fun generate(
        definition: TypeDefinition.Class,
        rootTypeName: String
    ): TypeSpec.Builder {
        val typeBuilder = TypeSpec.classBuilder(definition.name)

        if (definition.parentType != null) {
            typeBuilder.superclass(definition.parentType.asKotlinTypeName(rootTypeName))
        }

        if (
            definition.properties.any { it.type !is TypeDefinition.Constant } ||
            definition.additionalProperties != null
        ) {
            typeBuilder.addModifiers(KModifier.DATA)
        }

        typeBuilder.addKdoc(generateKDoc(definition))

        definition.properties.forEach {
            typeBuilder.addProperty(generateProperty(it, rootTypeName))
        }
        if (definition.additionalProperties != null) {
            typeBuilder.addProperty(
                generateAdditionalProperties(definition.additionalProperties, rootTypeName)
            )
        }

        typeBuilder.primaryConstructor(generateConstructor(definition, rootTypeName))

        typeBuilder.addFunction(generateClassSerializer(definition))

        if (!definition.isConstantClass()) {
            typeBuilder.addType(generateCompanionObject(definition, rootTypeName))
        }

        return typeBuilder
    }

    // endregion

    // region Internal

    private fun generateKDoc(definition: TypeDefinition.Class): CodeBlock {
        val docBuilder = CodeBlock.builder()

        if (definition.description.isNotBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }

        definition.properties.forEach { p ->
            if (p.type !is TypeDefinition.Constant && p.type.description.isNotBlank()) {
                docBuilder.add("@param ${p.name.variableName()} ${p.type.description}\n")
            }
        }

        if (
            definition.additionalProperties != null &&
            definition.additionalProperties.description.isNotBlank()
        ) {
            docBuilder.add(
                "@param ${Identifier.PARAM_ADDITIONAL_PROPS} ${definition.additionalProperties.description}\n"
            )
        }
        return docBuilder.build()
    }

    private fun generateClassSerializer(definition: TypeDefinition.Class): FunSpec {
        val funBuilder = FunSpec.builder(Identifier.FUN_TO_JSON)
            .returns(ClassNameRef.JsonElement)

        if (definition.parentType != null) {
            funBuilder.addModifiers(KModifier.OVERRIDE)
        }

        funBuilder.addStatement("val json = %T()", ClassNameRef.JsonObject)

        definition.properties.forEach { p ->
            funBuilder.appendPropertySerialization(p)
        }

        if (definition.additionalProperties != null) {
            funBuilder.appendAdditionalPropertiesSerialization(
                definition.additionalProperties,
                definition.properties.isNotEmpty()
            )
        }

        funBuilder.addStatement("return json")

        return funBuilder.build()
    }

    private fun FunSpec.Builder.appendPropertySerialization(
        property: TypeProperty
    ) {
        val propertyName = property.name.variableName()
        val isNullable =
            property.optional && property.type !is TypeDefinition.Constant && property.type !is TypeDefinition.Null
        val refName = if (isNullable) {
            beginControlFlow("%L?.let·{·%LNonNull·->", propertyName, propertyName)
            "${propertyName}NonNull"
        } else {
            propertyName
        }

        when (property.type) {
            is TypeDefinition.Constant -> appendConstantSerialization(
                property.type,
                property.name
            )
            is TypeDefinition.Primitive -> appendPrimitiveSerialization(
                property,
                refName
            )
            is TypeDefinition.Null -> appendNullSerialization(property)
            is TypeDefinition.Array -> appendArraySerialization(property, property.type, refName)
            is TypeDefinition.Class,
            is TypeDefinition.OneOfClass,
            is TypeDefinition.Enum -> appendTypeSerialization(property, refName)
        }

        if (isNullable) {
            endControlFlow()
        }
    }

    private fun FunSpec.Builder.appendConstantSerialization(
        type: TypeDefinition.Constant,
        name: String
    ) {
        val constantValue = type.value
        if (constantValue is String || constantValue is Number) {
            addStatement("json.addProperty(%S, %L)", name, name.variableName())
        } else {
            throw IllegalStateException(
                "Unable to generate serialization for constant $constantValue with type $type"
            )
        }
    }

    private fun FunSpec.Builder.appendPrimitiveSerialization(
        property: TypeProperty,
        propertyName: String
    ) {
        addStatement(
            "json.addProperty(%S, %L)",
            property.name,
            propertyName
        )
    }

    private fun FunSpec.Builder.appendNullSerialization(property: TypeProperty) {
        addStatement(
            "json.add(%S, %T.%L)",
            property.name,
            ClassNameRef.JsonNull,
            "INSTANCE"
        )
    }

    private fun FunSpec.Builder.appendArraySerialization(
        property: TypeProperty,
        propertyType: TypeDefinition.Array,
        propertyName: String
    ) {
        val resultArrayName = "${property.name.variableName()}Array"

        addStatement(
            "val %L = %T(%L.size)",
            resultArrayName,
            ClassNameRef.JsonArray,
            propertyName
        )

        when (propertyType.items) {
            is TypeDefinition.Null,
            is TypeDefinition.Primitive,
            is TypeDefinition.Constant -> addStatement(
                "%L.forEach { %L.add(it) }",
                propertyName,
                resultArrayName
            )
            is TypeDefinition.Class,
            is TypeDefinition.OneOfClass,
            is TypeDefinition.Enum -> addStatement(
                "%L.forEach { %L.add(it.%L()) }",
                propertyName,
                resultArrayName,
                Identifier.FUN_TO_JSON
            )
            is TypeDefinition.Array -> throw UnsupportedOperationException(
                "Unable to serialize an array of arrays: $propertyType"
            )
        }

        addStatement("json.add(%S, %L)", property.name, resultArrayName)
    }

    private fun FunSpec.Builder.appendTypeSerialization(
        property: TypeProperty,
        propertyName: String
    ) {
        addStatement(
            "json.add(%S, %L.%L())",
            property.name,
            propertyName,
            Identifier.FUN_TO_JSON
        )
    }

    private fun FunSpec.Builder.appendAdditionalPropertiesSerialization(
        additionalProperties: TypeDefinition,
        hasKnownProperties: Boolean
    ) {
        beginControlFlow("%L.forEach { (k, v) ->", Identifier.PARAM_ADDITIONAL_PROPS)

        if (hasKnownProperties) {
            beginControlFlow("if (k !in %L)", Identifier.PARAM_RESERVED_PROPS)
        }

        when (additionalProperties) {
            is TypeDefinition.Primitive -> addStatement("json.addProperty(k, v)")
            is TypeDefinition.Class -> addStatement(
                "json.add(k, v.%M())",
                MemberName(Identifier.PACKAGE_UTILS, Identifier.FUN_TO_JSON_ELT)
            )
            is TypeDefinition.Enum -> addStatement("json.add(k, v.%L()) }", Identifier.FUN_TO_JSON)
            is TypeDefinition.Null -> addStatement("json.add(k, null) }")
            is TypeDefinition.Array -> throw IllegalStateException(
                "Unable to generate custom serialization for Array type $additionalProperties"
            )
            is TypeDefinition.Constant -> throw IllegalStateException(
                "Unable to generate custom serialization for constant type $additionalProperties"
            )
            else -> throw IllegalStateException(
                "Unable to generate custom serialization for unknown type $additionalProperties"
            )
        }

        if (hasKnownProperties) {
            endControlFlow()
        }

        endControlFlow()
    }

    private fun generateConstructor(
        definition: TypeDefinition.Class,
        rootTypeName: String
    ): FunSpec {
        val constructorBuilder = FunSpec.constructorBuilder()

        definition.properties.forEach { p ->
            if (p.type !is TypeDefinition.Constant) {
                val propertyName = p.name.variableName()
                val isNullable = (p.optional || p.type is TypeDefinition.Null)
                val notNullableType = p.type.asKotlinTypeName(rootTypeName)
                val propertyType = notNullableType.copy(nullable = isNullable)
                constructorBuilder.addParameter(
                    ParameterSpec.builder(propertyName, propertyType)
                        .withDefaultValue(p, rootTypeName)
                        .build()
                )
            }
        }

        if (definition.additionalProperties != null) {
            val mapType = definition.additionalProperties.additionalPropertyType(rootTypeName)
            constructorBuilder.addParameter(
                ParameterSpec.builder(Identifier.PARAM_ADDITIONAL_PROPS, mapType)
                    .defaultValue("mutableMapOf()")
                    .build()
            )
        }

        return constructorBuilder.build()
    }

    private fun generateProperty(property: TypeProperty, rootTypeName: String): PropertySpec {
        val propertyName = property.name.variableName()
        val propertyType = property.type
        val isNullable = (property.optional || propertyType is TypeDefinition.Null) &&
            (propertyType !is TypeDefinition.Constant)
        val notNullableType = propertyType.asKotlinTypeName(rootTypeName)
        val type = notNullableType.copy(nullable = isNullable)
        val initializer = if (propertyType is TypeDefinition.Constant) {
            getKotlinValue(propertyType.value, propertyType.type)
        } else {
            propertyName
        }

        return PropertySpec.builder(propertyName, type)
            .mutable(!property.readOnly)
            .initializer(initializer)
            .build()
    }

    private fun generateAdditionalProperties(
        additionalPropertyType: TypeDefinition,
        rootTypeName: String
    ): PropertySpec {
        val type = additionalPropertyType.additionalPropertyType(rootTypeName)

        return PropertySpec.builder(Identifier.PARAM_ADDITIONAL_PROPS, type)
            .mutable(false)
            .initializer(Identifier.PARAM_ADDITIONAL_PROPS)
            .build()
    }

    private fun getKotlinValue(
        value: Any?,
        type: Any?
    ): String {
        return when {
            value is JsonPrimitiveType -> {
                throw IllegalStateException(
                    "Unable to get Kotlin Value from $value with type $type"
                )
            }
            value is String -> "\"$value\""
            value is Double &&
                (type == JsonType.INTEGER || type == JsonPrimitiveType.INTEGER) -> {
                "${value.toLong()}L"
            }
            value is Double -> {
                "$value"
            }
            value is Boolean -> {
                "$value"
            }
            else -> throw IllegalStateException(
                "Unable to get Kotlin Value from $value with type $type"
            )
        }
    }

    private fun generateCompanionObject(
        definition: TypeDefinition.Class,
        rootTypeName: String
    ): TypeSpec {
        val typeBuilder = TypeSpec.companionObjectBuilder()
            .addFunction(stringDeserializer.generate(definition, rootTypeName))
            .addFunction(deserializer.generate(definition, rootTypeName))

        if (definition.additionalProperties != null && definition.properties.isNotEmpty()) {
            typeBuilder.addProperty(generateReservedPropertiesArray(definition))
        }
        return typeBuilder.build()
    }

    private fun generateReservedPropertiesArray(definition: TypeDefinition.Class): PropertySpec {
        val propertyNames = definition.properties.joinToString(", ") { "\"${it.name}\"" }

        val propertyBuilder = PropertySpec.builder(
            Identifier.PARAM_RESERVED_PROPS,
            ARRAY.parameterizedBy(STRING),
            KModifier.INTERNAL
        ).initializer("arrayOf($propertyNames)")

        return propertyBuilder.build()
    }

    // endregion

    // region Internal Extensions

    private fun ParameterSpec.Builder.withDefaultValue(
        p: TypeProperty,
        rootTypeName: String
    ): ParameterSpec.Builder {
        val defaultValue = p.defaultValue
        if (defaultValue != null) {
            when (p.type) {
                is TypeDefinition.Primitive -> defaultValue(
                    getKotlinValue(
                        defaultValue,
                        p.type.type
                    )
                )
                is TypeDefinition.Enum -> defaultValue(
                    "%T.%L",
                    p.type.asKotlinTypeName(rootTypeName),
                    p.type.enumConstantName(
                        if (defaultValue is Number) {
                            defaultValue.toInt().toString()
                        } else {
                            defaultValue.toString()
                        }
                    )
                )
                else -> throw IllegalArgumentException(
                    "Unable to generate default value for class: ${p.type}. " +
                        "This feature is not supported yet"
                )
            }
        } else if (p.optional || p.type is TypeDefinition.Null) {
            defaultValue("null")
        }
        return this
    }

    private fun TypeDefinition.additionalPropertyType(rootTypeName: String): TypeName {
        val valueType = if (this is TypeDefinition.Primitive) {
            this.asKotlinTypeName(rootTypeName)
        } else {
            ANY.copy(nullable = true)
        }
        return MUTABLE_MAP.parameterizedBy(STRING, valueType)
    }

    // endregion
}
