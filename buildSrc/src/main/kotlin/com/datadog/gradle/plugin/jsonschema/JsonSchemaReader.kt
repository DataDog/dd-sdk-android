/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import android.databinding.tool.ext.toCamelCase
import com.google.gson.Gson
import java.io.File

class JsonSchemaReader(
    internal val nameMapping: Map<String, String>
) {

    private val gson = Gson()
    private lateinit var currentFile: File

    private val loadedSchemas: MutableList<JsonDefinition> = mutableListOf()

    // region JsonSchemaReader

    fun readSchema(schemaFile: File): TypeDefinition {
        println("Reading schema ${schemaFile.name}")
        val schema = loadSchema(schemaFile)
        require(schema.type == JsonType.OBJECT) {
            "Top level schema with type ${schema.type} is not supported."
        }
        currentFile = schemaFile
        val customName = nameMapping[schemaFile.name]
        val fileName = schemaFile.nameWithoutExtension
        val typeName = (customName ?: schema.title ?: fileName).toCamelCase()

        val rawType = transform(schema, typeName)
        return sanitize(rawType)
    }

    // endregion

    // region JsonSchema parsing

    /**
     * Returns the [DefinitionRef] from a given `$ref` property.
     *
     * - `#/definitions/foo`
     * - `#bar`
     *
     * @param ref the reference used to resolve the target definiton
     * @return the found Definition reference or null
     */
    private fun findDefinitionReference(
        ref: String
    ): Pair<String, JsonDefinition>? {
        val file = REF_FILE_REGEX.matchEntire(ref)
        if (file != null) {
            return loadDefinitionFromFileRef(file.groupValues[2], file.groupValues[5])
        }

        val name = REF_DEFINITION_REGEX.matchEntire(ref)?.groupValues?.get(1)
        val id = REF_ID_REGEX.matchEntire(ref)?.groupValues?.get(0)
        if (name == null && id == null) return null

        val matcher = if (name != null) {
            { key: String, _: JsonDefinition -> key == name }
        } else {
            { _: String, def: JsonDefinition -> def.id == id }
        }

        val match = loadedSchemas.mapNotNull { schema ->
            schema.definitions?.entries?.firstOrNull { matcher(it.key, it.value) }
        }.firstOrNull() ?: return null

        return match.key.toCamelCase() to match.value
    }

    /**
     * Loads a Definition from a Json Schema file
     * @param path the path to the file to load (relatively to the root file)
     * @param ref a nested reference to a definiton, or blank to use the root type.
     * @return the loaded definition or `null` if it wasn't found
     */
    private fun loadDefinitionFromFileRef(
        path: String,
        ref: String
    ): Pair<String, JsonDefinition>? {
        val file = File(currentFile.parentFile.absolutePath + File.separator + path)
        val schema = loadSchema(file)

        return if (ref.isBlank()) {
            val className = (schema.title ?: file.nameWithoutExtension).toCamelCase()
            return className to schema
        } else {
            findDefinitionReference(ref)
        }
    }

    /**
     * Loads a Json Schema from the given file.
     * @param file the [File] to load from.
     * @return the loaded [JsonDefinition]
     */
    private fun loadSchema(file: File): JsonDefinition {
        val schema = gson.fromJson(
            file.inputStream().reader(Charsets.UTF_8),
            JsonDefinition::class.java
        )
        loadedSchemas.add(schema)
        return schema
    }

    // endregion

    // region Extensions

    private fun String.singular(): String {
        return if (endsWith("ies")) {
            substring(0, length - 3)
        } else if (endsWith("s")) {
            substring(0, length - 1)
        } else {
            this
        }
    }

    // endregion

    // region Internal

    private fun transform(
        definition: JsonDefinition?,
        typeName: String
    ): TypeDefinition {

        if (definition == null) return TypeDefinition.Null()

        val type = definition.type
        return when (type) {
            JsonType.NULL -> TypeDefinition.Null(definition.description.orEmpty())
            JsonType.BOOLEAN,
            JsonType.NUMBER,
            JsonType.INTEGER,
            JsonType.STRING -> transformPrimitive(definition, typeName)
            JsonType.ARRAY -> transformArray(definition, typeName)
            JsonType.OBJECT, null -> transformType(definition, typeName)
        }
    }

    private fun transformPrimitive(
        definition: JsonDefinition,
        typeName: String
    ): TypeDefinition {
        return if (!definition.enum.isNullOrEmpty()) {
            transformEnum(typeName, definition.type, definition.enum, definition.description)
        } else if (definition.constant != null) {
            transformConstant(definition.type, definition.constant, definition.description)
        } else {
            TypeDefinition.Primitive(
                type = definition.type ?: JsonType.NULL,
                description = definition.description.orEmpty()
            )
        }
    }

    private fun transformEnum(
        typeName: String,
        type: JsonType?,
        values: List<String>,
        description: String?
    ): TypeDefinition.Enum {
        return TypeDefinition.Enum(
            name = typeName,
            type = type,
            values = values,
            description = description.orEmpty()
        )
    }

    private fun transformConstant(
        type: JsonType?,
        constant: Any,
        description: String?
    ): TypeDefinition.Constant {
        return TypeDefinition.Constant(
            type = type,
            value = constant,
            description = description.orEmpty()
        )
    }

    private fun transformArray(
        definition: JsonDefinition,
        typeName: String
    ): TypeDefinition {
        val singularName = typeName.singular()
        val items = definition.items
        return TypeDefinition.Array(
            items = transform(items, singularName),
            uniqueItems = definition.uniqueItems ?: false,
            description = definition.description.orEmpty()
        )
    }

    private fun transformType(
        definition: JsonDefinition,
        typeName: String
    ): TypeDefinition {
        return if (!definition.enum.isNullOrEmpty()) {
            transformEnum(typeName, definition.type, definition.enum, definition.description)
        } else if (definition.constant != null) {
            transformConstant(definition.type, definition.constant, definition.description)
        } else if (!definition.properties.isNullOrEmpty()) {
            generateDataClass(typeName, definition)
        } else if (!definition.allOf.isNullOrEmpty()) {
            generateTypeAllOf(typeName, definition.allOf)
        } else if (!definition.ref.isNullOrBlank()) {
            val refDefinition = findDefinitionReference(definition.ref)
            if (refDefinition != null) {
                transform(refDefinition.second, refDefinition.first)
            } else {
                throw IllegalStateException(
                    "Definition reference not found: ${definition.ref}."
                )
            }
        } else {
            throw UnsupportedOperationException("Unsupported schema definition\n$definition")
        }
    }

    private fun generateTypeAllOf(
        typeName: String,
        allOf: List<JsonDefinition>
    ): TypeDefinition {
        var mergedType: TypeDefinition = TypeDefinition.Class(typeName, emptyList())

        allOf.forEach {
            val type = transform(it, typeName)
            mergedType = mergedType.mergedWith(type)
        }
        return mergedType
    }

    private fun generateDataClass(
        typeName: String,
        definition: JsonDefinition
    ): TypeDefinition {

        val properties = mutableListOf<TypeProperty>()
        definition.properties?.forEach { (name, property) ->
            val required = (definition.required != null) && (name in definition.required)
            val readOnly = (property.readOnly == null) || (property.readOnly)
            val propertyType = transform(
                property,
                name.toCamelCase()
            )
            properties.add(TypeProperty(name, propertyType, !required, readOnly))
        }

        return TypeDefinition.Class(
            name = typeName,
            description = definition.description.orEmpty(),
            properties = properties
        )
    }

    private fun sanitize(type: TypeDefinition): TypeDefinition {
        if (type is TypeDefinition.Class) {
            val names = type.getChildrenTypeNames().distinct().sortedBy { it.first }
            val duplicates = mutableSetOf<String>()

            names.forEachIndexed { i, n ->
                if (i > 0) {
                    if (n.first == names[i - 1].first) {
                        duplicates.add(n.first)
                    }
                }
            }

            return type.renameRecursive(duplicates, "")
        } else {
            return type
        }
    }
    // endregion

    companion object {

        private val REF_DEFINITION_REGEX = Regex("#/definitions/([\\w]+)")
        private val REF_ID_REGEX = Regex("#[\\w]+")
        private val REF_FILE_REGEX = Regex("(file:)?(([^/]+/)*([^/]+)\\.json)(#(.*))?")
    }
}
