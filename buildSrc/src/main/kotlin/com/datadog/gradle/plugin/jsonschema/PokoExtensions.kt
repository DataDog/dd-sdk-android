/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import android.databinding.tool.ext.joinToCamelCaseAsVar
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.NUMBER
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import java.util.Locale

internal val NOTHING_NULLABLE = NOTHING.copy(nullable = true)

internal fun String.variableName(): String {
    val split = this.split("_").filter { it.isNotBlank() }
    if (split.isEmpty()) return ""
    if (split.size == 1) return split[0]
    return split.joinToCamelCaseAsVar()
}

internal fun String.enumConstantName(): String {
    return toUpperCase(Locale.US).replace(Regex("[^A-Z0-9]+"), "_")
}

internal fun JsonType?.asKotlinTypeName(): TypeName {
    return when (this) {
        null,
        JsonType.NULL -> NOTHING_NULLABLE
        JsonType.BOOLEAN -> BOOLEAN
        JsonType.NUMBER -> NUMBER
        JsonType.STRING -> STRING
        JsonType.INTEGER -> LONG
        JsonType.OBJECT,
        JsonType.ARRAY -> throw IllegalArgumentException(
            "Cannot convert $this to a KotlinTypeName"
        )
    }
}

internal fun JsonPrimitiveType?.asKotlinTypeName(): TypeName {
    return when (this) {
        JsonPrimitiveType.BOOLEAN -> BOOLEAN
        JsonPrimitiveType.DOUBLE -> DOUBLE
        JsonPrimitiveType.STRING -> STRING
        JsonPrimitiveType.INTEGER -> LONG
        JsonPrimitiveType.NUMBER -> NUMBER
        null -> NOTHING_NULLABLE
    }
}

internal fun String.uniqueClassName(knownTypes: MutableList<String>): String {
    var uniqueName = this
    var tries = 0
    while (uniqueName in knownTypes) {
        tries++
        uniqueName = "${this}$tries"
    }
    knownTypes.add(uniqueName)
    return uniqueName
}

internal fun TypeDefinition.Enum.withUniqueTypeName(
    nestedEnums: MutableSet<TypeDefinition.Enum>,
    knownTypes: MutableList<String>
): TypeDefinition.Enum {
    val matchingEnum = nestedEnums.firstOrNull { it.values == values }
    return matchingEnum ?: copy(name = name.uniqueClassName(knownTypes))
}

internal fun TypeDefinition.Class.withUniqueTypeName(
    nestedClasses: MutableSet<TypeDefinition.Class>,
    knownTypes: MutableList<String>
): TypeDefinition.Class {
    val matchingClass = nestedClasses.firstOrNull { it.properties == properties }
    return matchingClass ?: copy(name = name.uniqueClassName(knownTypes))
}

internal fun TypeDefinition.asKotlinTypeName(
    nestedEnums: MutableSet<TypeDefinition.Enum>,
    nestedClasses: MutableSet<TypeDefinition.Class>,
    knownTypes: MutableList<String>,
    packageName: String,
    rootTypeName: String
): TypeName {
    return when (this) {
        is TypeDefinition.Null -> NOTHING
        is TypeDefinition.Primitive -> type.asKotlinTypeName()
        is TypeDefinition.Constant -> type.asKotlinTypeName()
        is TypeDefinition.Class -> {
            val def = withUniqueTypeName(nestedClasses, knownTypes)
            nestedClasses.add(def)
            ClassName(packageName, rootTypeName, def.name)
        }
        is TypeDefinition.Array -> {
            if (uniqueItems) {
                SET.parameterizedBy(
                    items.asKotlinTypeName(
                        nestedEnums,
                        nestedClasses,
                        knownTypes,
                        packageName,
                        rootTypeName
                    )
                )
            } else {
                LIST.parameterizedBy(
                    items.asKotlinTypeName(
                        nestedEnums,
                        nestedClasses,
                        knownTypes,
                        packageName,
                        rootTypeName
                    )
                )
            }
        }
        is TypeDefinition.Enum -> {
            val def = withUniqueTypeName(nestedEnums, knownTypes)
            nestedEnums.add(def)
            ClassName(packageName, rootTypeName, def.name)
        }
    }
}

internal fun TypeDefinition.additionalPropertyType(
    nestedEnums: MutableSet<TypeDefinition.Enum>,
    nestedClasses: MutableSet<TypeDefinition.Class>,
    knownTypes: MutableList<String>,
    packageName: String,
    rootTypeName: String
): TypeName {
    return if (this is TypeDefinition.Primitive) {
        this.asKotlinTypeName(
            nestedEnums,
            nestedClasses,
            knownTypes,
            packageName,
            rootTypeName
        )
    } else {
        ANY.copy(nullable = true)
    }
}

internal fun TypeDefinition.Class.isConstantClass(): Boolean {
    // all the properties are of type Constant and the additionalProperties is null
    this.properties.forEach {
        if (it.type !is TypeDefinition.Constant) {
            return false
        }
    }
    return this.additionalProperties == null
}