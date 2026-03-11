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
    val oneOfPrimitiveOptionGenerator: KotlinSpecGenerator<TypeDefinition.Primitive, TypeSpec.Builder>,
    packageName: String,
    knownTypes: MutableSet<KotlinTypeWrapper>
) : TypeSpecGenerator<TypeDefinition.OneOfClass>(
    packageName,
    knownTypes
) {

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
                is TypeDefinition.OneOfClass.Option.Class -> {
                    val childType = it.cls.copy(parentType = definition)
                    val wrapper = childType.withUniqueTypeName(rootTypeName)
                    typeBuilder.addType(
                        classGenerator.generate(childType, rootTypeName).build()
                    )
                    wrapper.written = true
                }
                is TypeDefinition.OneOfClass.Option.Primitive -> {
                    val childType = it.primitive.copy(parentType = definition)
                    typeBuilder.addType(
                        oneOfPrimitiveOptionGenerator.generate(childType, rootTypeName).build()
                    )
                }
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

    @Suppress("FunctionMaxLength")
    private fun generateMultiClassStringDeserializer(
        definition: TypeDefinition.OneOfClass
    ): FunSpec {
        val returnType = ClassName.bestGuess(definition.name)

        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .throws(ClassNameRef.JsonParseException)
            .addParameter(Identifier.PARAM_JSON_STR, STRING)
            .returns(returnType)

        funBuilder.beginControlFlow("try")

        funBuilder.addStatement(
            "val %L = %T.parseString(%L)",
            Identifier.PARAM_JSON_ELEMENT,
            ClassNameRef.JsonParser,
            Identifier.PARAM_JSON_STR
        )
        funBuilder.addStatement(
            "return %L(%L)",
            Identifier.FUN_FROM_JSON_ELEMENT,
            Identifier.PARAM_JSON_ELEMENT
        )

        funBuilder.nextControlFlow(
            "catch (%L: %T)",
            Identifier.CAUGHT_EXCEPTION,
            ClassNameRef.IllegalStateException
        )
        funBuilder.addStatement("throw %T(", ClassNameRef.JsonParseException)
        funBuilder.addStatement("    \"$PARSE_ERROR_MSG_ONE_OF_TYPE %T\",", returnType)
        funBuilder.addStatement("    %L", Identifier.CAUGHT_EXCEPTION)
        funBuilder.addStatement(")")
        funBuilder.endControlFlow()

        return funBuilder.build()
    }

    private fun generateMultiClassDeserializer(
        definition: TypeDefinition.OneOfClass,
        rootTypeName: String
    ): FunSpec {
        val returnType = definition.asKotlinTypeName(rootTypeName)
        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON_ELEMENT)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .throws(ClassNameRef.JsonParseException)
            .addParameter(Identifier.PARAM_JSON_ELEMENT, ClassNameRef.JsonElement)
            .returns(returnType)

        // create error variable
        funBuilder.addStatement("val errors = mutableListOf<Throwable>()")

        // try to parse against all possible types
        val options = mutableListOf<String>()
        definition.options.forEach {
            val typeName = it.asKotlinTypeName(rootTypeName, definition)
            val variableName = "as${it.name()}"
            funBuilder.beginControlFlow("val %L = try", variableName)
            when (it) {
                is TypeDefinition.OneOfClass.Option.Class -> {
                    funBuilder.beginControlFlow(
                        "if (%L is %T)",
                        Identifier.PARAM_JSON_ELEMENT,
                        ClassNameRef.JsonObject
                    )
                    funBuilder.addStatement(
                        "%T.%L(%L)",
                        typeName,
                        Identifier.FUN_FROM_JSON_OBJ,
                        Identifier.PARAM_JSON_ELEMENT
                    )
                    funBuilder.nextControlFlow("else")
                    funBuilder.addStatement(
                        "throw %T(\"$PARSE_ERROR_MSG_TYPE \"\n + \"%T\")",
                        ClassNameRef.JsonParseException,
                        it.cls.asKotlinTypeName(rootTypeName)
                    )
                }
                is TypeDefinition.OneOfClass.Option.Primitive -> {
                    funBuilder.beginControlFlow(
                        "if (%L is %T)",
                        Identifier.PARAM_JSON_ELEMENT,
                        ClassNameRef.JsonPrimitive
                    )
                    funBuilder.addStatement(
                        "%T.%L(%L)",
                        typeName,
                        Identifier.FUN_FROM_JSON_PRIMITIVE,
                        Identifier.PARAM_JSON_ELEMENT
                    )
                    funBuilder.nextControlFlow("else")
                    funBuilder.addStatement(
                        "throw %T(\"$PARSE_ERROR_MSG_TYPE \"\n + \"%T\")",
                        ClassNameRef.JsonParseException,
                        it.primitive.asKotlinTypeName(rootTypeName)
                    )
                }
            }
            funBuilder.endControlFlow()

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
        funBuilder.addStatement("val message = \"$PARSE_ERROR_MSG_ONE_OF_TYPE \\n\" + \"%T\\n\" +", returnType)
        funBuilder.addStatement("    errors.joinToString(\"\\n\") { it.message.toString() }")
        funBuilder.addStatement("throw %T(message)", ClassNameRef.JsonParseException)
        funBuilder.endControlFlow()
        funBuilder.addStatement("return result")

        return funBuilder.build()
    }

    // endregion

    companion object {
        private const val PARSE_ERROR_MSG_ONE_OF_TYPE = "Unable to parse json into one of type"
        private const val PARSE_ERROR_MSG_TYPE = "Unable to parse json into type"
    }
}
