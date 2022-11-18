/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.datadog.gradle.plugin.jsonschema.variableName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.jvm.throws

class ClassJsonElementDeserializerGenerator(
    packageName: String,
    knownTypes: MutableSet<KotlinTypeWrapper>
) : KotlinSpecGenerator<TypeDefinition.Class, FunSpec>(
    packageName,
    knownTypes
) {

    override fun generate(definition: TypeDefinition.Class, rootTypeName: String): FunSpec {
        val isConstantClass = definition.isConstantClass()
        val returnType = ClassName.bestGuess(definition.name)

        val funBuilder = FunSpec.builder(Identifier.FUN_FROM_JSON_OBJ)
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .returns(returnType)

        if (!isConstantClass) {
            funBuilder.throws(ClassNameRef.JsonParseException)
            funBuilder.addParameter(Identifier.PARAM_JSON_OBJ, ClassNameRef.JsonObject)
            funBuilder.beginControlFlow("try")
        }

        funBuilder.appendDeserializerFunctionBlock(definition, rootTypeName)

        if (!isConstantClass) {
            caughtExceptions.forEach {
                funBuilder.nextControlFlow(
                    "catch (%L: %T)",
                    Identifier.CAUGHT_EXCEPTION,
                    it
                )
                funBuilder.addStatement("throw %T(", ClassNameRef.JsonParseException)
                funBuilder.addStatement("    \"$PARSE_ERROR_MSG %T\",", returnType)
                funBuilder.addStatement("    %L", Identifier.CAUGHT_EXCEPTION)
                funBuilder.addStatement(")")
            }
            funBuilder.endControlFlow()
        }
        return funBuilder.build()
    }

    // region Internal

    private fun FunSpec.Builder.appendDeserializerFunctionBlock(
        definition: TypeDefinition.Class,
        rootTypeName: String
    ) {
        val nonConstantProperties = definition.properties.filter {
            it.type !is TypeDefinition.Constant
        }

        nonConstantProperties.forEach { p ->
            appendDeserializedProperty(
                propertyType = p.type,
                assignee = "val ${p.name.variableName()}",
                getter = "${Identifier.PARAM_JSON_OBJ}.get(\"${p.name}\")",
                nullable = p.optional,
                rootTypeName = rootTypeName
            )
        }

        definition.additionalProperties?.let {
            appendAdditionalPropertiesDeserialization(
                it,
                definition.properties.isNotEmpty(),
                rootTypeName
            )
        }

        val arguments = nonConstantProperties.map { it.name.variableName() } +
            definition.additionalProperties?.let { Identifier.PARAM_ADDITIONAL_PROPS }
        val constructorArguments = arguments.filterNotNull().joinToString(", ")
        addStatement("return %L($constructorArguments)", definition.name)
    }

    private fun FunSpec.Builder.appendDeserializedProperty(
        propertyType: TypeDefinition,
        assignee: String,
        getter: String,
        nullable: Boolean,
        rootTypeName: String
    ) {
        when (propertyType) {
            is TypeDefinition.Null -> addStatement("$assignee = null")
            is TypeDefinition.Primitive -> addStatement(
                "$assignee = $getter${if (nullable) "?" else ""}.${propertyType.asPrimitiveTypeFun()}"
            )
            is TypeDefinition.Array -> appendArrayDeserialization(
                propertyType,
                assignee,
                getter,
                nullable,
                rootTypeName
            )
            is TypeDefinition.Class -> {
                if (propertyType.isConstantClass()) {
                    appendDeserializationForConstantProperty(
                        propertyType,
                        assignee,
                        getter,
                        nullable,
                        rootTypeName
                    )
                } else {
                    appendObjectDeserialization(
                        propertyType,
                        assignee,
                        getter,
                        nullable,
                        rootTypeName
                    )
                }
            }
            is TypeDefinition.OneOfClass -> {
                appendObjectDeserialization(
                    propertyType,
                    assignee,
                    getter,
                    nullable,
                    rootTypeName
                )
            }
            is TypeDefinition.Enum -> appendEnumDeserialization(
                propertyType,
                assignee,
                getter,
                nullable,
                rootTypeName
            )
            is TypeDefinition.Constant -> {
                // No Op
            }
        }
    }

    private fun FunSpec.Builder.appendArrayDeserialization(
        arrayType: TypeDefinition.Array,
        assignee: String,
        getter: String,
        nullable: Boolean,
        rootTypeName: String
    ) {
        val opt = if (nullable) "?" else ""
        beginControlFlow(
            "$assignee = $getter$opt.asJsonArray$opt.let·{·%L·->",
            Identifier.PARAM_JSON_ARRAY
        )
        val collectionClassName: ClassName = if (arrayType.uniqueItems) {
            ClassNameRef.MutableSet
        } else {
            ClassNameRef.MutableList
        }
        addStatement(
            "val %L = %T(%L.size())",
            Identifier.PARAM_COLLECTION,
            collectionClassName.parameterizedBy(
                arrayType.items.asKotlinTypeName(rootTypeName)
            ),
            Identifier.PARAM_JSON_ARRAY
        )
        beginControlFlow("%L.forEach", Identifier.PARAM_JSON_ARRAY)
        appendArrayItemDeserialization(arrayType, rootTypeName)

        endControlFlow()
        addStatement("%L", Identifier.PARAM_COLLECTION)
        endControlFlow()
    }

    private fun FunSpec.Builder.appendArrayItemDeserialization(
        arrayType: TypeDefinition.Array,
        rootTypeName: String
    ) {
        when (arrayType.items) {
            is TypeDefinition.Primitive -> addStatement(
                "%L.add(it.${arrayType.items.asPrimitiveTypeFun()})",
                Identifier.PARAM_COLLECTION
            )
            is TypeDefinition.OneOfClass,
            is TypeDefinition.Class -> addStatement(
                "%L.add(%T.%L(it.asJsonObject))",
                Identifier.PARAM_COLLECTION,
                arrayType.items.asKotlinTypeName(rootTypeName),
                Identifier.FUN_FROM_JSON_OBJ
            )
            is TypeDefinition.Enum -> addStatement(
                "%L.add(%T.%L(it.asString))",
                Identifier.PARAM_COLLECTION,
                arrayType.items.asKotlinTypeName(rootTypeName),
                Identifier.FUN_FROM_JSON
            )
            else -> throw IllegalStateException(
                "Unable to deserialize an array of ${arrayType.items}"
            )
        }
    }

    private fun FunSpec.Builder.appendDeserializationForConstantProperty(
        propertyType: TypeDefinition.Class,
        assignee: String,
        getter: String,
        nullable: Boolean,
        rootTypeName: String
    ) {
        if (nullable) {
            val opt = if (nullable) "?" else ""
            beginControlFlow("$assignee = $getter$opt.toString()$opt.let")
            addStatement(
                "%T()",
                propertyType.asKotlinTypeName(rootTypeName)
            )
            endControlFlow()
        } else {
            addStatement(
                "%L = %T()",
                assignee,
                propertyType.asKotlinTypeName(rootTypeName)
            )
        }
    }

    private fun FunSpec.Builder.appendObjectDeserialization(
        propertyType: TypeDefinition.Class,
        assignee: String,
        getter: String,
        nullable: Boolean,
        rootTypeName: String
    ) {
        val opt = if (nullable) "?" else ""
        beginControlFlow("$assignee = $getter$opt.asJsonObject$opt.let")
        val codeBlockFormat = if (propertyType.isConstantClass()) {
            "%T.%L()"
        } else {
            "%T.%L(it)"
        }
        addStatement(
            codeBlockFormat,
            propertyType.asKotlinTypeName(rootTypeName),
            if (propertyType.isConstantClass()) Identifier.FUN_FROM_JSON else Identifier.FUN_FROM_JSON_OBJ
        )
        endControlFlow()
    }

    private fun FunSpec.Builder.appendObjectDeserialization(
        propertyType: TypeDefinition.OneOfClass,
        assignee: String,
        getter: String,
        nullable: Boolean,
        rootTypeName: String
    ) {
        val opt = if (nullable) "?" else ""
        beginControlFlow("$assignee = $getter$opt.asJsonObject$opt.let")

        addStatement(
            "%T.%L(it)",
            propertyType.asKotlinTypeName(rootTypeName),
            Identifier.FUN_FROM_JSON_OBJ
        )
        endControlFlow()
    }

    private fun FunSpec.Builder.appendEnumDeserialization(
        propertyType: TypeDefinition.Enum,
        assignee: String,
        getter: String,
        nullable: Boolean,
        rootTypeName: String
    ) {
        if (propertyType.allowsNull()) {
            val elementName = "json${propertyType.name.variableName()}"
            addStatement("val $elementName = $getter")
            beginControlFlow("$assignee = if ($elementName is %T || $elementName == null)", ClassNameRef.JsonNull)
            addStatement(
                "%T.%L(null)",
                propertyType.asKotlinTypeName(rootTypeName),
                Identifier.FUN_FROM_JSON
            )
            nextControlFlow("else")
            addStatement(
                "%T.%L($elementName.asString)",
                propertyType.asKotlinTypeName(rootTypeName),
                Identifier.FUN_FROM_JSON
            )
            endControlFlow()
        } else if (nullable) {
            beginControlFlow("$assignee = $getter?.asString?.let")
            addStatement(
                "%T.%L(it)",
                propertyType.asKotlinTypeName(rootTypeName),
                Identifier.FUN_FROM_JSON
            )
            endControlFlow()
        } else {
            addStatement(
                "$assignee = %T.%L($getter.asString)",
                propertyType.asKotlinTypeName(rootTypeName),
                Identifier.FUN_FROM_JSON
            )
        }
    }

    private fun FunSpec.Builder.appendAdditionalPropertiesDeserialization(
        additionalProperties: TypeDefinition,
        hasKnownProperties: Boolean,
        rootTypeName: String
    ) {
        addStatement(
            "val %L = mutableMapOf<%T, %T>()",
            Identifier.PARAM_ADDITIONAL_PROPS,
            STRING,
            additionalProperties.additionalPropertyTypeName(rootTypeName)
        )
        beginControlFlow(
            "for (entry in %L.entrySet())",
            Identifier.PARAM_JSON_OBJ
        )

        if (hasKnownProperties) {
            beginControlFlow(
                "if (entry.key !in %L)",
                Identifier.PARAM_RESERVED_PROPS
            )
        }

        if (additionalProperties is TypeDefinition.Class) {
            addStatement(
                "%L[entry.key] = entry.value",
                Identifier.PARAM_ADDITIONAL_PROPS
            )
        } else {
            appendDeserializedProperty(
                propertyType = additionalProperties,
                assignee = "${Identifier.PARAM_ADDITIONAL_PROPS}[entry.key]",
                getter = "entry.value",
                nullable = false,
                rootTypeName = rootTypeName
            )
        }

        if (hasKnownProperties) {
            endControlFlow()
        }
        endControlFlow()
    }

    // endregion

    companion object {
        private const val PARSE_ERROR_MSG = "Unable to parse json into type"

        private val caughtExceptions = arrayOf(
            ClassNameRef.IllegalStateException,
            ClassNameRef.NumberFormatException,
            ClassNameRef.NullPointerException
        )
    }
}
