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
        val type: JsonPrimitiveType,
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
        override val description: String = "",
        val additionalProperties: TypeDefinition? = null
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

        internal fun getChildrenTypeNames(): List<Pair<String, String>> {
            val direct = properties.map { it.type }
                .mapNotNull {
                    when (it) {
                        is Class -> it.name to it.toString()
                        is Enum -> it.name to it.toString()
                        else -> null
                    }
                }
            val indirect = properties.map { it.type }
                .mapNotNull { (it as? Class)?.getChildrenTypeNames() }
                .flatten()

            return direct + indirect + (name to toString())
        }

        fun renameRecursive(duplicates: Set<String>, parentName: String): TypeDefinition {
            val newName = if (name in duplicates) {
                "$parentName$name"
            } else name

            val newProperties = properties.map {
                if (it.type is Class) {
                    it.copy(type = it.type.renameRecursive(duplicates, newName))
                } else if (it.type is Enum) {
                    it.copy(type = it.type.rename(duplicates, newName))
                } else {
                    it
                }
            }

            return copy(name = newName, properties = newProperties)
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

        internal fun rename(duplicates: Set<String>, parentName: String): TypeDefinition {
            return if (name in duplicates) {
                copy(name = "$parentName$name")
            } else {
                this
            }
        }
    }
}
