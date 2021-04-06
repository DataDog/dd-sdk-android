/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.datadog.tools.annotation.NoOpImplementation
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.NoType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.StandardLocation
import org.jetbrains.annotations.Nullable
import javax.lang.model.element.VariableElement

@SupportedOptions("org.gradle.annotation.processing.aggregating")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("com.datadog.tools.annotation.NoOpImplementation")
class NoOpFactoryAnnotationProcessor : AbstractProcessor() {

    // region Processor

    override fun process(
        annotations: MutableSet<out TypeElement>?,
        roundEnv: RoundEnvironment
    ): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(NoOpImplementation::class.java)
        if (annotatedElements.isEmpty()) return false

        val kaptKotlinGeneratedDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
        if (kaptKotlinGeneratedDir == null) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Can't find the target directory for generated Kotlin files."
            )
            return false
        }

        for (element in annotatedElements) {
            val typeElement = element.toTypeElementOrNull() ?: continue
            generateNoOpImplementation(typeElement)
        }

        return true
    }

    // endregion

    // region Internal

    /**
     * Generates a File with the target NoOpImplementation
     */
    private fun generateNoOpImplementation(
        typeElement: TypeElement
    ) {
        if (typeElement.kind != ElementKind.INTERFACE) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Unable to generate a NoOpImplementation for ${typeElement.simpleName}, " +
                    "it is not an interface."
            )
            return
        }

        val packageName = typeElement.qualifiedName.toString().substringBeforeLast('.')
        val typeSpec = generateTypeSpec(typeElement, packageName)

        val className = typeSpec.name.orEmpty()
        val file = FileSpec.builder(packageName, className)
            .addType(typeSpec)
            .indent("    ")
            .build()

        val kotlinFileObject = processingEnv.filer
            .createResource(
                StandardLocation.SOURCE_OUTPUT,
                packageName,
                "$className.kt",
                typeElement
            )
        val writer = kotlinFileObject.openWriter()
        file.writeTo(writer)
        writer.close()

        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "Generated ${file.name}\r\n")
    }

    /**
     * Generate the Class Implementation based on the given Parent interface
     */
    private fun generateTypeSpec(
        typeElement: TypeElement,
        packageName: String
    ): TypeSpec {
        val interfaceName = typeElement.simpleName.toString()
        val noOpName = "NoOp$interfaceName"

        val typeSpecBuilder = TypeSpec.classBuilder(noOpName)
            .addModifiers(KModifier.INTERNAL)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "RedundantUnitReturnType")
                    .build()
            )

        generateSuperTypeDeclaration(typeSpecBuilder, typeElement, packageName, interfaceName)

        generateParentInterfacesImplementation(typeSpecBuilder, typeElement)

        return typeSpecBuilder.build()
    }

    /**
     * Declares the supertype.
     * If the supertype is parameterized (eg: Foo<T:CharSequence>),
     * the NoOp implementation will copy the parameterization.
     */
    private fun generateSuperTypeDeclaration(
        typeSpecBuilder: TypeSpec.Builder,
        typeElement: TypeElement,
        packageName: String,
        interfaceName: String
    ) {

        val params = typeElement.typeParameters
        if (params.isEmpty()) {
            typeSpecBuilder.addSuperinterface(ClassName(packageName, interfaceName))
        } else {
            val typeVariables = mutableListOf<TypeVariableName>()
            params.forEach {
                val alias = it.simpleName.toString()
                val bounds = it.bounds.map { it.asKotlinTypeName() }
                val variableName = TypeVariableName.invoke(alias, bounds)
                typeVariables.add(variableName)
            }
            typeSpecBuilder.addSuperinterface(
                ClassName(packageName, interfaceName).parameterizedBy(typeVariables)
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
    private fun generateParentInterfacesImplementation(
        typeSpecBuilder: TypeSpec.Builder,
        typeElement: TypeElement
    ) {
        val interfaces = mutableListOf(typeElement)
        val executableElements: MutableMap<String, ExecutableElement> = mutableMapOf()
        while (interfaces.isNotEmpty()) {
            val interfaceType = interfaces.removeAt(0)
            if (interfaceType.kind == ElementKind.INTERFACE) {
                fetchExecutableEnclosedElements(interfaceType, executableElements)

                interfaceType.interfaces.forEach {
                    val element = processingEnv.typeUtils.asElement(it).toTypeElementOrNull()
                    if (element != null) {
                        interfaces.add(element)
                    }
                }
            }
        }
        executableElements.values.forEach {
            typeSpecBuilder.addFunction(generateFunctionImplementation(it))
        }
    }

    /**
     * Updates the executable element map with the newly found enclosed executable elements
     * in this interface avoiding the duplicates.
     */
    private fun fetchExecutableEnclosedElements(
        typeElement: TypeElement,
        executableElements: MutableMap<String, ExecutableElement>
    ) {
        typeElement.enclosedElements.forEach {
            // TODO RUMM-000 implement kotlin properties ?
            if (it is ExecutableElement &&
                !executableElements.containsKey(it.id())
            ) {
                executableElements[it.id()] = it
            }
        }
    }

    /**
     * Generates the implementation for a given method.
     */
    private fun generateFunctionImplementation(element: ExecutableElement): FunSpec {
        val funSpecBuilder = FunSpec.builder(element.simpleName.toString())
            .addModifiers(KModifier.OVERRIDE)

        // add parameters
        val params = element.parameters
        params.forEachIndexed { _, param ->
            val paramType = param.asType().asKotlinTypeName()
            val isNullable = param.getAnnotation(Nullable::class.java) != null
            funSpecBuilder.addParameter(
                param.simpleName.toString(),
                paramType.copy(isNullable)
            )
        }

        val returnType = element.returnType
        if (returnType !is NoType) {
            val isReturnNullable = element.getAnnotation(Nullable::class.java) != null
            generateFunctionReturnStatement(funSpecBuilder, returnType, params, isReturnNullable)
        }

        return funSpecBuilder.build()
    }

    /**
     * Generates the return statement for a given method.
     *
     * This will use sensible default values, using the following rules :
     *  - if the return type is nullable, the method will return null
     *  - if the return type is a primitive, the method will return 0/false
     *  - if the return type is a String, the method will return an empty String
     *  - if the return type is an enum, the method will return the first enum constant
     *  - if the return type is an interface, it will check if it known SDK collection interface
     *  (one of [Map], [List], [Set]), otherwise assume a NoOp implementation exist
     *  - otherwise it will assume a default constructor for the given type exists.
     */
    private fun generateFunctionReturnStatement(
        funSpecBuilder: FunSpec.Builder,
        returnType: TypeMirror,
        functionParams: List<VariableElement>,
        isReturnNullable: Boolean
    ) {

        val type = returnType.asKotlinTypeName().copy(isReturnNullable)
        funSpecBuilder.returns(type)
        if (!isReturnNullable && functionParams.isNotEmpty()) {
            functionParams.forEach {
                if (it.asType() == returnType) {
                    funSpecBuilder.addStatement("return %L", functionParams[0].simpleName)
                    return
                }
            }
        }
        val returnTypeDef = processingEnv.typeUtils.asElement(returnType)
        when {
            type.isNullable -> funSpecBuilder.addStatement("return null")
            type == BOOLEAN -> funSpecBuilder.addStatement("return false")
            type == INT -> funSpecBuilder.addStatement("return 0")
            type == LONG -> funSpecBuilder.addStatement("return 0L")
            type == FLOAT -> funSpecBuilder.addStatement("return 0.0f")
            type == DOUBLE -> funSpecBuilder.addStatement("return 0.0")
            type == STRING -> funSpecBuilder.addStatement("return \"\"")
            returnTypeDef.kind == ElementKind.ENUM -> {
                val firstValue = returnTypeDef.enclosedElements.first {
                    it.kind == ElementKind.ENUM_CONSTANT
                }
                funSpecBuilder.addStatement("return %T.${firstValue.simpleName}", type)
            }
            returnTypeDef.kind == ElementKind.INTERFACE -> {
                val returnTypeElement = returnTypeDef as TypeElement
                when (returnTypeElement.qualifiedName.toString()) {
                    List::class.java.name -> {
                        funSpecBuilder.addStatement(
                            "return %M()",
                            MemberName(KOTLIN_COLLECTIONS_PACKAGE, "emptyList")
                        )
                    }
                    Map::class.java.name -> {
                        funSpecBuilder.addStatement(
                            "return %M()",
                            MemberName(KOTLIN_COLLECTIONS_PACKAGE, "emptyMap")
                        )
                    }
                    Set::class.java.name -> {
                        funSpecBuilder.addStatement(
                            "return %M()",
                            MemberName(KOTLIN_COLLECTIONS_PACKAGE, "emptySet")
                        )
                    }
                    else -> {
                        val packageName = returnTypeElement.qualifiedName
                            .toString()
                            .substringBeforeLast('.')
                        val noOpReturnType =
                            ClassName(packageName, "NoOp${returnTypeDef.simpleName}")
                        funSpecBuilder.addStatement("return %T()", noOpReturnType)
                    }
                }
            }
            else -> funSpecBuilder.addStatement("return %T()", type)
        }
    }

    private fun ExecutableElement.id(): String {
        val s = this.simpleName.toString() +
            this.parameters
                .map {
                    it.simpleName
                }
                .joinToString(separator = ":")
        return s
    }
    // endregion

    companion object {
        const val KOTLIN_COLLECTIONS_PACKAGE = "kotlin.collections"
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}
