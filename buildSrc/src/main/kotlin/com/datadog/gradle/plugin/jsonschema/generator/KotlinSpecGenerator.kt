/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.datadog.gradle.plugin.jsonschema.asKotlinTypeName
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.TypeName

abstract class KotlinSpecGenerator<I : Any, O : Any>(
    private val packageName: String,
    val nestedTypes: MutableSet<TypeDefinition>,
    val knownTypeNames: MutableSet<String>
) {

    /**
     * Generates a KotlinPoet Spec based on the provided Json information
     * @param rootTypeName the name of the root type (matching the name of the file)
     * @param definition the definition read from a Json Schema file
     */
    abstract fun generate(definition: I, rootTypeName: String): O

    // region Utilities

    protected fun TypeDefinition.name(): String? {
        return when (this) {
            is TypeDefinition.Array,
            is TypeDefinition.Primitive,
            is TypeDefinition.Constant,
            is TypeDefinition.Null -> null
            is TypeDefinition.Enum -> name
            is TypeDefinition.Class -> name
            is TypeDefinition.MultiClass -> name
        }
    }

    protected fun TypeDefinition.asKotlinTypeName(
        rootTypeName: String
    ): TypeName {
        return when (this) {
            is TypeDefinition.Null -> NOTHING
            is TypeDefinition.Primitive -> type.asKotlinTypeName()
            is TypeDefinition.Constant -> type.asKotlinTypeName()
            is TypeDefinition.Class -> {
                val def = withUniqueTypeName()
                nestedTypes.add(def)
                ClassName(packageName, rootTypeName, def.name)
            }
            is TypeDefinition.Array -> {
                if (uniqueItems) {
                    SET.parameterizedBy(items.asKotlinTypeName(rootTypeName))
                } else {
                    LIST.parameterizedBy(items.asKotlinTypeName(rootTypeName))
                }
            }
            is TypeDefinition.Enum -> {
                val def = withUniqueTypeName()
                nestedTypes.add(def)
                ClassName(packageName, rootTypeName, def.name)
            }
            is TypeDefinition.MultiClass -> {
                if (rootTypeName == name) {
                    ClassName(packageName, rootTypeName)
                } else {
                    ClassName(packageName, rootTypeName, name)
                }
            }
        }
    }

    fun TypeDefinition.additionalPropertyTypeName(rootTypeName: String): TypeName {
        return if (this is TypeDefinition.Primitive) {
             this.asKotlinTypeName(rootTypeName)
        } else {
            ANY.copy(nullable = true)
        }
    }

    private fun TypeDefinition.Class.withUniqueTypeName(): TypeDefinition.Class {
        val matchingClass = nestedTypes.filterIsInstance<TypeDefinition.Class>()
            .firstOrNull {
                it.properties == properties && it.additionalProperties == additionalProperties
            }
        return matchingClass ?: copy(name = name.uniqueTypeName())
    }

    private fun TypeDefinition.Enum.withUniqueTypeName(): TypeDefinition.Enum {
        val matchingEnum = nestedTypes.filterIsInstance<TypeDefinition.Enum>()
            .firstOrNull { it.values == values }
        return matchingEnum ?: copy(name = name.uniqueTypeName())
    }

    private fun String.uniqueTypeName(): String {
        var uniqueName = this
        var tries = 0
        while (uniqueName in knownTypeNames) {
            tries++
            uniqueName = "${this}$tries"
        }
        knownTypeNames.add(uniqueName)
        return uniqueName
    }

    // endregion
}