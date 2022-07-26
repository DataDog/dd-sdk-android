/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.datadog.gradle.utils.toCamelCase
import com.google.gson.Gson
import java.io.File
import org.gradle.api.logging.Logger

class JsonSchemaReader(
    private val nameMapping: Map<String, String>,
    private val logger: Logger
) {

    private val gson = Gson()
    private lateinit var currentFile: File

    private val loadedSchemas: MutableList<JsonDefinition> = mutableListOf()
    private val knownSchemas: MutableMap<File, JsonDefinition> = mutableMapOf()

    // region JsonSchemaReader

    fun readSchema(schemaFile: File): TypeDefinition {
        logger.info("Reading schema ${schemaFile.name}")
        val schema = loadSchema(schemaFile)
        currentFile = schemaFile
        val customName = nameMapping[schemaFile.name]
        val fileName = schemaFile.nameWithoutExtension
        val typeName = (customName ?: schema.title ?: fileName).toCamelCase()

        val rawType = transform(schema, typeName, schemaFile)
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
     * @param fromFile the file where the reference is being used
     * @param ref the reference used to resolve the target definition
     * @return the found Definition reference or null
     */
    private fun findDefinitionReference(
        ref: String,
        fromFile: File
    ): JsonDefinitionReference? {
        val file = REF_FILE_REGEX.matchEntire(ref)
        if (file != null) {
            val path = file.groupValues[2]
            val localRef = file.groupValues[5]
            return loadDefinitionFromFileRef(path, localRef, fromFile)
        }

        val name = REF_DEFINITION_REGEX.matchEntire(ref)?.groupValues?.get(1)
        val id = REF_ID_REGEX.matchEntire(ref)?.groupValues?.get(0)
        if (name == null && id == null) return null

        val matcher = if (name != null) {
            { key: String, _: JsonDefinition -> key == name }
        } else {
            { _: String, def: JsonDefinition -> def.id == id }
        }

        val match = knownSchemas[fromFile]
            ?.definitions
            ?.entries
            ?.firstOrNull { matcher(it.key, it.value) } ?: return null

        return JsonDefinitionReference(
            match.key.toCamelCase(),
            match.value,
            fromFile
        )
    }

    /**
     * Loads a Definition from a Json Schema file
     * @param path the path to the file to load (relatively to the root file)
     * @param ref a nested reference to a definition, or blank to use the root type.
     * @return the loaded definition or `null` if it wasn't found
     */
    private fun loadDefinitionFromFileRef(
        path: String,
        ref: String,
        fromFile: File
    ): JsonDefinitionReference? {
        val file = File(fromFile.parentFile.absolutePath + File.separator + path)
        val schema = loadSchema(file)

        return if (ref.isBlank()) {
            val className = (schema.title ?: file.nameWithoutExtension).toCamelCase()
            return JsonDefinitionReference(
                className,
                schema,
                file
            )
        } else {
            findDefinitionReference(ref, file)
        }
    }

    /**
     * Loads a Json Schema from the given file.
     * @param file the [File] to load from.
     * @return the loaded [JsonDefinition]
     */
    private fun loadSchema(file: File): JsonDefinition {
        val knownSchema = knownSchemas[file]
        if (knownSchema != null) {
            return knownSchema
        }

        val schema = gson.fromJson(
            file.inputStream().reader(Charsets.UTF_8),
            JsonDefinition::class.java
        )
        loadedSchemas.add(schema)
        knownSchemas[file] = schema

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

    private fun extractAdditionalPropertiesType(
        definition: JsonDefinition,
        fromFile: File
    ): TypeDefinition? {
        return when (val additional = definition.additionalProperties) {
            null -> null // TODO additionalProperties is true by default !
            is Map<*, *> -> {
                val value = additional["type"]?.toString()
                if (value == null) {
                    throw IllegalStateException("additionalProperties object is missing a `type`")
                } else {
                    val type = JsonType.values().firstOrNull { it.name.equals(value, true) }
                    transform(JsonDefinition.ANY.copy(type = type), "?", fromFile)
                }
            }
            is Boolean -> {
                if (additional) {
                    transform(JsonDefinition.EMPTY.copy(type = JsonType.OBJECT), "?", fromFile)
                } else {
                    null
                }
            }
            else -> {
                throw IllegalStateException("additionalProperties uses an unknown format")
            }
        }
    }

    private fun transform(
        definition: JsonDefinition?,
        typeName: String,
        fromFile: File
    ): TypeDefinition {

        if (definition == null) return TypeDefinition.Null()

        return when (definition.type) {
            JsonType.NULL -> TypeDefinition.Null(definition.description.orEmpty())
            JsonType.BOOLEAN -> transformPrimitive(definition, JsonPrimitiveType.BOOLEAN, typeName)
            JsonType.NUMBER -> transformPrimitive(definition, JsonPrimitiveType.NUMBER, typeName)
            JsonType.INTEGER -> transformPrimitive(definition, JsonPrimitiveType.INTEGER, typeName)
            JsonType.STRING -> transformPrimitive(definition, JsonPrimitiveType.STRING, typeName)
            JsonType.ARRAY -> transformArray(definition, typeName, fromFile)
            JsonType.OBJECT,
            null -> transformType(definition, typeName, fromFile)
        }
    }

    private fun transformPrimitive(
        definition: JsonDefinition,
        primitiveType: JsonPrimitiveType,
        typeName: String
    ): TypeDefinition {
        return if (!definition.enum.isNullOrEmpty()) {
            transformEnum(typeName, definition.type, definition.enum, definition.description)
        } else if (definition.constant != null) {
            transformConstant(definition.type, definition.constant, definition.description)
        } else {
            TypeDefinition.Primitive(
                type = primitiveType,
                description = definition.description.orEmpty()
            )
        }
    }

    private fun transformEnum(
        typeName: String,
        type: JsonType?,
        values: List<String?>,
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
        typeName: String,
        fromFile: File
    ): TypeDefinition {
        val singularName = typeName.singular()
        val items = definition.items
        return TypeDefinition.Array(
            items = transform(items, singularName, fromFile),
            uniqueItems = definition.uniqueItems ?: false,
            description = definition.description.orEmpty()
        )
    }

    private fun transformType(
        definition: JsonDefinition,
        typeName: String,
        fromFile: File
    ): TypeDefinition {
        return if (!definition.enum.isNullOrEmpty()) {
            transformEnum(typeName, definition.type, definition.enum, definition.description)
        } else if (definition.constant != null) {
            transformConstant(definition.type, definition.constant, definition.description)
        } else if (!definition.properties.isNullOrEmpty() ||
            definition.additionalProperties != null
        ) {
            generateDataClass(typeName, definition, fromFile)
        } else if (!definition.allOf.isNullOrEmpty()) {
            generateTypeAllOf(typeName, definition.allOf, fromFile)
        } else if (!definition.oneOf.isNullOrEmpty()) {
            generateTypeOneOf(typeName, definition.oneOf, definition.description, fromFile)
        } else if (!definition.ref.isNullOrBlank()) {
            val refDefinition = findDefinitionReference(definition.ref, fromFile)
            if (refDefinition != null) {
                transform(refDefinition.definition, refDefinition.typeName, refDefinition.fromFile)
            } else {
                throw IllegalStateException(
                    "Definition reference not found: ${definition.ref}."
                )
            }
        } else if (definition.type == JsonType.OBJECT) {
            generateDataClass(typeName, definition, fromFile)
        } else {
            throw UnsupportedOperationException("Unsupported schema definition\n$definition")
        }
    }

    private fun generateTypeOneOf(
        typeName: String,
        oneOf: List<JsonDefinition>,
        description: String?,
        fromFile: File
    ): TypeDefinition {
        return TypeDefinition.OneOfClass(
            typeName,
            oneOf.mapIndexed { i, it ->
                transform(it, it.title ?: "${typeName}_$i", fromFile)
            },
            description.orEmpty()
        )
    }

    private fun generateTypeAllOf(
        typeName: String,
        allOf: List<JsonDefinition>,
        fromFile: File
    ): TypeDefinition {
        var mergedType: TypeDefinition = TypeDefinition.Class(typeName, emptyList())

        allOf.forEach {
            val type = transform(it, typeName, fromFile)
            mergedType = mergedType.mergedWith(type)
        }
        return mergedType
    }

    private fun generateDataClass(
        typeName: String,
        definition: JsonDefinition,
        fromFile: File
    ): TypeDefinition {

        val properties = mutableListOf<TypeProperty>()
        definition.properties?.forEach { (name, property) ->
            val required = (definition.required != null) && (name in definition.required)
            val readOnly = (property.readOnly == null) || (property.readOnly)
            val propertyType = transform(
                property,
                name.toCamelCase(),
                fromFile
            )
            properties.add(
                TypeProperty(
                    name,
                    propertyType,
                    !required,
                    readOnly,
                    property.default
                )
            )
        }
        val additional = extractAdditionalPropertiesType(definition, fromFile)

        return TypeDefinition.Class(
            name = typeName,
            description = definition.description.orEmpty(),
            properties = properties,
            additionalProperties = additional
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
