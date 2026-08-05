/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.ext

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType

/**
 * The placeholder used when a type can't be resolved.
 */
internal const val UNKNOWN_TYPE: String = "UNKNOWN"

/**
 * Renders the fully qualified name of the given [type].
 *
 * @param type the type to render
 * @param treatGenericAsSuper replaces a generic type (e.g. `<T : java.io.Closeable>`) by its super type
 * @param includeTypeArguments includes the type arguments in the rendered name
 * (e.g. if false, the type `List<String>` is rendered as `kotlin.collections.List`)
 */
internal fun KaSession.fqTypeName(
    type: KaType,
    treatGenericAsSuper: Boolean = true,
    includeTypeArguments: Boolean = true
): String {
    val bareType = when (type) {
        // Types coming from Java are flexible (e.g. `String!`), because the Kotlin compiler doesn't
        // know whether they're nullable or not. The class name comes from the lower bound, while
        // nullability is decided by the type as a whole further down, so platform types stay nullable.
        is KaFlexibleType -> type.lowerBound
        // A smart cast produces an intersection (e.g. `Runnable & Future<*>`). The type the value was
        // narrowed to is the one being called, and comes first in the conjuncts.
        is KaIntersectionType -> type.conjuncts.firstOrNull() ?: type

        else -> type
    }

    val resolved = if (bareType is KaTypeParameterType && treatGenericAsSuper) {
        // Treat generic types as their closest supertype; an unbounded type parameter is an `Any?`
        bareType.symbol.upperBounds.firstOrNull() ?: builtinTypes.nullableAny
    } else {
        bareType
    }

    val fqName = when (resolved) {
        is KaClassType -> resolved.classId.asFqNameString()
        is KaTypeParameterType -> resolved.name.asString()
        else -> null
    }

    if (fqName == null) {
        println("Unable to get fqName for ${type.javaClass} $type")
        return UNKNOWN_TYPE
    }

    val arguments = if (includeTypeArguments && resolved is KaClassType && resolved.typeArguments.isNotEmpty()) {
        resolved.typeArguments.joinToString(", ", prefix = "<", postfix = ">") { projection ->
            val argumentType = projection.type
            if (projection is KaStarTypeProjection || argumentType == null) {
                "*"
            } else {
                fqTypeName(argumentType, treatGenericAsSuper)
            }
        }
    } else {
        ""
    }

    return if (type.isNullable) {
        "$fqName$arguments?"
    } else {
        fqName + arguments
    }
}

/**
 * Renders the fully qualified name of a call receiver's type, always as a non nullable type.
 */
internal fun KaSession.fqReceiverTypeName(
    receiver: KaReceiverValue,
    treatGenericAsSuper: Boolean = true,
    includeTypeArguments: Boolean = true
): String = fqTypeName(receiver.type, treatGenericAsSuper, includeTypeArguments).removeSuffix("?")

/**
 * Unwraps a receiver which was smart cast, to get to the receiver as it was written in the source.
 */
internal fun KaReceiverValue.unwrapSmartCast(): KaReceiverValue =
    if (this is KaSmartCastedReceiverValue) original.unwrapSmartCast() else this

/**
 * Whether this receiver was explicitly written at the call site (as opposed to an implicit `this`).
 */
internal fun KaReceiverValue.isExplicit(): Boolean = unwrapSmartCast() is KaExplicitReceiverValue
