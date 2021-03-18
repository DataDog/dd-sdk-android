/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BOOLEAN_ARRAY
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.CHAR_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.DOUBLE_ARRAY
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FLOAT_ARRAY
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.SHORT_ARRAY
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ErrorType
import javax.lang.model.type.NoType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.lang.model.type.WildcardType
import javax.lang.model.util.SimpleTypeVisitor8

/**
 * Code mostly inspired by KotlinPoet, adapted to avoid some nullability issues
 */
internal class TypeMirrorToKotlinTypeNameVisitor : SimpleTypeVisitor8<TypeName, Void?>() {

    /**
     * Visits a primitive type
     */
    override fun visitPrimitive(t: PrimitiveType, p: Void?): TypeName {
        return when (t.kind) {
            TypeKind.BOOLEAN -> BOOLEAN
            TypeKind.BYTE -> BYTE
            TypeKind.SHORT -> SHORT
            TypeKind.INT -> INT
            TypeKind.LONG -> LONG
            TypeKind.CHAR -> CHAR
            TypeKind.FLOAT -> FLOAT
            TypeKind.DOUBLE -> DOUBLE
            else -> throw AssertionError()
        }
    }

    /**
     * Visits an array (X[]).
     * This will convert to Kotlin's primitive arrays if possible
     */
    override fun visitArray(t: ArrayType, p: Void?): TypeName {
        val typeArgument = t.componentType.asKotlinTypeName()
        return when (typeArgument) {
            BOOLEAN -> BOOLEAN_ARRAY
            BYTE -> BYTE_ARRAY
            SHORT -> SHORT_ARRAY
            INT -> INT_ARRAY
            LONG -> LONG_ARRAY
            CHAR -> CHAR_ARRAY
            FLOAT -> FLOAT_ARRAY
            DOUBLE -> DOUBLE_ARRAY
            else -> ARRAY.parameterizedBy(typeArgument)
        }
    }

    /**
     * Visits an Error type (in throw declarations / statements).
     */
    override fun visitError(t: ErrorType, p: Void?): TypeName {
        return visitDeclared(t, p)
    }

    /**
     * Visits a declared type, eg : a class, interface, enum defined in code.
     */
    override fun visitDeclared(t: DeclaredType, p: Void?): TypeName {
        // The recommended kotlinpoet-metadata API is
        // experimental there. For now we will just suppress the deprecation warning here.
        @Suppress("DEPRECATION")
        val rawType: ClassName = (t.asElement() as TypeElement).asClassName()
        val enclosingType = t.enclosingType
        val enclosing = if (enclosingType.kind != TypeKind.NONE &&
            Modifier.STATIC !in t.asElement().modifiers
        ) {
            enclosingType.accept(this, p)
        } else {
            null
        }
        if (t.typeArguments.isEmpty() && enclosing !is ParameterizedTypeName) {
            return rawType
        }

        val typeArgumentNames = mutableListOf<TypeName>()
        for (typeArgument in t.typeArguments) {
            typeArgumentNames += typeArgument.asKotlinTypeName()
        }
        return if (enclosing is ParameterizedTypeName)
            enclosing.nestedClass(rawType.simpleName, typeArgumentNames) else
            rawType.parameterizedBy(typeArgumentNames)
    }

    /**
     * Visits a Type Variable (eg: <T>).
     */
    override fun visitTypeVariable(
        t: TypeVariable,
        p: Void?
    ): TypeName {
        val element = t.asElement() as TypeParameterElement
        val alias = element.simpleName.toString()
        val bounds = element.bounds.map { it.asKotlinTypeName() }
        return TypeVariableName.invoke(
            alias,
            bounds
        )
    }

    /**
     * Visits a "wildcard" type (usually used within generics)
     */
    override fun visitWildcard(
        t: WildcardType,
        p: Void?
    ): TypeName {
        val outType = t.extendsBound

        return if (outType == null) {
            val inType = t.superBound
            if (inType == null) {
                STAR
            } else {
                WildcardTypeName.consumerOf(
                    inType.asKotlinTypeName()
                )
            }
        } else {
            val outTypeName = outType.asKotlinTypeName()
            if (outTypeName == ANY) {
                STAR
            } else {
                WildcardTypeName.producerOf(
                    outTypeName
                )
            }
        }
    }

    /**
     * Visits a Void/Unit type.
     */
    override fun visitNoType(t: NoType, p: Void?): TypeName {
        return if (t.kind == TypeKind.VOID) {
            UNIT
        } else {
            defaultAction(t, p)
        }
    }

    /**
     * Fallback for unsupported types
     */
    override fun defaultAction(e: TypeMirror?, p: Void?): TypeName {
        throw IllegalArgumentException("Unexpected type mirror: $e")
    }
}
