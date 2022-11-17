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
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.jvm.throws

class ClassStringDeserializerGenerator(
    packageName: String,
    knownTypes: MutableSet<KotlinTypeWrapper>
) : KotlinSpecGenerator<TypeDefinition.Class, FunSpec>(
    packageName,
    knownTypes
) {
    override fun generate(definition: TypeDefinition.Class, rootTypeName: String): FunSpec {
        val isConstantClass = definition.isConstantClass()
        val returnType = ClassName.bestGuess(definition.name)

        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .returns(returnType)

        if (!isConstantClass) {
            funBuilder.throws(ClassNameRef.JsonParseException)
            funBuilder.addParameter(Identifier.PARAM_JSON_STR, STRING)
        }

        funBuilder.addStatement(
            "val %L = %T.parseString(%L).asJsonObject",
            Identifier.PARAM_JSON_OBJ,
            ClassNameRef.JsonParser,
            Identifier.PARAM_JSON_STR
        )
        funBuilder.addStatement("return %L(%L)", Identifier.FUN_FROM_JSON_ELT, Identifier.PARAM_JSON_OBJ)

        return funBuilder.build()
    }
}
