/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import java.io.File
import java.lang.IllegalArgumentException

class PokoGenerator(
    internal val outputDir: File,
    internal val packageName: String
) {

    private lateinit var rootTypeName: String
    private val knownTypes: MutableList<String> = mutableListOf()
    private val nestedClasses: MutableSet<TypeDefinition.Class> = mutableSetOf()

    private val nestedEnums: MutableSet<TypeDefinition.Enum> = mutableSetOf()
    private val deserializerGenerator =
        PokoDeserializerGenerator(packageName, knownTypes, nestedClasses, nestedEnums)
    private val serializerGenerator = PokoSerializerGenerator()

    // region PokoGenerator

    /**
     * Generate a POKO file based on the input schema file
     */
    fun generate(typeDefinition: TypeDefinition) {
        println("Generating class for type $typeDefinition with package name $packageName")
        knownTypes.clear()
        nestedClasses.clear()
        nestedEnums.clear()
        generateFile(typeDefinition)
    }

    // endregion

    // region Code Generation

    /**
     *  Generate a POKO file based on the root schema definition
     */
    private fun generateFile(definition: TypeDefinition) {
        check(definition is TypeDefinition.Class)

        rootTypeName = definition.name
        val fileBuilder = FileSpec.builder(packageName, definition.name)
        val typeBuilder = generateClass(definition, fileBuilder)

        while (nestedClasses.isNotEmpty()) {
            val definitions = nestedClasses.toList()
            definitions.forEach {
                typeBuilder.addType(generateClass(it, fileBuilder).build())
            }
            nestedClasses.removeAll(definitions)
        }

        nestedEnums.forEach {
            typeBuilder.addType(generateEnumClass(it))
        }

        fileBuilder
            .addType(typeBuilder.build())
            .indent("    ")
            .build()
            .writeTo(outputDir)
    }

    /**
     * Generates the `class` [TypeSpec.Builder] for the given definition.
     * @param definition the definition of the type
     */
    private fun generateClass(
        definition: TypeDefinition.Class,
        fileBuilder: FileSpec.Builder
    ): TypeSpec.Builder {
        val constructorBuilder = FunSpec.constructorBuilder()
        val typeBuilder = TypeSpec.classBuilder(definition.name)
        val docBuilder = CodeBlock.builder()

        addClassImports(definition, fileBuilder)
        appendTypeDefinition(
            definition,
            typeBuilder,
            constructorBuilder,
            docBuilder
        )
        typeBuilder.primaryConstructor(constructorBuilder.build())
            .addKdoc(docBuilder.build())
        generateCompanionObjectForClass(typeBuilder, definition)

        return typeBuilder
    }

    /**
     * Generates the Companion Object code block
     * @param typeBuilder the class builder
     * @param definition the class type definition
     */
    private fun generateCompanionObjectForClass(
        typeBuilder: TypeSpec.Builder,
        definition: TypeDefinition.Class
    ) {
        // In case the class is constant (contains constant primitives and no additionalProperties)
        // we do not need a deserializer method or RESERVED_ATTRIBUTES for serialization
        if (definition.isConstantClass()) {
            return
        }

        val companionSpecBuilder: TypeSpec.Builder = TypeSpec.companionObjectBuilder()
        // add the RESERVED_ATTRIBUTES property to Companion Object
        if (definition.additionalProperties != null && definition.properties.isNotEmpty()) {
            companionSpecBuilder.addProperty(generateReservedPropertyNames(definition))
        }

        deserializerGenerator.generateDeserializerForClass(
            definition,
            rootTypeName,
            companionSpecBuilder
        )
        typeBuilder.addType(companionSpecBuilder.build())
    }

    /**
     * Provides extra imports for this specific class.
     * @param definition the class type
     * @param fileBuilder the file builder
     */
    private fun addClassImports(
        definition: TypeDefinition.Class,
        fileBuilder: FileSpec.Builder
    ) {
        if (definition.additionalProperties != null &&
            definition.additionalProperties is TypeDefinition.Class
        ) {
            // import extension functions
            fileBuilder.addImport(
                EXTENSION_FUNCTIONS_PACKAGE_NAME,
                TO_JSON_ELEMENT_EXTENSION_FUNCTION
            )
        }
    }

    /**
     * Generates the `enum class` [TypeSpec.Builder] for the given definition.
     * @param definition the enum class definition
     */
    private fun generateEnumClass(
        definition: TypeDefinition.Enum
    ): TypeSpec {
        val enumBuilder = TypeSpec.enumBuilder(definition.name)
        enumBuilder.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ENUM_CONSTRUCTOR_JSON_VALUE_NAME, String::class)
                .build()
        )
        val docBuilder = CodeBlock.builder()

        if (definition.description.isNotBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }
        enumBuilder.addProperty(
            PropertySpec.builder(ENUM_CONSTRUCTOR_JSON_VALUE_NAME, String::class, KModifier.PRIVATE)
                .initializer(ENUM_CONSTRUCTOR_JSON_VALUE_NAME)
                .build()
        )

        definition.values.forEach { value ->
            enumBuilder.addEnumConstant(
                value.enumConstantName(),
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter("%S", value)
                    .build()
            )
        }

        enumBuilder.addFunction(serializerGenerator.generateEnumSerializer())
        val companionSpecBuilder = TypeSpec.companionObjectBuilder()
        deserializerGenerator.generateDeserializerForEnum(
            definition,
            rootTypeName,
            companionSpecBuilder
        )

        return enumBuilder
            .addKdoc(docBuilder.build())
            .addType(companionSpecBuilder.build())
            .build()
    }

    /**
     * Appends a property to a [TypeSpec.Builder].
     * @param property the property definition
     * @param typeBuilder the `data class` [TypeSpec] builder.
     * @param constructorBuilder the `data class` constructor builder.
     * @param docBuilder the `data class` KDoc builder.
     */
    private fun appendProperty(
        property: TypeProperty,
        typeBuilder: TypeSpec.Builder,
        constructorBuilder: FunSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {
        val varName = property.name.variableName()
        val nullable = (property.optional || property.type is TypeDefinition.Null)
        val notNullableType = property.type.asKotlinTypeName(
            nestedEnums,
            nestedClasses,
            knownTypes,
            packageName,
            rootTypeName
        )
        val type = notNullableType.copy(nullable = nullable)
        val constructorParamBuilder = ParameterSpec.builder(varName, type)
        if (property.defaultValue != null) {
            appendDefaultConstructorValue(property, constructorParamBuilder, notNullableType)
        } else if (nullable) {
            constructorParamBuilder.defaultValue("null")
        }
        constructorBuilder.addParameter(constructorParamBuilder.build())

        typeBuilder.addProperty(
            PropertySpec.builder(varName, type)
                .mutable(!property.readOnly)
                .initializer(varName)
                .build()
        )

        if (property.type.description.isNotBlank()) {
            docBuilder.add("@param $varName ${property.type.description}\n")
        }
    }

    /**
     * Appends property default constructor value.
     * @param property the property type
     * @param constructorParamBuilder the `data class` constructor builder.
     * @param propertyTypeName Kotlin Poet equivalent property [TypeName]
     */
    private fun appendDefaultConstructorValue(
        property: TypeProperty,
        constructorParamBuilder: ParameterSpec.Builder,
        propertyTypeName: TypeName
    ) {
        val type = property.type
        when (type) {
            is TypeDefinition.Primitive -> {
                constructorParamBuilder.defaultValue(
                    getKotlinValue(
                        property.defaultValue,
                        type.type
                    )
                )
            }
            is TypeDefinition.Enum -> {
                constructorParamBuilder.defaultValue(
                    "%T.%L",
                    propertyTypeName,
                    (property.defaultValue as String).enumConstantName()
                )
            }
            else -> {
                throw IllegalArgumentException(
                    "Unable to generate default value " +
                        "for class: $type. This feature is not supported yet"
                )
            }
        }
    }

    /**
     * Appends a property to a [TypeSpec.Builder].
     * @param additionalPropertyType the additional properties type definition
     * @param typeBuilder the `data class` [TypeSpec] builder.
     * @param constructorBuilder the `data class` constructor builder.
     * @param docBuilder the `data class` KDoc builder.
     */
    private fun appendAdditionalProperties(
        additionalPropertyType: TypeDefinition,
        typeBuilder: TypeSpec.Builder,
        constructorBuilder: FunSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {

        val type = additionalPropertyType.additionalPropertyType(
            nestedEnums,
            nestedClasses,
            knownTypes,
            packageName,
            rootTypeName
        )
        val constructorParamBuilder = ParameterSpec.builder(
            ADDITIONAL_PROPERTIES_NAME,
            MAP.parameterizedBy(STRING, type)
        )
        constructorParamBuilder.defaultValue("emptyMap()")
        constructorBuilder.addParameter(constructorParamBuilder.build())

        typeBuilder.addProperty(
            PropertySpec.builder(ADDITIONAL_PROPERTIES_NAME, MAP.parameterizedBy(STRING, type))
                .mutable(false)
                .initializer(ADDITIONAL_PROPERTIES_NAME)
                .build()
        )

        if (additionalPropertyType.description.isNotBlank()) {
            docBuilder.add(
                "@param $ADDITIONAL_PROPERTIES_NAME ${additionalPropertyType.description}\n"
            )
        }
    }

    /**
     * Appends a property to a [TypeSpec.Builder], with a constant default value.
     * @param name the property json name
     * @param definition the property definition
     * @param typeBuilder the `data class` [TypeSpec] builder.
     */
    private fun appendConstant(
        name: String,
        definition: TypeDefinition.Constant,
        typeBuilder: TypeSpec.Builder
    ) {
        val varName = name.variableName()
        val constantValue = definition.value
        val constantType = definition.type.asKotlinTypeName()
        val constantDefinitionForType = getKotlinValue(constantValue, definition.type)
        val propertyBuilder = PropertySpec.builder(varName, constantType)
            .initializer(constantDefinitionForType)

        if (definition.description.isNotBlank()) {
            propertyBuilder.addKdoc(definition.description)
        }

        typeBuilder.addProperty(propertyBuilder.build())
    }

    /**
     * Appends all properties to a [TypeSpec.Builder] from the given definition.
     * @param definition the definition to use.
     * @param typeBuilder the `data class` [TypeSpec] builder.
     * @param constructorBuilder the `data class` constructor builder.
     * @param docBuilder the `data class` KDoc builder.
     */
    private fun appendTypeDefinition(
        definition: TypeDefinition.Class,
        typeBuilder: TypeSpec.Builder,
        constructorBuilder: FunSpec.Builder,
        docBuilder: CodeBlock.Builder
    ) {
        if (definition.description.isNotBlank()) {
            docBuilder.add(definition.description)
            docBuilder.add("\n")
        }

        var nonConstants = 0

        definition.properties.forEach { p ->
            if (p.type is TypeDefinition.Constant) {
                appendConstant(p.name, p.type, typeBuilder)
            } else {
                nonConstants++
                appendProperty(
                    p,
                    typeBuilder,
                    constructorBuilder,
                    docBuilder
                )
            }
        }
        if (definition.additionalProperties != null) {
            nonConstants++
            appendAdditionalProperties(
                definition.additionalProperties,
                typeBuilder,
                constructorBuilder,
                docBuilder
            )
        }

        if (nonConstants > 0) {
            typeBuilder.addModifiers(KModifier.DATA)
        }

        typeBuilder.addFunction(serializerGenerator.generateClassSerializer(definition))
    }

    /**
     * Generates the value definition for a specific type [JsonPrimitiveType] or [JsonType].
     * @param constantValue
     * @param type the type of the value ]
     */
    private fun getKotlinValue(
        constantValue: Any?,
        type: Any?
    ): String {
        return if (constantValue is String) {
            "\"$constantValue\""
        } else if (constantValue is Double &&
            (type == JsonType.INTEGER || type == JsonPrimitiveType.INTEGER)
        ) {
            "${constantValue.toLong()}L"
        } else if (constantValue is Double) {
            "$constantValue"
        } else if (constantValue is Boolean) {
            "$constantValue"
        } else {
            throw IllegalStateException("Unable to generate constant type $type")
        }
    }

    private fun generateReservedPropertyNames(definition: TypeDefinition.Class): PropertySpec {

        val propertyNames = definition.properties
            .joinToString(", ") { "\"${it.name}\"" }

        val propertyBuilder = PropertySpec.builder(
            RESERVED_PROPERTIES_NAME,
            ARRAY.parameterizedBy(STRING),
            KModifier.PRIVATE
        ).initializer("arrayOf($propertyNames)")
        return propertyBuilder.build()
    }

    // endregion

    companion object {
        const val EXTENSION_FUNCTIONS_PACKAGE_NAME = "com.datadog.android.core.internal.utils"
        const val TO_JSON_ELEMENT_EXTENSION_FUNCTION = "toJsonElement"
        const val ENUM_CONSTRUCTOR_JSON_VALUE_NAME = "jsonValue"
        const val ADDITIONAL_PROPERTIES_NAME = "additionalProperties"
        const val RESERVED_PROPERTIES_NAME = "RESERVED_PROPERTIES"
    }
}
