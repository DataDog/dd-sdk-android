/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import kotlin.reflect.jvm.internal.impl.name.FqName
import org.jetbrains.annotations.Nullable

/**
 * Converts a TypeMirror (APT type) into a Kotlin Type Name (Kotlin Poet type).
 */
internal fun TypeMirror.asKotlinTypeName(): TypeName {

    val isNullable = getAnnotation(Nullable::class.java) != null
    val typeName = accept(TypeMirrorToKotlinTypeNameVisitor(), null)
    return typeName.copy(isNullable).javaToKotlinType()
}

/**
 * Converts well known java classes to their Kotlin counterparts.
 * eg: java.lang.String -> kotlin.String
 *
 * This ensures that generated code is compatible with super-interface definitions
 */
internal fun TypeName.javaToKotlinType(): TypeName {
    return when (this) {
        is ParameterizedTypeName -> {
            val rawKotlinType = rawType.javaToKotlinType() as ClassName
            rawKotlinType.parameterizedBy(
                *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
            )
        }
        is WildcardTypeName -> {
            if (inTypes.isNotEmpty()) {
                inTypes[0].javaToKotlinType()
            } else {
                outTypes[0].javaToKotlinType()
            }
        }

        else -> {
            // Use the JavaToKotlinClassMap to do the conversion
            val fqName = FqName(toString())
            val className = JavaToKotlinClassMap.INSTANCE
                .mapJavaToKotlin(fqName)?.asSingleFqName()?.asString()
            if (className == null) {
                this
            } else {
                ClassName.bestGuess(className)
            }
        }
    }
}

/**
 * Converts an APT element to a TypeElement
 */
internal fun Element.toTypeElementOrNull(): TypeElement? {
    if (this !is TypeElement) {
        return null
    }

    return this
}
