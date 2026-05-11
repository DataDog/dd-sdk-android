/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.jvm.throws

/**
 * Generates a wrapper Kotlin class for a JSON schema whose root type is an array. The wrapper holds
 * the array as a single `items` property and serializes/deserializes directly as a JSON array, not
 * an object. The array items must be a single object type ([TypeDefinition.Class]); other item
 * shapes (oneOf/anyOf, primitives) are not supported as top-level arrays.
 */
class ArrayWrapperClassGenerator(
    packageName: String,
    knownTypes: MutableSet<KotlinTypeWrapper>
) : TypeSpecGenerator<TypeDefinition.Array>(
    packageName,
    knownTypes
) {

    override fun generate(
        definition: TypeDefinition.Array,
        rootTypeName: String
    ): TypeSpec.Builder {
        val itemsClass = definition.items
        check(itemsClass is TypeDefinition.Class) {
            "Top-level array schema must have items of a single object type, " +
                "but got ${itemsClass::class.simpleName}. " +
                "Multiple item types (oneOf/anyOf/primitives) are not supported as top-level arrays."
        }

        // Registers the items class as a known nested type so FileGenerator's
        // nested-class loop emits it inside the wrapper.
        val itemTypeName = itemsClass.asKotlinTypeName(rootTypeName)
        val collectionTypeName = if (definition.uniqueItems) {
            SET.parameterizedBy(itemTypeName)
        } else {
            LIST.parameterizedBy(itemTypeName)
        }

        val typeBuilder = TypeSpec.classBuilder(rootTypeName)
            .addModifiers(KModifier.DATA)

        if (definition.description.isNotBlank()) {
            typeBuilder.addKdoc(definition.description + "\n")
        }

        typeBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder(PROPERTY_ITEMS, collectionTypeName).build())
                .build()
        )
        typeBuilder.addProperty(
            PropertySpec.builder(PROPERTY_ITEMS, collectionTypeName)
                .mutable(false)
                .initializer(PROPERTY_ITEMS)
                .build()
        )

        typeBuilder.addFunction(generateToJson())
        typeBuilder.addType(generateCompanion(definition, rootTypeName))

        return typeBuilder
    }

    private fun generateToJson(): FunSpec {
        return FunSpec.builder(Identifier.FUN_TO_JSON)
            .returns(ClassNameRef.JsonElement)
            .addStatement(
                "val %L = %T(%L.size)",
                Identifier.PARAM_JSON_ARRAY,
                ClassNameRef.JsonArray,
                PROPERTY_ITEMS
            )
            .addStatement(
                "%L.forEach { %L.add(it.%L()) }",
                PROPERTY_ITEMS,
                Identifier.PARAM_JSON_ARRAY,
                Identifier.FUN_TO_JSON
            )
            .addStatement("return %L", Identifier.PARAM_JSON_ARRAY)
            .build()
    }

    private fun generateCompanion(
        definition: TypeDefinition.Array,
        rootTypeName: String
    ): TypeSpec {
        return TypeSpec.companionObjectBuilder()
            .addFunction(generateFromJsonString(rootTypeName))
            .addFunction(generateFromJsonElement(definition, rootTypeName))
            .build()
    }

    private fun generateFromJsonString(rootTypeName: String): FunSpec {
        val returnType = ClassName.bestGuess(rootTypeName)
        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .returns(returnType)

        funBuilder.throws(ClassNameRef.JsonParseException)
        funBuilder.addParameter(Identifier.PARAM_JSON_STR, STRING)

        funBuilder.wrapInDeserializerTryCatch(returnType, STRING_DESERIALIZER_EXCEPTIONS) {
            addStatement(
                "val %L = %T.parseString(%L).asJsonArray",
                Identifier.PARAM_JSON_ARRAY,
                ClassNameRef.JsonParser,
                Identifier.PARAM_JSON_STR
            )
            addStatement(
                "return %L(%L)",
                Identifier.FUN_FROM_JSON_ELEMENT,
                Identifier.PARAM_JSON_ARRAY
            )
        }

        return funBuilder.build()
    }

    private fun generateFromJsonElement(
        definition: TypeDefinition.Array,
        rootTypeName: String
    ): FunSpec {
        val returnType = ClassName.bestGuess(rootTypeName)
        val itemTypeName = definition.items.asKotlinTypeName(rootTypeName)
        val collectionClass = if (definition.uniqueItems) {
            ClassNameRef.MutableSet
        } else {
            ClassNameRef.MutableList
        }

        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON_ELEMENT)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .returns(returnType)

        funBuilder.throws(ClassNameRef.JsonParseException)
        funBuilder.addParameter(Identifier.PARAM_JSON_ELEMENT, ClassNameRef.JsonElement)

        funBuilder.wrapInDeserializerTryCatch(returnType, ELEMENT_DESERIALIZER_EXCEPTIONS) {
            addStatement(
                "val %L = %L.asJsonArray",
                Identifier.PARAM_JSON_ARRAY,
                Identifier.PARAM_JSON_ELEMENT
            )
            addStatement(
                "val %L = %T(%L.size())",
                Identifier.PARAM_COLLECTION,
                collectionClass.parameterizedBy(itemTypeName),
                Identifier.PARAM_JSON_ARRAY
            )
            beginControlFlow("%L.forEach", Identifier.PARAM_JSON_ARRAY)
            addStatement(
                "%L.add(%T.%L(it.asJsonObject))",
                Identifier.PARAM_COLLECTION,
                itemTypeName,
                Identifier.FUN_FROM_JSON_OBJ
            )
            endControlFlow()
            addStatement("return %L(%L)", rootTypeName, Identifier.PARAM_COLLECTION)
        }

        return funBuilder.build()
    }

    companion object {
        private const val PROPERTY_ITEMS = "items"
    }
}
