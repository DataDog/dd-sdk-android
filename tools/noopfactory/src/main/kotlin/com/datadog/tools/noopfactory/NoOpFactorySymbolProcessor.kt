/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.datadog.tools.annotation.NoOpImplementation
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSCallableReference
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.TypeParameterResolver
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.file.FileAlreadyExistsException

/**
 * A [SymbolProcessor] generating a no-op implementation of interfaces annotated with
 * @[NoOpImplementation].
 */
@Suppress("TooManyFunctions")
class NoOpFactorySymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private var invoked = false
    private val generatedNames = mutableMapOf<String, KSClassDeclaration>()

    /** @inheritdoc */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) {
            logger.info("Already invoked, ignoring")
            return emptyList()
        }
        val result = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(NoOpImplementation::class.java.canonicalName)
            .filterIsInstance<KSClassDeclaration>()
            .forEach {
                when {
                    it.classKind != ClassKind.INTERFACE -> it.warnCannotGenerate("it is not an interface.")
                    it.isLocal() -> it.warnCannotGenerate("local interfaces are not supported.")
                    it.hasRestrictedVisibility() -> it.warnCannotGenerate(
                        "it or one of its enclosing classes has private or protected visibility."
                    )
                    else -> {
                        generateNoOpImplementation(it)
                        result.add(it)
                    }
                }
            }

        invoked = true
        return result
    }

    /**
     * Whether this declaration is an opt-in marker, i.e. an annotation class itself annotated with
     * [RequiresOptIn]. This is checked on the declaration directly rather than by collecting every marker
     * in the round up front: `Resolver.getSymbolsWithAnnotation` doesn't see markers declared in
     * dependencies, and comparing `KSType` instances resolved through different paths is unreliable.
     */
    private fun KSDeclaration.isRequiresOptInMarker(): Boolean {
        if ((this as? KSClassDeclaration)?.classKind != ClassKind.ANNOTATION_CLASS) return false
        return annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == REQUIRES_OPT_IN_NAME
        }
    }

    // region Internal Generation

    @OptIn(KspExperimental::class)
    private fun generateNoOpImplementation(interfaceDeclaration: KSClassDeclaration) {
        logger.logging("Generating NoOp for ${interfaceDeclaration.simpleName.getShortName()}")
        val annotation = interfaceDeclaration.getAnnotationsByType(NoOpImplementation::class).firstOrNull()
            ?: return
        val publicNoOpImplementation = safeAnnotationArg(false) { annotation.publicNoOpImplementation }
        val customName = safeAnnotationArg("") { annotation.customName }

        val packageName = interfaceDeclaration.packageName.asString()
        val className = resolveNoOpClassName(interfaceDeclaration, customName, packageName) ?: return

        val declarationSourceFile = interfaceDeclaration.containingFile
        val typeSpec = generateTypeSpec(interfaceDeclaration, publicNoOpImplementation, className)
        val fileSpec = FileSpec.builder(packageName, className)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "ktlint")
                    .apply {
                        interfaceDeclaration.getAllSuperTypes()
                            .map { it.declaration.containingFile }
                            .let { it.plus(declarationSourceFile) }
                            .filterNotNull()
                            .flatMap { it.getAnnotationsByType(Suppress::class).flatMap { it.names.toList() } }
                            .toSet()
                            .forEach { addMember("%S", it) }
                    }
                    .build()
            )
            .addType(typeSpec)
            .indent("    ")
            .build()
        val dependencies = if (declarationSourceFile == null) {
            Dependencies.ALL_FILES
        } else {
            Dependencies(false, declarationSourceFile)
        }
        writeNoOpFile(interfaceDeclaration, fileSpec, dependencies, className, customName)
    }

    private fun resolveNoOpClassName(
        interfaceDeclaration: KSClassDeclaration,
        customName: String,
        packageName: String
    ): String? {
        val isCustomName = customName.isNotEmpty()

        if (isCustomName && !customName.matches(VALID_KOTLIN_IDENTIFIER)) {
            logger.error(
                "customName \"$customName\" for '${interfaceDeclaration.qualifiedName?.asString()}' " +
                    "is not a valid Kotlin class name. Use only letters, digits, and underscores, " +
                    "starting with a letter or underscore."
            )
            return null
        }

        val className = customName.ifEmpty { interfaceDeclaration.defaultNoOpName() }
        val qualifiedGeneratedName = "$packageName.$className"
        val existing = generatedNames[qualifiedGeneratedName]

        return if (existing != null) {
            reportNameCollision(interfaceDeclaration, existing, className, isCustomName)
            null
        } else {
            generatedNames[qualifiedGeneratedName] = interfaceDeclaration
            className
        }
    }

    private fun reportNameCollision(
        interfaceDeclaration: KSClassDeclaration,
        existing: KSClassDeclaration,
        className: String,
        isCustomName: Boolean
    ) {
        if (isCustomName) {
            logger.error(
                "customName \"$className\" for '${interfaceDeclaration.qualifiedName?.asString()}' " +
                    "conflicts with the NoOp already generated for '${existing.qualifiedName?.asString()}'. " +
                    "Choose a different customName."
            )
        } else {
            logger.error(
                "NoOp name collision: both '${existing.qualifiedName?.asString()}' and " +
                    "'${interfaceDeclaration.qualifiedName?.asString()}' would generate '$className'. " +
                    "Use @NoOpImplementation(customName = \"...\") on one of them to resolve the conflict."
            )
        }
    }

    @Suppress("SwallowedException")
    private fun writeNoOpFile(
        interfaceDeclaration: KSClassDeclaration,
        fileSpec: FileSpec,
        dependencies: Dependencies,
        className: String,
        customName: String
    ) {
        val isCustomName = customName.isNotEmpty()
        val qualifiedGeneratedName = "${fileSpec.packageName}.$className"
        try {
            val outputStream = codeGenerator.createNewFile(dependencies, fileSpec.packageName, fileSpec.name)
            val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
            fileSpec.writeTo(writer)
            try {
                writer.flush()
                writer.close()
            } catch (e: IOException) {
                logger.warn("Error flushing writer for file ${fileSpec.packageName}.${fileSpec.name}: ${e.message}.")
            }
            logger.logging("✔ Wrote $className")
        } catch (e: FileAlreadyExistsException) {
            val fqn = interfaceDeclaration.qualifiedName?.asString()
            if (isCustomName) {
                logger.error(
                    "customName \"$className\" for '$fqn' conflicts with an existing file " +
                        "'$qualifiedGeneratedName'. Choose a different customName or rename the existing file."
                )
            } else {
                logger.error(
                    "Cannot generate '$qualifiedGeneratedName': file already exists. " +
                        "If you created this file manually, rename it or use " +
                        "@NoOpImplementation(customName = \"...\") on '$fqn'."
                )
            }
        } catch (e: IOException) {
            logger.error("Error writing file ${fileSpec.packageName}.${fileSpec.name}")
            logger.exception(e)
        }
    }

    /**
     * Generate the Class Implementation based on the given Parent interface.
     */
    private fun generateTypeSpec(
        declaration: KSClassDeclaration,
        publicNoOpImplementation: Boolean,
        className: String
    ): TypeSpec {
        val typeSpecBuilder = TypeSpec.classBuilder(className)
            .addModifiers(if (publicNoOpImplementation) KModifier.PUBLIC else KModifier.INTERNAL)

        generateSuperTypeDeclaration(typeSpecBuilder, declaration)

        generateParentInterfacesImplementation(typeSpecBuilder, declaration)

        return typeSpecBuilder.build()
    }

    /**
     * Declares the supertype.
     * If the supertype is parameterized (eg: Foo<T:CharSequence>),
     * the NoOp implementation will copy the parameterization.
     */
    private fun generateSuperTypeDeclaration(
        typeSpecBuilder: TypeSpec.Builder,
        declaration: KSClassDeclaration
    ) {
        // toClassName() builds the full nested class reference (e.g., Outer.Inner) so that
        // a NoOp for a nested interface correctly extends Outer.Inner, not just Inner.
        val interfaceClassName = declaration.toClassName()
        val params = declaration.typeParameters
        if (params.isEmpty()) {
            typeSpecBuilder.addSuperinterface(interfaceClassName)
        } else {
            val typeVariables = mutableListOf<TypeVariableName>()
            params.forEach { param ->
                val alias = param.simpleName.asString()
                val bounds = param.bounds.map { it.toTypeName() }.toList()
                val variableName = TypeVariableName.invoke(alias, bounds)
                typeVariables.add(variableName)
            }

            typeSpecBuilder.addSuperinterface(
                interfaceClassName.parameterizedBy(typeVariables)
            )
                .addTypeVariables(typeVariables)
        }
    }

    /**
     * Generates the parent interfaces implementations.
     *
     * This method will iterate over all the transitive parent interfaces and generate the
     * appropriate implementation. This ensures that the following source :
     *
     * ```kotlin
     * interface A { fun doA() }
     *
     * interface B : A { fun doB) }
     *
     * @NoOpImplementation
     * interface C : B { fun doC) }
     * ```
     *
     * will generate the following class :
     *
     * ```kotlin
     * class NoOpC : C {
     *   fun doC() {}
     *   fun doB() {}
     *   fun doA() {}
     * }
     * ```
     */
    @Suppress("FunctionMaxLength")
    private fun generateParentInterfacesImplementation(
        typeSpecBuilder: TypeSpec.Builder,
        declaration: KSClassDeclaration
    ) {
        val interfaces = mutableListOf(declaration)
        val functions: MutableMap<String, KSFunctionDeclaration> = mutableMapOf()
        val properties = mutableMapOf<String, KSPropertyDeclaration>()
        val typeParamResolver = declaration.typeParameters.toTypeParameterResolver()
        while (interfaces.isNotEmpty()) {
            generateFirstInterfaceImplementation(interfaces, functions, properties, typeParamResolver)
        }

        functions.values.forEach {
            typeSpecBuilder.addFunction(generateFunctionImplementation(it, typeParamResolver))
        }
        properties.values.forEach {
            typeSpecBuilder.addProperty(generatePropertyImplementation(it, typeParamResolver))
        }
    }

    @Suppress("FunctionMaxLength")
    private fun generateFirstInterfaceImplementation(
        interfaces: MutableList<KSClassDeclaration>,
        functions: MutableMap<String, KSFunctionDeclaration>,
        properties: MutableMap<String, KSPropertyDeclaration>,
        typeParamResolver: TypeParameterResolver
    ) {
        val interfaceType = interfaces.removeAt(0)
        if (interfaceType.classKind == ClassKind.INTERFACE) {
            fetchInterfaceFunctions(interfaceType, functions, typeParamResolver)
            fetchInterfaceProperties(interfaceType, properties)

            interfaceType.superTypes.forEach {
                val superDeclaration = it.resolve().declaration
                if (superDeclaration is KSClassDeclaration &&
                    superDeclaration.classKind == ClassKind.INTERFACE
                ) {
                    interfaces.add(superDeclaration)
                }
            }
        }
    }

    /**
     * Updates the executable element map with the newly found enclosed executable elements
     * in this interface avoiding the duplicates.
     */
    private fun fetchInterfaceFunctions(
        declaration: KSClassDeclaration,
        executableElements: MutableMap<String, KSFunctionDeclaration>,
        typeParamResolver: TypeParameterResolver
    ) {
        declaration.getAllFunctions().forEach {
            // hack for the case when we process a function from super definition, which was already
            // seen and which has a generic parameters - it will need a typeResolver from super
            // and will have a different ID, but essentially we already saw everything
            // needed to implement this function
            if (executableElements.values.any { seen -> seen.findOverridee() == it }) {
                return@forEach
            }
            val id = it.identifier(typeParamResolver)
            if ((id !in ignoredFunctions) && !executableElements.containsKey(id)) {
                executableElements[id] = it
            }
        }
    }

    /**
     * Updates the property element map with the newly found enclosed properties
     * in this interface avoiding the duplicates.
     */
    private fun fetchInterfaceProperties(
        declaration: KSClassDeclaration,
        executableElements: MutableMap<String, KSPropertyDeclaration>
    ) {
        declaration.getAllProperties().forEach {
            val id = it.identifier()
            if (!executableElements.containsKey(id)) {
                executableElements[id] = it
            }
        }
    }

    /**
     * Generates the implementation for a given method.
     */
    @OptIn(KspExperimental::class)
    private fun generateFunctionImplementation(
        functionDeclaration: KSFunctionDeclaration,
        typeParamResolver: TypeParameterResolver = TypeParameterResolver.EMPTY
    ): FunSpec {
        val funSpecBuilder = FunSpec.builder(functionDeclaration.simpleName.asString())
            .addModifiers(KModifier.OVERRIDE)

        // add parameters
        val params = functionDeclaration.parameters
        params.forEachIndexed { i, param ->
            val paramType = param.type.resolve()
            funSpecBuilder.addParameter(
                param.name?.asString() ?: "p$i",
                paramType.toTypeName(typeParamResolver)
            )
        }

        // add Deprecated annotation, which has a special handling during compile-time
        val deprecatedAnnotation = functionDeclaration
            .getAnnotationsByType(Deprecated::class)
            .firstOrNull()

        if (deprecatedAnnotation != null) {
            funSpecBuilder.addAnnotation(
                AnnotationSpec.builder(Deprecated::class)
                    .addMember("%S", deprecatedAnnotation.message)
                    .build()
            )
        }

        // check if the function has any RequiresOptIn annotations and add OptIn annotation for the override function
        functionDeclaration
            .annotations
            .filter { it.annotationType.resolve().declaration.isRequiresOptInMarker() }
            .forEach {
                funSpecBuilder.addAnnotation(
                    AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                        .addMember("%T::class", it.annotationType.resolve().toTypeName())
                        .build()
                )
            }
        val returnType = functionDeclaration.returnType?.resolve()
        if (returnType != null) {
            generateFunctionReturnStatement(funSpecBuilder, returnType, params, typeParamResolver)
        } else {
            logger.warn("return type was null for ${functionDeclaration.qualifiedName?.asString()}")
        }

        return funSpecBuilder.build()
    }

    /**
     * Generates the implementation for a given property.
     */
    private fun generatePropertyImplementation(
        propertyDeclaration: KSPropertyDeclaration,
        typeParamResolver: TypeParameterResolver = TypeParameterResolver.EMPTY
    ): PropertySpec {
        val propertySpecBuilder = PropertySpec
            .builder(
                propertyDeclaration.simpleName.asString(),
                propertyDeclaration.type.resolve().toTypeName(typeParamResolver)
            )
            .addModifiers(KModifier.OVERRIDE)
            .mutable(propertyDeclaration.isMutable)

        generatePropertyInitializerStatement(
            propertySpecBuilder,
            propertyDeclaration.type.resolve(),
            typeParamResolver
        )

        return propertySpecBuilder.build()
    }

    /**
     * Generates the return statement for a given method.
     *
     * This will use sensible default values, using the following rules :
     *  - if the return type is nullable, the method will return null
     *  - if the return type is a primitive, the method will return 0/false
     *  - if the return type is a String, the method will return an empty String
     *  - if the return type is an enum, the method will return the first enum constant
     *  - if the return type is an interface, it will check if it is known SDK collection interface
     *  (one of [Map], [List], [Set]), otherwise assume a NoOp implementation exist
     *  - otherwise it will assume a default constructor for the given type exists.
     */
    @Suppress("LongMethod", "FunctionMaxLength")
    private fun generateFunctionReturnStatement(
        funSpecBuilder: FunSpec.Builder,
        returnType: KSType,
        functionParams: List<KSValueParameter>,
        typeParamResolver: TypeParameterResolver
    ) {
        val returnTypeName = returnType.toTypeName(typeParamResolver)
        val returnClassDeclaration = returnType.declaration as? KSClassDeclaration
        val returnClassKind = returnClassDeclaration?.classKind
        val rawTypeName = (returnTypeName as? ParameterizedTypeName)?.rawType ?: returnTypeName

        val matchingParamName = functionParams.firstOrNull { param ->
            param.type.resolve() == returnType
        }?.name?.asString()

        funSpecBuilder.returns(returnTypeName)
        val returnNewInstance = "return %M()"
        when {
            returnTypeName.isNullable -> funSpecBuilder.addStatement("return null")
            returnTypeName == BOOLEAN -> funSpecBuilder.addStatement("return false")
            returnTypeName == INT -> funSpecBuilder.addStatement("return 0")
            returnTypeName == LONG -> funSpecBuilder.addStatement("return 0L")
            returnTypeName == FLOAT -> funSpecBuilder.addStatement("return 0.0f")
            returnTypeName == DOUBLE -> funSpecBuilder.addStatement("return 0.0")
            returnTypeName == STRING -> funSpecBuilder.addStatement("return \"\"")
            returnTypeName == UNIT -> {}
            rawTypeName == LIST -> funSpecBuilder.addStatement(
                returnNewInstance,
                MemberName(
                    KOTLIN_COLLECTIONS_PACKAGE,
                    "emptyList"
                )
            )

            rawTypeName == MAP -> funSpecBuilder.addStatement(
                returnNewInstance,
                MemberName(
                    KOTLIN_COLLECTIONS_PACKAGE,
                    "emptyMap"
                )
            )

            rawTypeName == SET -> funSpecBuilder.addStatement(
                returnNewInstance,
                MemberName(
                    KOTLIN_COLLECTIONS_PACKAGE,
                    "emptySet"
                )
            )

            matchingParamName != null && !returnTypeName.isNullable -> {
                funSpecBuilder.addStatement("return %L", matchingParamName)
            }

            returnClassKind == ClassKind.ENUM_CLASS -> {
                val firstValue = returnClassDeclaration.declarations.firstOrNull {
                    (it as? KSClassDeclaration)?.classKind == ClassKind.ENUM_ENTRY
                }

                if (firstValue != null) {
                    funSpecBuilder.addStatement(
                        "return %T.${firstValue.simpleName.asString()}",
                        returnTypeName
                    )
                } else {
                    logger.error(
                        "Unable to find value for ${returnClassDeclaration.simpleName.asString()}"
                    )
                }
            }

            returnClassKind == ClassKind.INTERFACE && !returnType.isFunctionTypeOrAlias -> {
                // NoOp implementations are always generated at the package root, never nested.
                // The name concatenates all enclosing class names to avoid collisions
                // (e.g. Outer.Inner → NoOpOuterInner, not Outer.NoOpInner).
                val noOpReturnType = ClassName(
                    returnClassDeclaration.packageName.asString(),
                    returnClassDeclaration.noOpName()
                )
                funSpecBuilder.addStatement("return %T()", noOpReturnType)
            }

            returnType.isFunctionTypeOrAlias -> {
                val funcReturnTypeName = returnType.returnTypeNameOfFunctionType(typeParamResolver)
                if (funcReturnTypeName == UNIT) {
                    funSpecBuilder.addStatement("return {}")
                } else {
                    logger.error(
                        "Unable to generate return lambda for" +
                            " function type ${returnType.declaration.simpleName.asString()}"
                    )
                }
            }

            else -> {
                funSpecBuilder.addStatement("return %T()", returnTypeName)
            }
        }
    }

    // TODO RUMM-0000 There is some duplication in the function vs property statement declaration code
    /**
     * Generates the initialization for a given property.
     *
     * This will use sensible default values, using the following rules :
     *  - if the property type is nullable, the property will be null
     *  - if the property type is a primitive, the property will be 0/false
     *  - if the property type is a String, the property will be an empty String
     *  - if the property type is an enum, the property will be the first enum constant
     *  - if the property type is an interface, it will check if it is known SDK collection interface
     *  (one of [Map], [List], [Set]), otherwise assume a NoOp implementation exist
     *  - otherwise it will assume a default constructor for the given type exists.
     */
    @Suppress("LongMethod", "FunctionMaxLength")
    private fun generatePropertyInitializerStatement(
        propertySpecBuilder: PropertySpec.Builder,
        propertyType: KSType,
        typeParamResolver: TypeParameterResolver
    ) {
        val propertyTypeName = propertyType.toTypeName(typeParamResolver)
        val propertyClassDeclaration = propertyType.declaration as? KSClassDeclaration
        val propertyClassKind = propertyClassDeclaration?.classKind
        val rawTypeName = (propertyTypeName as? ParameterizedTypeName)?.rawType ?: propertyTypeName

        val newInstance = "%M()"
        when {
            propertyTypeName.isNullable -> propertySpecBuilder.initializer("null")
            propertyTypeName == BOOLEAN -> propertySpecBuilder.initializer("false")
            propertyTypeName == INT -> propertySpecBuilder.initializer("0")
            propertyTypeName == LONG -> propertySpecBuilder.initializer("0L")
            propertyTypeName == FLOAT -> propertySpecBuilder.initializer("0.0f")
            propertyTypeName == DOUBLE -> propertySpecBuilder.initializer("0.0")
            propertyTypeName == STRING -> propertySpecBuilder.initializer("\"\"")
            rawTypeName == LIST -> propertySpecBuilder.initializer(
                newInstance,
                MemberName(
                    KOTLIN_COLLECTIONS_PACKAGE,
                    "emptyList"
                )
            )

            rawTypeName == MAP -> propertySpecBuilder.initializer(
                newInstance,
                MemberName(
                    KOTLIN_COLLECTIONS_PACKAGE,
                    "emptyMap"
                )
            )

            rawTypeName == SET -> propertySpecBuilder.initializer(
                newInstance,
                MemberName(
                    KOTLIN_COLLECTIONS_PACKAGE,
                    "emptySet"
                )
            )

            propertyClassKind == ClassKind.ENUM_CLASS -> {
                val firstValue = propertyClassDeclaration.declarations.firstOrNull {
                    (it as? KSClassDeclaration)?.classKind == ClassKind.ENUM_ENTRY
                }

                if (firstValue != null) {
                    propertySpecBuilder.initializer(
                        "%T.${firstValue.simpleName.asString()}",
                        propertyTypeName
                    )
                } else {
                    logger.error(
                        "Unable to find value for ${propertyClassDeclaration.simpleName.asString()}"
                    )
                }
            }

            propertyClassKind == ClassKind.INTERFACE -> {
                // NoOp implementations are always generated at the package root, never nested.
                // The name concatenates all enclosing class names to avoid collisions
                // (e.g. Outer.Inner → NoOpOuterInner, not Outer.NoOpInner).
                val noOpType = ClassName(
                    propertyClassDeclaration.packageName.asString(),
                    propertyClassDeclaration.noOpName()
                )
                propertySpecBuilder.initializer("%T()", noOpType)
            }

            else -> {
                propertySpecBuilder.initializer("%T()", propertyTypeName)
            }
        }
    }

    /**
     * KSP1 expanded typealiases when resolving a [KSType], so a `typealias Foo = (A) -> Unit`
     * reported `isFunctionType == true`. KSP2 keeps the alias, so the check has to look through it.
     */
    private val KSType.isFunctionTypeOrAlias: Boolean
        get() = isFunctionType ||
            (declaration as? KSTypeAlias)?.type?.resolve()?.isFunctionTypeOrAlias == true

    /**
     * @return the identifier name of the [KSFunctionDeclaration]
     */
    private fun KSFunctionDeclaration.identifier(typeParamResolver: TypeParameterResolver): String {
        return simpleName.asString() + parameters.joinToString(",", "(", ")") {
            val name = it.name?.asString() ?: "?"
            val type = it.type.resolve().toTypeName(typeParamResolver)
            "$name:$type"
        }
    }

    private fun KSType.returnTypeNameOfFunctionType(
        typeParamResolver: TypeParameterResolver
    ): TypeName? {
        return if (declaration is KSClassDeclaration) {
            (toTypeName(typeParamResolver) as ParameterizedTypeName)
                .typeArguments
                .first()
        } else if (declaration is KSTypeAlias) {
            val refElement = (declaration as KSTypeAlias)
                .type
                .element as? KSCallableReference
            refElement?.returnType?.toTypeName(typeParamResolver)
        } else {
            null
        }
    }

    /**
     * @return the identifier name of the [KSPropertyDeclaration]
     */
    private fun KSPropertyDeclaration.identifier(): String = simpleName.asString()

    /**
     * @return the NoOp class name for this interface, concatenating all enclosing class names
     * to avoid collisions between same-named interfaces in different outer classes
     * (e.g. `Outer.Inner` → `NoOpOuterInner`, `OuterOuter.Outer.Inner` → `NoOpOuterOuterOuterInner`).
     */
    private fun KSClassDeclaration.warnCannotGenerate(reason: String) {
        logger.warn(
            "Unable to generate a NoOpImplementation for ${simpleName.asString()}, $reason"
        )
    }

    private fun KSClassDeclaration.hasRestrictedVisibility(): Boolean {
        var current: KSClassDeclaration? = this
        while (current != null) {
            if (Modifier.PRIVATE in current.modifiers || Modifier.PROTECTED in current.modifiers) {
                return true
            }
            current = current.parentDeclaration as? KSClassDeclaration
        }
        return false
    }

    // endregion

    companion object {
        private val ignoredFunctions = arrayOf("equals(other:kotlin.Any?)", "hashCode()", "toString()")
        private const val KOTLIN_COLLECTIONS_PACKAGE = "kotlin.collections"
        private const val REQUIRES_OPT_IN_NAME = "kotlin.RequiresOptIn"
        private val VALID_KOTLIN_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }
}
