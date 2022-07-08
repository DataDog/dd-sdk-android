/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.jvm.throws

class MultiClassGenerator(
    packageName: String,
    nestedTypes: MutableSet<TypeDefinition>,
    knownTypeNames: MutableSet<String>
) : TypeSpecGenerator<TypeDefinition.MultiClass>(
    packageName,
    nestedTypes,
    knownTypeNames
) {

    //region TypeSpecGenerator

    override fun generate(
        definition: TypeDefinition.MultiClass,
        rootTypeName: String
    ): TypeSpec.Builder {
        val typeBuilder = TypeSpec.classBuilder(definition.name)
            .addModifiers(KModifier.SEALED)
        knownTypeNames.add(definition.name)

        if (definition.description.isNotBlank()) {
            val docBuilder = CodeBlock.builder()
            docBuilder.add(definition.description)
            docBuilder.add("\n")
            typeBuilder.addKdoc(docBuilder.build())
        }

        definition.options.forEach {
            val updatedType = when (it) {
                is TypeDefinition.Class -> it.copy(parentType = definition)
                else -> TODO("Can't have type $it as child of a `one_of` block")
            }
            nestedTypes.add(updatedType)
        }

        typeBuilder.addFunction(generateMultiClassSerializer())

        typeBuilder.addType(generateCompanionObject(definition, rootTypeName))

        return typeBuilder
    }

    // endregion

    // region Internal

    private fun generateMultiClassSerializer(): FunSpec {
        return FunSpec.builder(Identifier.FUN_TO_JSON)
            .addModifiers(KModifier.ABSTRACT)
            .returns(ClassNameRef.JsonElement).build()
    }

    private fun generateCompanionObject(
        definition: TypeDefinition.MultiClass,
        rootTypeName: String
    ): TypeSpec {
        return TypeSpec.companionObjectBuilder()
            .addFunction(generateMultiClassDeserializer(definition, rootTypeName))
            .build()
    }

    private fun generateMultiClassDeserializer(
        definition: TypeDefinition.MultiClass,
        rootTypeName: String
    ): FunSpec {
        val returnType = definition.asKotlinTypeName(rootTypeName)
        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .throws(ClassNameRef.JsonParseException)
            .addParameter(Identifier.PARAM_JSON_STR, String::class)
            .returns(returnType)

        // create error variable
        funBuilder.addStatement("val errors = mutableListOf<Throwable>()")

        // try to parse against all possible types
        val options = mutableListOf<String>()
        definition.options.forEach {
            val typeName = it.asKotlinTypeName(rootTypeName)
            val variableName = "as${it.name()}"
            funBuilder.beginControlFlow("val %L = try", variableName)
            funBuilder.addStatement(
                "%T.%L(%L)",
                typeName,
                Identifier.FUN_FROM_JSON,
                Identifier.PARAM_JSON_STR
            )
            funBuilder.nextControlFlow(
                "catch (%L: %T)",
                Identifier.CAUGHT_EXCEPTION,
                ClassNameRef.JsonParseException
            )
            funBuilder.addStatement(
                "errors.add(%L)",
                Identifier.CAUGHT_EXCEPTION
            )
            funBuilder.addStatement("null")
            funBuilder.endControlFlow()
            options.add(variableName)
        }

        funBuilder.addStatement(
            "val result = arrayOf(${options.joinToString(", ")}).firstOrNull { it != null }"
        )
        funBuilder.beginControlFlow("if (result == null)")
        funBuilder.addStatement("val message = \"$PARSE_ERROR_MSG %T\"", returnType)
        funBuilder.addStatement("throw %T(message, errors[0])", ClassNameRef.JsonParseException)
        funBuilder.endControlFlow()
        funBuilder.addStatement("return result")

        return funBuilder.build()
    }

    // endregion

    companion object {
        private const val PARSE_ERROR_MSG = "Unable to parse json into one of type"
    }
}