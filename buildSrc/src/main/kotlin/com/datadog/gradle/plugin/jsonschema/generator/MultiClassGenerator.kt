/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.jvm.throws

class MultiClassGenerator(
    val classGenerator: KotlinSpecGenerator<TypeDefinition.Class, TypeSpec.Builder>,
    packageName: String,
    knownTypes: MutableSet<KotlinTypeWrapper>
) : TypeSpecGenerator<TypeDefinition.OneOfClass>(
    packageName,
    knownTypes
) {
    private val stringDeserializer = ClassStringDeserializerGenerator(packageName, knownTypes)

    //region TypeSpecGenerator

    override fun generate(
        definition: TypeDefinition.OneOfClass,
        rootTypeName: String
    ): TypeSpec.Builder {
        val typeBuilder = TypeSpec.classBuilder(definition.name)
            .addModifiers(KModifier.SEALED)

        if (definition.description.isNotBlank()) {
            val docBuilder = CodeBlock.builder()
            docBuilder.add(definition.description)
            docBuilder.add("\n")
            typeBuilder.addKdoc(docBuilder.build())
        }

        definition.options.forEach {
            when (it) {
                is TypeDefinition.Class -> {
                    val childType = it.copy(parentType = definition)
                    val wrapper = childType.withUniqueTypeName(rootTypeName)
                    typeBuilder.addType(
                        classGenerator.generate(childType, rootTypeName).build()
                    )
                    wrapper.written = true
                }
                else -> throw IllegalStateException(
                    "Can't have type $it as child of a `one_of` block"
                )
            }
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
        definition: TypeDefinition.OneOfClass,
        rootTypeName: String
    ): TypeSpec {
        return TypeSpec.companionObjectBuilder()
            .addFunction(generateMultiClassStringDeserializer(definition))
            .addFunction(generateMultiClassDeserializer(definition, rootTypeName))
            .build()
    }

    private fun generateMultiClassStringDeserializer(
        definition: TypeDefinition.OneOfClass
    ): FunSpec {
        val returnType = ClassName.bestGuess(definition.name)

        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .throws(ClassNameRef.JsonParseException)
            .addParameter(Identifier.PARAM_JSON_STR, STRING)
            .returns(returnType)

        funBuilder.addStatement(
            "val %L = %T.parseString(%L).asJsonObject",
            Identifier.PARAM_JSON_OBJ,
            ClassNameRef.JsonParser,
            Identifier.PARAM_JSON_STR
        )
        funBuilder.addStatement("return %L(%L)", Identifier.FUN_FROM_JSON_ELT, Identifier.PARAM_JSON_OBJ)

        return funBuilder.build()
    }

    private fun generateMultiClassDeserializer(
        definition: TypeDefinition.OneOfClass,
        rootTypeName: String
    ): FunSpec {
        val returnType = definition.asKotlinTypeName(rootTypeName)
        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON_ELT)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .throws(ClassNameRef.JsonParseException)
            .addParameter(Identifier.PARAM_JSON_OBJ, ClassNameRef.JsonObject)
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
                Identifier.FUN_FROM_JSON_ELT,
                Identifier.PARAM_JSON_OBJ
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

        funBuilder.addStatement("val result = arrayOf(")
        options.forEach { funBuilder.addStatement("    $it,") }
        funBuilder.addStatement(").firstOrNull { it != null }")

        funBuilder.beginControlFlow("if (result == null)")
        funBuilder.addStatement("val message = \"$PARSE_ERROR_MSG \\n\" + \"%T\\n\" +", returnType)
        funBuilder.addStatement("    errors.joinToString(\"\\n\") { it.message.toString() }")
        funBuilder.addStatement("throw %T(message)", ClassNameRef.JsonParseException)
        funBuilder.endControlFlow()
        funBuilder.addStatement("return result")

        return funBuilder.build()
    }

    // endregion

    companion object {
        private const val PARSE_ERROR_MSG = "Unable to parse json into one of type"
    }
}
