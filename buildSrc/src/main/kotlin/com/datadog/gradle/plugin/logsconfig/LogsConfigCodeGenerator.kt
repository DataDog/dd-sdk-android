/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.gradle.plugin.logsconfig

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import java.io.File

internal class LogsConfigCodeGenerator(
    private val packageName: String
) {

    fun generate(config: LogsConfig, outputDir: File) {
        for (entry in config.logs) {
            generateLogEntry(entry, outputDir)
        }
    }

    private fun generateLogEntry(entry: LogEntry, outputDir: File) {
        val containerName = toPascalCase(entry.id) + "Log"
        val enclosingNames = listOf(containerName)

        val containerObject = TypeSpec.objectBuilder(containerName)
            .addModifiers(KModifier.INTERNAL)

        val nonConstProperties = entry.properties.filter { (_, prop) ->
            prop !is PropertyDefinition.Const
        }

        for ((propName, propDef) in nonConstProperties) {
            val nestedType = generateTypeSpec(propName, propDef, enclosingNames)
            if (nestedType != null) {
                containerObject.addType(nestedType)
            }
        }

        val extensionFun = generateExtensionFunction(entry, enclosingNames, nonConstProperties)

        val fileSpec = FileSpec.builder(packageName, containerName)
            .addType(containerObject.build())
            .addFunction(extensionFun)
            .addFunction(generatePutNonNullHelper())
            .indent("    ")
            .build()

        fileSpec.writeTo(outputDir)
    }

    private fun generateTypeSpec(
        propertyName: String,
        propDef: PropertyDefinition,
        enclosingNames: List<String>
    ): TypeSpec? {
        return when (propDef) {
            is PropertyDefinition.ObjectDef -> generateDataClass(propertyName, propDef, enclosingNames)
            is PropertyDefinition.EnumDef -> generateEnumClass(propertyName, propDef)
            is PropertyDefinition.Primitive,
            is PropertyDefinition.Const -> null
        }
    }

    private fun generateDataClass(
        name: String,
        objDef: PropertyDefinition.ObjectDef,
        enclosingNames: List<String>
    ): TypeSpec {
        val className = toPascalCase(name)
        val childEnclosingNames = enclosingNames + className
        val classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.INTERNAL, KModifier.DATA)

        val constructorBuilder = FunSpec.constructorBuilder()

        for ((propName, propDef) in objDef.properties) {
            if (propDef is PropertyDefinition.Const) continue

            val kotlinName = toCamelCase(propName)
            val typeName = resolveTypeName(propName, propDef, childEnclosingNames)
            val finalType = if (propDef.nullable) typeName.copy(nullable = true) else typeName

            constructorBuilder.addParameter(
                ParameterSpec.builder(kotlinName, finalType).build()
            )
            classBuilder.addProperty(
                PropertySpec.builder(kotlinName, finalType)
                    .initializer(kotlinName)
                    .build()
            )
        }

        classBuilder.primaryConstructor(constructorBuilder.build())

        for ((propName, propDef) in objDef.properties) {
            if (propDef is PropertyDefinition.Const) continue
            val nestedType = generateTypeSpec(propName, propDef, childEnclosingNames)
            if (nestedType != null) {
                classBuilder.addType(nestedType)
            }
        }

        classBuilder.addFunction(generateToMapFunction(objDef))

        return classBuilder.build()
    }

    private fun generateEnumClass(name: String, enumDef: PropertyDefinition.EnumDef): TypeSpec {
        val className = toPascalCase(name)
        val enumBuilder = TypeSpec.enumBuilder(className)
            .addModifiers(KModifier.INTERNAL)

        enumBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("serializedValue", String::class)
                .build()
        )
        enumBuilder.addProperty(
            PropertySpec.builder("serializedValue", String::class)
                .initializer("serializedValue")
                .build()
        )

        for (value in enumDef.values) {
            enumBuilder.addEnumConstant(
                toUpperSnakeCase(value),
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%S", value)
                    .build()
            )
        }

        return enumBuilder.build()
    }

    private fun generateToMapFunction(objDef: PropertyDefinition.ObjectDef): FunSpec {
        val mapType = MAP.parameterizedBy(STRING, Any::class.asTypeName().copy(nullable = true))
        val buildMap = MemberName("kotlin.collections", "buildMap")

        val funBuilder = FunSpec.builder("toMap")
            .addModifiers(KModifier.INTERNAL)
            .returns(mapType)

        val codeBuilder = CodeBlock.builder()
            .beginControlFlow("return %M", buildMap)

        for ((propName, propDef) in objDef.properties) {
            appendPropertySerialization(codeBuilder, propName, propDef)
        }

        codeBuilder.endControlFlow()
        funBuilder.addCode(codeBuilder.build())

        return funBuilder.build()
    }

    private fun appendPropertySerialization(
        codeBuilder: CodeBlock.Builder,
        propName: String,
        propDef: PropertyDefinition
    ) {
        val kotlinName = toCamelCase(propName)

        when (propDef) {
            is PropertyDefinition.Const -> {
                codeBuilder.addStatement("put(%S, %S)", propName, propDef.value)
            }

            is PropertyDefinition.Primitive -> {
                if (propDef.nullable) {
                    codeBuilder.addStatement("putNonNull(%S, %N)", propName, kotlinName)
                } else {
                    codeBuilder.addStatement("put(%S, %N)", propName, kotlinName)
                }
            }

            is PropertyDefinition.EnumDef -> {
                if (propDef.nullable) {
                    codeBuilder.addStatement(
                        "putNonNull(%S, %N?.serializedValue)",
                        propName,
                        kotlinName
                    )
                } else {
                    codeBuilder.addStatement(
                        "put(%S, %N.serializedValue)",
                        propName,
                        kotlinName
                    )
                }
            }

            is PropertyDefinition.ObjectDef -> {
                if (propDef.nullable) {
                    codeBuilder.addStatement(
                        "putNonNull(%S, %N?.toMap())",
                        propName,
                        kotlinName
                    )
                } else {
                    codeBuilder.addStatement("put(%S, %N.toMap())", propName, kotlinName)
                }
            }
        }
    }

    private fun generateExtensionFunction(
        entry: LogEntry,
        enclosingNames: List<String>,
        nonConstProperties: Map<String, PropertyDefinition>
    ): FunSpec {
        val internalLoggerClass = ClassName(
            "com.datadog.android.api",
            "InternalLogger"
        )

        val funBuilder = FunSpec.builder("log${toPascalCase(entry.id)}")
            .addModifiers(KModifier.INTERNAL)
            .receiver(internalLoggerClass)

        for ((propName, propDef) in nonConstProperties) {
            val kotlinName = toCamelCase(propName)
            val typeName = resolveTypeName(propName, propDef, enclosingNames)
            val finalType = if (propDef.nullable) typeName.copy(nullable = true) else typeName
            funBuilder.addParameter(ParameterSpec.builder(kotlinName, finalType).build())
        }

        val buildMap = MemberName("kotlin.collections", "buildMap")
        val codeBuilder = CodeBlock.builder()
        codeBuilder.add("logMetric(\n")
        codeBuilder.indent()
        codeBuilder.addStatement("messageBuilder = { %S },", entry.message)
        codeBuilder.add("additionalProperties = %M {\n", buildMap)
        codeBuilder.indent()

        for ((propName, propDef) in entry.properties) {
            appendPropertySerialization(codeBuilder, propName, propDef)
        }

        codeBuilder.unindent()
        codeBuilder.addStatement("},")
        codeBuilder.addStatement("samplingRate = %Lf", entry.sampleRate)
        codeBuilder.unindent()
        codeBuilder.addStatement(")")

        funBuilder.addCode(codeBuilder.build())

        return funBuilder.build()
    }

    private fun generatePutNonNullHelper(): FunSpec {
        val kType = TypeVariableName("K")
        val vType = TypeVariableName("V")
        val mutableMapType = ClassName("kotlin.collections", "MutableMap")
            .parameterizedBy(kType, vType)

        return FunSpec.builder("putNonNull")
            .addModifiers(KModifier.PRIVATE)
            .addTypeVariable(kType)
            .addTypeVariable(vType)
            .receiver(mutableMapType)
            .addParameter("key", kType)
            .addParameter("value", vType.copy(nullable = true))
            .beginControlFlow("if (value != null)")
            .addStatement("put(key, value)")
            .endControlFlow()
            .build()
    }

    private fun resolveTypeName(
        propertyName: String,
        propDef: PropertyDefinition,
        enclosingNames: List<String>
    ): TypeName {
        return when (propDef) {
            is PropertyDefinition.Primitive -> primitiveToTypeName(propDef.type)
            is PropertyDefinition.Const -> primitiveToTypeName(propDef.type)
            is PropertyDefinition.EnumDef -> {
                ClassName(packageName, enclosingNames + toPascalCase(propertyName))
            }
            is PropertyDefinition.ObjectDef -> {
                ClassName(packageName, enclosingNames + toPascalCase(propertyName))
            }
        }
    }

    private fun primitiveToTypeName(type: PrimitiveType): TypeName {
        return when (type) {
            PrimitiveType.STRING -> String::class.asTypeName()
            PrimitiveType.INT -> Int::class.asTypeName()
            PrimitiveType.LONG -> Long::class.asTypeName()
            PrimitiveType.FLOAT -> Float::class.asTypeName()
            PrimitiveType.DOUBLE -> Double::class.asTypeName()
            PrimitiveType.BOOLEAN -> Boolean::class.asTypeName()
        }
    }

    companion object {
        internal fun toPascalCase(snakeCase: String): String {
            return snakeCase.split("_").joinToString("") { part ->
                part.replaceFirstChar { it.uppercase() }
            }
        }

        internal fun toCamelCase(snakeCase: String): String {
            val pascal = toPascalCase(snakeCase)
            return pascal.replaceFirstChar { it.lowercase() }
        }

        internal fun toUpperSnakeCase(snakeCase: String): String {
            return snakeCase.uppercase()
        }
    }
}
