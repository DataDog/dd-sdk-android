/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

sealed class TypeDefinition {

    abstract val description: String

    abstract fun mergedWith(other: TypeDefinition): TypeDefinition

    data class Null(
        override val description: String = ""
    ) : TypeDefinition() {
        override fun mergedWith(other: TypeDefinition): TypeDefinition {
            return other
        }
    }

    data class Constant(
        val type: JsonType?,
        val value: Any,
        override val description: String = ""
    ) : TypeDefinition() {
        override fun mergedWith(other: TypeDefinition): TypeDefinition {
            throw IllegalStateException("Can't merge Constant with type $other")
        }
    }

    data class Primitive(
        val type: JsonType,
        override val description: String = ""
    ) : TypeDefinition() {
        override fun mergedWith(other: TypeDefinition): TypeDefinition {
            if (other is Primitive && type == other.type) {
                return Primitive(type, "$description\n${other.description}".trim())
            } else {
                throw IllegalStateException("Can't merge Primitive with type $other")
            }
        }
    }

    data class Array(
        val items: TypeDefinition,
        val uniqueItems: Boolean = false,
        override val description: String = ""
    ) : TypeDefinition() {
        override fun mergedWith(other: TypeDefinition): TypeDefinition {
            TODO("Not yet implemented")
        }
    }

    data class Class(
        val name: String,
        val properties: List<TypeProperty>,
        override val description: String = ""
    ) : TypeDefinition() {
        override fun mergedWith(other: TypeDefinition): TypeDefinition {
            check(other is Class) { "Cannot merge Class with ${other.javaClass}" }

            val mergedFields = mutableListOf<TypeProperty>()
            properties.forEach { p ->
                val matchingProperty = other.properties.firstOrNull { it.name == p.name }
                if (matchingProperty == null) {
                    mergedFields.add(p)
                } else {
                    mergedFields.add(p.mergedWith(matchingProperty))
                }
            }

            other.properties.forEach { p ->
                val matchingProperty = properties.firstOrNull { it.name == p.name }
                if (matchingProperty == null) {
                    mergedFields.add(p)
                }
            }

            return Class(
                name,
                mergedFields,
                "$description\n${other.description}".trim()
            )
        }
    }

    data class Enum(
        val name: String,
        val type: JsonType?,
        val values: List<String>,
        override val description: String = ""
    ) : TypeDefinition() {
        override fun mergedWith(other: TypeDefinition): TypeDefinition {
            TODO("Not yet implemented")
        }
    }
}
