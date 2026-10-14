/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.ext

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType

internal fun KaSession.fqTypeName(
    type: KaType,
    treatGenericAsSuper: Boolean = true,
    includeTypeArguments: Boolean = true
): String {
    val baseName = when (type) {
        // types from java are flexible because the Kotlin Compiler doesn't know whether
        // they're nullable or not. Use the lower bound name and report the nullability below
        is KaFlexibleType -> fqTypeName(type.lowerBound, treatGenericAsSuper, includeTypeArguments)
            .removeSuffix("?")

        is KaDefinitelyNotNullType -> fqTypeName(type.original, treatGenericAsSuper, includeTypeArguments)
            .removeSuffix("?")

        is KaTypeParameterType -> if (treatGenericAsSuper) {
            // Treat generic types as their closest supertype (usually Any)
            type.symbol.upperBounds.firstOrNull()?.fqClassName() ?: "kotlin.Any"
        } else {
            type.name.asString()
        }

        is KaClassType -> {
            val fqName = type.classId.asSingleFqName().asString()
            if (includeTypeArguments && type.typeArguments.isNotEmpty()) {
                type.typeArguments.joinToString(", ", prefix = "$fqName<", postfix = ">") {
                    when (it) {
                        is KaStarTypeProjection -> "*"
                        is KaTypeArgumentWithVariance -> fqTypeName(it.type, treatGenericAsSuper)
                    }
                }
            } else {
                fqName
            }
        }

        else -> {
            println("Unable to get fqName for ${type.javaClass} $type")
            return UNKNOWN_TYPE
        }
    }
    return if (type.isNullable) "$baseName?" else baseName
}

/**
 * @return the fully qualified name of the class (without type arguments nor nullability)
 */
internal fun KaType.fqClassName(): String? {
    return when (this) {
        is KaFlexibleType -> lowerBound.fqClassName()
        is KaDefinitelyNotNullType -> original.fqClassName()
        is KaClassType -> classId.asSingleFqName().asString()
        else -> null
    }
}

private const val UNKNOWN_TYPE = "UNKNOWN"
