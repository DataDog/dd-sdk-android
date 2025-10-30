/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.JsonPrimitiveType
import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.datadog.gradle.plugin.jsonschema.nameString
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.jvm.throws

class OneOfPrimitiveOptionGenerator(
    packageName: String
) : TypeSpecGenerator<TypeDefinition.Primitive>(
    packageName,
    mutableSetOf()
) {
    override fun generate(
        definition: TypeDefinition.Primitive,
        rootTypeName: String
    ): TypeSpec.Builder {
        val className = definition.type.nameString()
        val typeBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)

        if (definition.parentType != null) {
            typeBuilder.superclass(definition.parentType.asKotlinTypeName(rootTypeName))
        }

        if (definition.description.isNotBlank()) {
            val docBuilder = CodeBlock.builder()
            docBuilder.add(definition.description)
            docBuilder.add("\n")
            typeBuilder.addKdoc(docBuilder.build())
        }

        val valueType = definition.asKotlinTypeName(rootTypeName)

        typeBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder("item", valueType).build())
                .build()
        )

        typeBuilder.addProperty(
            PropertySpec.builder("item", valueType)
                .initializer("item")
                .build()
        )

        typeBuilder.addFunction(
            FunSpec.builder(Identifier.FUN_TO_JSON)
                .apply { if (definition.parentType != null) addModifiers(KModifier.OVERRIDE) }
                .returns(ClassNameRef.JsonElement)
                .addStatement("return %T(item)", ClassNameRef.JsonPrimitive)
                .build()
        )

        typeBuilder.addType(
            TypeSpec.companionObjectBuilder()
                .addFunction(generateStringDeserializer(className))
                .addFunction(generateElementDeserializer(className, definition))
                .build()
        )

        return typeBuilder
    }

    private fun generateStringDeserializer(className: String): FunSpec {
        val returnType = ClassName.bestGuess(className)
        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .throws(ClassNameRef.JsonParseException)
            .addParameter(Identifier.PARAM_JSON_STR, STRING)
            .returns(returnType)

        funBuilder.addStatement(
            "val %L = %T.parseString(%L)",
            Identifier.PARAM_JSON_OBJ,
            ClassNameRef.JsonParser,
            Identifier.PARAM_JSON_STR
        )
        funBuilder.beginControlFlow("try")
        funBuilder.addStatement(
            "return %L(%L.asJsonPrimitive)",
            Identifier.FUN_FROM_JSON_OBJ,
            Identifier.PARAM_JSON_OBJ
        )
        funBuilder.nextControlFlow("catch (%L: %T)", Identifier.CAUGHT_EXCEPTION, ClassNameRef.IllegalStateException)
        funBuilder.addStatement(
            "throw %T(\"$PARSE_ERROR_MSG %T\", %L)",
            ClassNameRef.JsonParseException,
            returnType,
            Identifier.CAUGHT_EXCEPTION
        )
        funBuilder.endControlFlow()

        return funBuilder.build()
    }

    private fun generateElementDeserializer(className: String, definition: TypeDefinition.Primitive): FunSpec {
        val returnType = ClassName.bestGuess(className)
        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON_OBJ).apply {
            addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            throws(ClassNameRef.JsonParseException)
            addParameter(Identifier.PARAM_JSON_OBJ, ClassNameRef.JsonPrimitive)
            returns(returnType)
        }

        funBuilder.beginControlFlow("try")

        val typeInfo = definition.type.typeInfo()

        funBuilder.beginControlFlow(
            "if (%L.${typeInfo.isFuncName})",
            Identifier.PARAM_JSON_OBJ
        )
        funBuilder.addStatement(
            "return %T(%L.${typeInfo.asFuncName})",
            returnType,
            Identifier.PARAM_JSON_OBJ
        )
        funBuilder.nextControlFlow("else")
        funBuilder.addStatement(
            "throw %T(\"Can't convert %L to ${typeInfo.typeName}\")",
            ClassNameRef.JsonParseException,
            Identifier.PARAM_JSON_OBJ
        )
        funBuilder.endControlFlow()

        definition.type.caughtExceptions()
            .forEach { exception ->
                funBuilder.nextControlFlow("catch (%L: %T)", Identifier.CAUGHT_EXCEPTION, exception)
                funBuilder.addStatement(
                    "throw %T(\"$PARSE_ERROR_MSG %T\", %L)",
                    ClassNameRef.JsonParseException,
                    returnType,
                    Identifier.CAUGHT_EXCEPTION
                )
            }

        funBuilder.endControlFlow()

        return funBuilder.build()
    }

    companion object {
        private const val PARSE_ERROR_MSG = "Unable to parse json into type"

        private fun JsonPrimitiveType.caughtExceptions(): List<ClassName> {
            return when (this) {
                JsonPrimitiveType.STRING,
                JsonPrimitiveType.BOOLEAN -> listOf(
                    ClassNameRef.IllegalStateException,
                    ClassNameRef.UnsupportedOperationException
                )
                JsonPrimitiveType.DOUBLE -> error("Double is not supported")
                JsonPrimitiveType.INTEGER,
                JsonPrimitiveType.NUMBER -> listOf(
                    ClassNameRef.IllegalStateException,
                    ClassNameRef.NumberFormatException,
                    ClassNameRef.UnsupportedOperationException
                )
            }
        }

        private class TypeInfo(
            val asFuncName: String,
            val isFuncName: String,
            val typeName: String
        )

        private fun JsonPrimitiveType.typeInfo(): TypeInfo {
            return when (this) {
                JsonPrimitiveType.STRING -> TypeInfo(
                    asFuncName = "asString",
                    isFuncName = "isString",
                    typeName = "String"
                )
                JsonPrimitiveType.BOOLEAN -> TypeInfo(
                    asFuncName = "asBoolean",
                    isFuncName = "isBoolean",
                    typeName = "Boolean"
                )
                JsonPrimitiveType.INTEGER -> TypeInfo(
                    asFuncName = "asLong",
                    isFuncName = "isNumber",
                    typeName = "Long"
                )
                JsonPrimitiveType.DOUBLE -> error("Double is not supported")
                JsonPrimitiveType.NUMBER -> TypeInfo(
                    asFuncName = "asNumber",
                    isFuncName = "isNumber",
                    typeName = "Number"
                )
            }
        }
    }
}
