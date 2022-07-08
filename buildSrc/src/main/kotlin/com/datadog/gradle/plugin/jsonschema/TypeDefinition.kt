/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import java.util.Locale
import kotlin.reflect.KClass

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

        fun asPrimitiveType(): String {
            return when (type) {
                JsonPrimitiveType.BOOLEAN -> "asBoolean"
                JsonPrimitiveType.DOUBLE -> "asDouble"
                JsonPrimitiveType.STRING -> "asString"
                JsonPrimitiveType.INTEGER -> "asLong"
                JsonPrimitiveType.NUMBER -> "asNumber"
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
        val additionalProperties: TypeDefinition? = null,
        val parentType: MultiClass? = null
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

        fun isConstantClass(): Boolean {
            // all the properties are of type Constant and the additionalProperties is null
            this.properties.forEach {
                if (it.type !is Constant) {
                    return false
                }
            }
            return this.additionalProperties == null // TODO false
        }

        fun getChildrenTypeNames(): List<Pair<String, String>> {
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
        val values: List<String?>,
        override val description: String = ""
    ) : TypeDefinition() {

        override fun mergedWith(other: TypeDefinition): TypeDefinition {
            TODO("Not yet implemented")
        }

        fun jsonValueType(): KClass<*> {
            return when (type) {
                JsonType.NUMBER -> Number::class
                JsonType.STRING, JsonType.OBJECT, null -> String::class
                else -> throw IllegalStateException("Not yet implemented")
            }
        }

        internal fun rename(duplicates: Set<String>, parentName: String): TypeDefinition {
            return if (name in duplicates) {
                copy(name = "$parentName$name")
            } else {
                this
            }
        }

        internal fun allowsNull(): Boolean = values.any { it == null }

        internal fun enumConstantName(constantName: String?): String {
            return if (constantName == null) {
                "${name.toUpperCase(Locale.US)}_NULL"
            } else if (type == JsonType.NUMBER) {
                "${name.toUpperCase(Locale.US)}_${constantName.sanitizedName()}"
            } else {
                constantName.sanitizedName()
            }
        }

        private fun String.sanitizedName(): String {
            return toUpperCase(Locale.US).replace(Regex("[^A-Z0-9]+"), "_")
        }
    }

    data class MultiClass(
        val name: String,
        val type: Type,
        val options: List<TypeDefinition>,
        override val description: String = ""
    ) : TypeDefinition() {

        enum class Type {
            // TODO ANY_OF,
            ONE_OF
        }

        override fun mergedWith(other: TypeDefinition): TypeDefinition {
            TODO("Not yet implemented")
        }
    }
}
