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
    private val packageName: String,
    private val className: String
) {

    private val internalLoggerClass = ClassName(
        "com.datadog.android.api",
        "InternalLogger"
    )

    fun generate(config: LogsConfig, outputDir: File) {
        val enclosingNames = listOf(className)

        val classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.INTERNAL)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("internalLogger", internalLoggerClass)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("internalLogger", internalLoggerClass)
                    .initializer("internalLogger")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )

        for (entry in config.logs) {
            val nonConstProperties = entry.properties.filter { (_, prop) ->
                prop !is PropertyDefinition.Const
            }

            for ((propName, propDef) in nonConstProperties) {
                val nestedType = generateTypeSpec(propName, propDef, enclosingNames)
                if (nestedType != null) {
                    classBuilder.addType(nestedType)
                }
            }

            classBuilder.addFunction(
                generateLogFunction(entry, enclosingNames, nonConstProperties)
            )
        }

        val fileSpec = FileSpec.builder(packageName, className)
            .addType(classBuilder.build())
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
        val typeName = toPascalCase(name)
        val childEnclosingNames = enclosingNames + typeName
        val classBuilder = TypeSpec.classBuilder(typeName)
            .addModifiers(KModifier.INTERNAL, KModifier.DATA)

        val constructorBuilder = FunSpec.constructorBuilder()

        for ((propName, propDef) in objDef.properties) {
            if (propDef is PropertyDefinition.Const) continue

            val kotlinName = toCamelCase(propName)
            val resolvedType = resolveTypeName(propName, propDef, childEnclosingNames)
            val finalType = if (propDef.nullable) resolvedType.copy(nullable = true) else resolvedType

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
        val typeName = toPascalCase(name)
        val enumBuilder = TypeSpec.enumBuilder(typeName)
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
                appendConstSerialization(codeBuilder, propName, propDef)
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

    private fun appendConstSerialization(
        codeBuilder: CodeBlock.Builder,
        propName: String,
        propDef: PropertyDefinition.Const
    ) {
        when (propDef.type) {
            PrimitiveType.STRING -> codeBuilder.addStatement("put(%S, %S)", propName, propDef.value)
            PrimitiveType.INT -> codeBuilder.addStatement("put(%S, %L)", propName, propDef.value.toInt())
            PrimitiveType.LONG -> codeBuilder.addStatement("put(%S, %LL)", propName, propDef.value.toLong())
            PrimitiveType.FLOAT -> codeBuilder.addStatement("put(%S, %Lf)", propName, propDef.value.toFloat())
            PrimitiveType.DOUBLE -> codeBuilder.addStatement("put(%S, %L)", propName, propDef.value.toDouble())
            PrimitiveType.BOOLEAN -> codeBuilder.addStatement("put(%S, %L)", propName, propDef.value.toBoolean())
        }
    }

    private fun generateLogFunction(
        entry: LogEntry,
        enclosingNames: List<String>,
        nonConstProperties: Map<String, PropertyDefinition>
    ): FunSpec {
        return when (entry) {
            is MetricLogEntry -> generateMetricLogFunction(entry, enclosingNames, nonConstProperties)
            is SimpleLogEntry -> generateSimpleLogFunction(entry, enclosingNames, nonConstProperties)
        }
    }

    private fun generateMetricLogFunction(
        entry: MetricLogEntry,
        enclosingNames: List<String>,
        nonConstProperties: Map<String, PropertyDefinition>
    ): FunSpec {
        val funBuilder = FunSpec.builder("log${toPascalCase(entry.id)}")
            .addModifiers(KModifier.INTERNAL)

        addPropertyParameters(funBuilder, nonConstProperties, enclosingNames)

        val buildMap = MemberName("kotlin.collections", "buildMap")
        val codeBuilder = CodeBlock.builder()
        codeBuilder.add("internalLogger.logMetric(\n")
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

    private fun generateSimpleLogFunction(
        entry: SimpleLogEntry,
        enclosingNames: List<String>,
        nonConstProperties: Map<String, PropertyDefinition>
    ): FunSpec {
        val funBuilder = FunSpec.builder("log${toPascalCase(entry.id)}")
            .addModifiers(KModifier.INTERNAL)

        if (entry.throwable) {
            funBuilder.addParameter(
                ParameterSpec.builder(
                    "throwable",
                    Throwable::class.asTypeName().copy(nullable = true)
                ).defaultValue("null").build()
            )
        }

        addPropertyParameters(funBuilder, nonConstProperties, enclosingNames)

        val levelClass = ClassName("com.datadog.android.api", "InternalLogger", "Level")
        val targetClass = ClassName("com.datadog.android.api", "InternalLogger", "Target")

        val codeBuilder = CodeBlock.builder()
        codeBuilder.add("internalLogger.log(\n")
        codeBuilder.indent()
        codeBuilder.addStatement("level = %T.%L,", levelClass, entry.level.name)

        if (entry.targets.size == 1) {
            codeBuilder.addStatement(
                "target = %T.%L,",
                targetClass,
                entry.targets.first().name
            )
        } else {
            val targetsList = entry.targets.joinToString(", ") { "%T.${it.name}" }
            val targetTypes = entry.targets.map { targetClass }.toTypedArray()
            codeBuilder.addStatement(
                "targets = listOf($targetsList),",
                *targetTypes
            )
        }

        codeBuilder.addStatement("messageBuilder = { %S }", entry.message)

        if (entry.throwable) {
            codeBuilder.add(",\nthrowable = throwable")
        }

        if (entry.onlyOnce) {
            codeBuilder.add(",\nonlyOnce = true")
        }

        if (nonConstProperties.isNotEmpty()) {
            val buildMap = MemberName("kotlin.collections", "buildMap")
            codeBuilder.add(",\nadditionalProperties = %M {\n", buildMap)
            codeBuilder.indent()
            for ((propName, propDef) in entry.properties) {
                appendPropertySerialization(codeBuilder, propName, propDef)
            }
            codeBuilder.unindent()
            codeBuilder.add("}")
        }

        codeBuilder.add("\n")
        codeBuilder.unindent()
        codeBuilder.addStatement(")")

        funBuilder.addCode(codeBuilder.build())

        return funBuilder.build()
    }

    private fun addPropertyParameters(
        funBuilder: FunSpec.Builder,
        nonConstProperties: Map<String, PropertyDefinition>,
        enclosingNames: List<String>
    ) {
        for ((propName, propDef) in nonConstProperties) {
            val kotlinName = toCamelCase(propName)
            val resolvedType = resolveTypeName(propName, propDef, enclosingNames)
            val finalType = if (propDef.nullable) resolvedType.copy(nullable = true) else resolvedType
            funBuilder.addParameter(ParameterSpec.builder(kotlinName, finalType).build())
        }
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

        internal fun moduleNameToClassName(moduleName: String): String {
            return moduleName.split("-").joinToString("") { part ->
                part.replaceFirstChar { it.uppercase() }
            } + "Logger"
        }
    }
}
