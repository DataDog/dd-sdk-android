/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.JsonType
import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName

class EnumClassGenerator(
    packageName: String,
    knownTypes: MutableSet<KotlinTypeWrapper>
) : TypeSpecGenerator<TypeDefinition.Enum>(
    packageName,
    knownTypes
) {

    // region TypeSpecGenerator

    override fun generate(
        definition: TypeDefinition.Enum,
        rootTypeName: String
    ): TypeSpec.Builder {
        val enumBuilder = TypeSpec.enumBuilder(definition.name)

        val jsonValueType = definition.jsonValueType()
        val typeName = jsonValueType.asTypeName().copy(nullable = definition.allowsNull())

        enumBuilder.addKdoc(generateKDoc(definition))

        enumBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(
                    Identifier.PARAM_JSON_VALUE,
                    typeName
                )
                .build()
        )

        enumBuilder.addProperty(
            PropertySpec.builder(Identifier.PARAM_JSON_VALUE, typeName, KModifier.PRIVATE)
                .initializer(Identifier.PARAM_JSON_VALUE)
                .build()
        )

        val parameterFormat = if (definition.type == JsonType.NUMBER) "%L" else "%S"
        definition.values.forEach { value ->
            val enumValue = if (value == null) {
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("null")
                    .build()
            } else {
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter(parameterFormat, value)
                    .build()
            }
            enumBuilder.addEnumConstant(definition.enumConstantName(value), enumValue)
        }

        enumBuilder.addFunction(generateEnumSerializer(definition))

        enumBuilder.addType(generateCompanionObject(definition, rootTypeName))

        return enumBuilder
    }

    // endregion

    // region Internal

    private fun generateKDoc(definition: TypeDefinition.Enum): CodeBlock {
        val docBuilder = CodeBlock.builder()

        if (definition.description.isNotBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }
        return docBuilder.build()
    }

    private fun generateCompanionObject(
        definition: TypeDefinition.Enum,
        rootTypeName: String
    ): TypeSpec {
        return TypeSpec.companionObjectBuilder()
            .addFunction(generateEnumDeserializer(definition, rootTypeName))
            .build()
    }

    private fun generateEnumSerializer(definition: TypeDefinition.Enum): FunSpec {
        val funBuilder = FunSpec.builder(Identifier.FUN_TO_JSON)
            .returns(ClassNameRef.JsonElement)

        if (definition.allowsNull()) {
            funBuilder.beginControlFlow("if (%L == null)", Identifier.PARAM_JSON_VALUE)
            funBuilder.addStatement("return %T.%L", ClassNameRef.JsonNull, "INSTANCE")
            funBuilder.nextControlFlow("else")
        }

        funBuilder.addStatement(
            "return %T(%L)",
            ClassNameRef.JsonPrimitive,
            Identifier.PARAM_JSON_VALUE
        )

        if (definition.allowsNull()) {
            funBuilder.endControlFlow()
        }

        return funBuilder.build()
    }

    private fun generateEnumDeserializer(
        definition: TypeDefinition.Enum,
        rootTypeName: String
    ): FunSpec {
        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .addParameter(
                Identifier.PARAM_JSON_STR,
                String::class.asTypeName().copy(nullable = definition.allowsNull())
            )
            .returns(definition.asKotlinTypeName(rootTypeName))

        funBuilder.beginControlFlow("return values().first")
        funBuilder.addStatement(
            if (definition.type == JsonType.NUMBER) "it.%L.toString() == %L" else "it.%L == %L",
            Identifier.PARAM_JSON_VALUE,
            Identifier.PARAM_JSON_STR
        )
        funBuilder.endControlFlow()

        return funBuilder.build()
    }

    // endregion
}