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
    val packageName: String,
    val knownTypes: MutableSet<KotlinTypeWrapper>
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
            is TypeDefinition.OneOfClass -> name
        }
    }

    protected fun TypeDefinition.asKotlinTypeName(
        rootTypeName: String
    ): TypeName {
        return when (this) {
            is TypeDefinition.Null -> NOTHING
            is TypeDefinition.Primitive -> type.asKotlinTypeName()
            is TypeDefinition.Constant -> type.asKotlinTypeName()
            is TypeDefinition.Array -> {
                if (uniqueItems) {
                    SET.parameterizedBy(items.asKotlinTypeName(rootTypeName))
                } else {
                    LIST.parameterizedBy(items.asKotlinTypeName(rootTypeName))
                }
            }
            is TypeDefinition.Class -> withUniqueTypeName(rootTypeName).typeName
            is TypeDefinition.Enum -> withUniqueTypeName(rootTypeName).typeName
            is TypeDefinition.OneOfClass -> withUniqueTypeName(rootTypeName).typeName
        }
    }

    fun TypeDefinition.additionalPropertyTypeName(rootTypeName: String): TypeName {
        return if (this is TypeDefinition.Primitive) {
            this.asKotlinTypeName(rootTypeName)
        } else {
            ANY.copy(nullable = true)
        }
    }

    fun TypeDefinition.Class.withUniqueTypeName(rootTypeName: String): KotlinTypeWrapper {
        val matchingClass = knownTypes.firstOrNull {
            it.type.matches(this)
        }
        return if (matchingClass == null) {
            val uniqueName = name.uniqueTypeName()
            val typeName = if ((parentType == null) || (parentType.name == rootTypeName)) {
                ClassName(packageName, rootTypeName, uniqueName)
            } else {
                ClassName(packageName, rootTypeName, parentType.name, uniqueName)
            }
            KotlinTypeWrapper(
                uniqueName,
                typeName,
                this.copy(name = uniqueName)
            ).apply { knownTypes.add(this) }
        } else {
            matchingClass
        }
    }

    private fun TypeDefinition.Enum.withUniqueTypeName(rootTypeName: String): KotlinTypeWrapper {
        val matchingEnum = knownTypes.firstOrNull {
            it.type.matches(this)
        }
        return if (matchingEnum == null) {
            val uniqueName = name.uniqueTypeName()
            val typeName = ClassName(packageName, rootTypeName, uniqueName)
            KotlinTypeWrapper(
                uniqueName,
                typeName,
                this.copy(name = uniqueName)
            ).apply { knownTypes.add(this) }
        } else {
            matchingEnum
        }
    }

    private fun TypeDefinition.OneOfClass.withUniqueTypeName(rootTypeName: String): KotlinTypeWrapper {
        val matchingOneOf = knownTypes.firstOrNull {
            it.type.matches(this)
        }
        return if (matchingOneOf == null) {
            val uniqueName = name.uniqueTypeName()
            val typeName = ClassName(packageName, rootTypeName, uniqueName)
            KotlinTypeWrapper(
                uniqueName,
                typeName,
                this.copy(name = uniqueName)
            ).apply { knownTypes.add(this) }
        } else {
            matchingOneOf
        }
    }

    private fun String.uniqueTypeName(): String {
        var uniqueName = this
        var tries = 0
        while (knownTypes.any { it.name == uniqueName }) {
            tries++
            uniqueName = "${this}$tries"
        }
        return uniqueName
    }

    // endregion

}