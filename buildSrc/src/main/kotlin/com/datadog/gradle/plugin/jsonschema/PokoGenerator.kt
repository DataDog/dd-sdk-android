/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import android.databinding.tool.ext.joinToCamelCaseAsVar
import android.databinding.tool.ext.toCamelCase
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import java.util.Locale
import kotlin.IllegalStateException

class PokoGenerator(
    internal val schemaFile: File,
    internal val outputDir: File,
    internal val packageName: String,
    internal val nameMapping: Map<String, String>
) {

    val gson = Gson()

    private lateinit var rootTypeName: String
    private lateinit var rootDefinition: Definition
    private val knownTypes: MutableList<String> = mutableListOf()
    private val loadedSchemas: MutableList<Definition> = mutableListOf()

    private val nestedDefinitions: MutableList<DefinitionRef> = mutableListOf()
    private val nestedEnums: MutableList<Pair<String, List<String>>> = mutableListOf()

    // region PokoGenerator

    /**
     * Generate a POKO file based on the input schema file
     */
    fun generate() {
        println("Generating class for schema ${schemaFile.name} with package name $packageName")
        val schema = loadSchema(schemaFile)
        require(schema.type == Type.OBJECT) {
            "Top level schema with type ${schema.type} is not supported."
        }
        rootDefinition = schema

        generateFile(schema)
    }

    // endregion

    // region Code Generation

    /**
     *  Generate a POKO file based on the root schema definition
     */
    private fun generateFile(schema: Definition) {
        val customName = nameMapping[schemaFile.name]
        val fileName = schemaFile.nameWithoutExtension
        rootTypeName = (customName ?: schema.title ?: fileName).toCamelCase()

        val fileBuilder = FileSpec.builder(packageName, rootTypeName)
        val typeBuilder = generateTopLevelType(schema)

        while (nestedDefinitions.isNotEmpty()) {
            val definitions = nestedDefinitions.toList()
            definitions.forEach {
                typeBuilder.addType(generateDataClass(it.className, it.definition).build())
            }
            nestedDefinitions.removeAll(definitions)
        }

        nestedEnums.forEach { (name, values) ->
            typeBuilder.addType(generateEnumClass(name.toCamelCase(), values))
        }

        fileBuilder
            .addType(typeBuilder.build())
            .indent("    ")
            .build()
            .writeTo(outputDir)
    }

    /**
     * Generates the main, top level type related to the given Schema
     */
    private fun generateTopLevelType(schema: Definition): TypeSpec.Builder {
        return if (!schema.properties.isNullOrEmpty()) {
            generateDataClass(rootTypeName, schema)
        } else if (!schema.allOf.isNullOrEmpty()) {
            generateTypeAllOf(rootTypeName, schema.allOf)
        } else if (!schema.ref.isNullOrBlank()) {
            val refDefinition = findDefinitionReference(schema.ref)
            if (refDefinition != null) {
                rootDefinition = refDefinition.definition
                generateTopLevelType(refDefinition.definition)
            } else {
                throw IllegalStateException(
                    "Top level definition reference not found: ${schema.ref}."
                )
            }
        } else {
            println("UNSUPPORTED\n$schema")
            TODO()
        }
    }

    /**
     * Generates the `data class` [TypeSpec.Builder] for the given definition.
     * @param className the name of the class / enum
     * @param definition the Json Schema definition of the type
     */
    private fun generateDataClass(
        className: String,
        definition: Definition
    ): TypeSpec.Builder {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(className)
        val docBuilder = CodeBlock.builder()

        appendTypeDefinition(
            definition,
            typeBuilder,
            constructorBuilder,
            docBuilder
        )

        typeBuilder.primaryConstructor(constructorBuilder.build())
            .addKdoc(docBuilder.build())

        return typeBuilder
    }

    /**
     * Generates the `enum class` [TypeSpec.Builder] for the given definition.
     * @param className the name of the class / enum
     * @param values the list of allowed json enum values
     */
    private fun generateEnumClass(
        className: String,
        values: List<String>
    ): TypeSpec {
        val enumBuilder = TypeSpec.enumBuilder(className)

        values.forEach { value ->
            enumBuilder.addEnumConstant(
                value.toUpperCase(Locale.US),
                TypeSpec.anonymousClassBuilder()
                    .addAnnotation(value.serializedAnnotation())
                    .build()
            )
        }

        return enumBuilder.build()
    }

    /**
     * Generates the `data class` [TypeSpec.Builder] merging all the given definitions.
     * @param className the name of the class / enum
     * @param definitions the Json Schema definition of the type
     */
    private fun generateTypeAllOf(
        className: String,
        definitions: List<Definition>
    ): TypeSpec.Builder {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(className)
        val docBuilder = CodeBlock.builder()

        definitions.forEach { subDef ->
            if (!subDef.ref.isNullOrBlank()) {
                val subRef = findDefinitionReference(subDef.ref)
                checkNotNull(subRef) { "AllOf definition reference not found: ${subDef.ref}." }
                appendTypeDefinition(subRef.definition, typeBuilder, constructorBuilder, docBuilder)
            } else if (!subDef.properties.isNullOrEmpty()) {
                appendTypeDefinition(subDef, typeBuilder, constructorBuilder, docBuilder)
            }
        }

        typeBuilder.primaryConstructor(constructorBuilder.build())
            .addKdoc(docBuilder.build())

        return typeBuilder
    }

    /**
     * Appends a `data class` property to a [TypeSpec.Builder].
     * @param name the property json name
     * @param required whether the property is required or not.
     * @param propertyDef the property [Definition]
     * @param typeBuilder the `data class` [TypeSpec] builder.
     * @param constructorBuilder the `data class` constructor builder.
     * @param docBuilder the `data class` KDoc builder.
     */
    private fun appendProperty(
        name: String,
        required: Boolean,
        propertyDef: Definition,
        typeBuilder: TypeSpec.Builder,
        constructorBuilder: FunSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {
        val varName = name.variableName()
        val type = propertyDef.asKotlinTypeName(name, false).copy(nullable = !required)

        val constructorParamBuilder = ParameterSpec.builder(varName, type)
        if (!required) { constructorParamBuilder.defaultValue("null") }
        constructorBuilder.addParameter(constructorParamBuilder.build())

        typeBuilder.addProperty(
            PropertySpec.builder(varName, type)
                .initializer(varName)
                .addAnnotation(name.serializedAnnotation())
                .build()
        )

        if (!propertyDef.description.isNullOrBlank()) {
            docBuilder.add("@param $varName ${propertyDef.description}\n")
        }
    }

    /**
     * Appends a `class` property to a [TypeSpec.Builder], with a constant default value.
     * @param name the property json name
     * @param propertyDef the property [Definition]
     * @param typeBuilder the `data class` [TypeSpec] builder.
     */
    private fun appendConstant(
        name: String,
        propertyDef: Definition,
        typeBuilder: TypeSpec.Builder
    ) {
        val varName = name.variableName()
        val constant = propertyDef.constant
        val propertyBuilder = if (constant is String) {
            PropertySpec.builder(varName, STRING)
                .initializer("\"$constant\"")
        } else if (constant is Double && propertyDef.type == Type.INTEGER) {
            PropertySpec.builder(varName, LONG)
                .initializer("${constant.toLong()}L")
        } else if (constant is Double) {
            PropertySpec.builder(varName, DOUBLE)
                .initializer("$constant")
        } else {
            TODO("Unknown type $constant ${constant!!.javaClass}")
        }

        if (!propertyDef.description.isNullOrBlank()) {
            propertyBuilder.addKdoc(propertyDef.description)
        }

        propertyBuilder.addAnnotation(name.serializedAnnotation())

        typeBuilder.addProperty(propertyBuilder.build())
    }

    /**
     * Appends all `data class` properties to a [TypeSpec.Builder] from the given definition.
     * @param definition the definition to use.
     * @param typeBuilder the `data class` [TypeSpec] builder.
     * @param constructorBuilder the `data class` constructor builder.
     * @param docBuilder the `data class` KDoc builder.
     */
    private fun appendTypeDefinition(
        definition: Definition,
        typeBuilder: TypeSpec.Builder,
        constructorBuilder: FunSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {
        if (!definition.description.isNullOrBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }

        var nonConstants = 0

        definition.properties?.forEach { (name, property) ->
            val required = (definition.required != null) && (name in definition.required)
            val isConstant = property.constant != null

            if (isConstant) {
                appendConstant(name, property, typeBuilder)
            } else {
                nonConstants++
                appendProperty(
                    name, required, property,
                    typeBuilder,
                    constructorBuilder,
                    docBuilder
                )
            }
        }

        if (nonConstants > 0) {
            typeBuilder.addModifiers(KModifier.DATA)
        }
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
    private fun findDefinitionReference(ref: String): DefinitionRef? {
        val file = REF_FILE_REGEX.matchEntire(ref)
        if (file != null) {
            return loadDefinitionFromFileRef(file.groupValues[2], file.groupValues[5])
        }

        val name = REF_DEFINITION_REGEX.matchEntire(ref)?.groupValues?.get(1)
        val id = REF_ID_REGEX.matchEntire(ref)?.groupValues?.get(0)
        if (name == null && id == null) return null

        val matcher = if (name != null) {
            { key: String, _: Definition -> key == name }
        } else {
            { _: String, def: Definition -> def.id == id }
        }

        val match = loadedSchemas.mapNotNull { schema ->
            schema.definitions?.entries?.firstOrNull { matcher(it.key, it.value) }
        }.firstOrNull() ?: return null

        val className = match.key.toCamelCase()
        return DefinitionRef(
            definition = match.value,
            id = ref,
            className = className,
            typeName = ClassName(packageName, rootTypeName, className)
        )
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
    ): DefinitionRef? {
        val file = File(schemaFile.parentFile.absolutePath + File.separator + path)
        val schema = loadSchema(file)

        return if (ref.isBlank()) {
            val className = (schema.title ?: file.nameWithoutExtension).toCamelCase()
            DefinitionRef(
                definition = schema,
                id = path,
                className = className,
                typeName = ClassName(packageName, rootTypeName, className)
            )
        } else {
            findDefinitionReference(ref)
        }
    }

    /**
     * Loads a Json Schema from the given file.
     * @param file the [File] to load from.
     * @return the loaded [Definition]
     */
    private fun loadSchema(file: File): Definition {
        val schema = gson.fromJson(
            file.inputStream().reader(Charsets.UTF_8),
            Definition::class.java
        )
        loadedSchemas.add(schema)
        return schema
    }

    // endregion

    // region Extensions

    private fun String.variableName(): String {
        val split = this.split("_").filter { it.isNotBlank() }
        if (split.isEmpty()) return ""
        if (split.size == 1) return split[0]
        return split.joinToCamelCaseAsVar()
    }

    private fun String.singular(): String {
        return if (endsWith("ies")) {
            substring(0, length - 3)
        } else if (endsWith("s")) {
            substring(0, length - 1)
        } else {
            this
        }
    }

    private fun String.uniqueClassName(): String {
        var uniqueName = this
        var tries = 0
        while (uniqueName in knownTypes) {
            tries++
            uniqueName = "${this}$tries"
        }
        knownTypes.add(uniqueName)
        return uniqueName
    }

    private fun DefinitionRef.withUniqueClassName(): DefinitionRef {
        val uniqueName = className.uniqueClassName()
        return copy(
            className = uniqueName,
            typeName = ClassName(packageName, rootTypeName, uniqueName)
        )
    }

    private fun Definition.asKotlinTypeName(
        name: String,
        withinCollection: Boolean
    ): TypeName {
        val typeName = if (withinCollection) {
            name.toCamelCase().singular()
        } else {
            name.toCamelCase()
        }

        if (!enum.isNullOrEmpty()) {
            val uniqueName = typeName.uniqueClassName()
            nestedEnums.add(uniqueName to enum)
            return ClassName(packageName, rootTypeName, uniqueName)
        }

        return if (ref.isNullOrBlank()) {
            type.asKotlinType(typeName, this)
        } else {
            val def = findDefinitionReference(ref)
            checkNotNull(def) { "Unable to get definition from: $ref" }
            val entry = nestedDefinitions.firstOrNull { it.definition == def.definition }
            if (entry == null) {
                val uniqueDef = def.withUniqueClassName()
                nestedDefinitions.add(uniqueDef)
                ClassName(packageName, rootTypeName, uniqueDef.className)
            } else {
                ClassName(packageName, rootTypeName, entry.className)
            }
        }
    }

    private fun Type?.asKotlinType(className: String, definition: Definition): TypeName {
        return when (this) {
            Type.NULL -> ANY_NULLABLE
            Type.BOOLEAN -> BOOLEAN
            Type.OBJECT -> {
                val def = DefinitionRef(
                    definition = definition,
                    id = className,
                    className = className,
                    typeName = ClassName(packageName, rootTypeName, className)
                ).withUniqueClassName()
                nestedDefinitions.add(def)
                def.typeName
            }
            Type.ARRAY -> {
                if (definition.uniqueItems == true) {
                    SET.parameterizedBy(
                        definition.items?.asKotlinTypeName(className, true) ?: STAR
                    )
                } else {
                    LIST.parameterizedBy(
                        definition.items?.asKotlinTypeName(className, true) ?: STAR
                    )
                }
            }
            Type.NUMBER -> DOUBLE
            Type.STRING -> STRING
            Type.INTEGER -> LONG
            null -> {
                ANY_NULLABLE
            }
        }
    }

    private fun String.serializedAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(SerializedName::class.java)
            .addMember("%S", this)
            .build()
    }

    // endregion

    companion object {

        private val REF_DEFINITION_REGEX = Regex("#/definitions/([\\w]+)")
        private val REF_ID_REGEX = Regex("#[\\w]+")
        private val REF_FILE_REGEX = Regex("(file:)?(([^/]+/)*([^/]+)\\.json)(#(.*))?")

        private val ANY_NULLABLE = ANY.copy(nullable = true)
    }
}
