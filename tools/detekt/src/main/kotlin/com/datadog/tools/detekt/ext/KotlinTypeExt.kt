/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.ext

import org.jetbrains.kotlin.descriptors.buildPossiblyInnerType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.getArguments
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.supertypes

internal fun KotlinType.fqTypeName(
    treatGenericAsSuper: Boolean = true,
    includeTypeArguments: Boolean = true
): String {
    val descriptor = if ((constructor.toString().length == 1) && treatGenericAsSuper) {
        // Treat generic types as their closest supertype (usually Any)
        supertypes().firstOrNull()?.buildPossiblyInnerType()?.classifierDescriptor
    } else {
        buildPossiblyInnerType()?.classifierDescriptor
    }
    val fqName = descriptor?.fqNameOrNull()?.asString()
    if (fqName == null) {
        println("Unable to get fqName for ${this.javaClass} $this")
        return "UNKNOWN"
    }
    val arguments = if (getArguments().isNotEmpty()) {
        if (includeTypeArguments) {
            arguments.joinToString(", ", prefix = "<", postfix = ">") {
                it.type.fqTypeName(treatGenericAsSuper)
            }
        } else {
            ""
        }
    } else {
        ""
    }
    return if (isNullable()) {
        "$fqName$arguments?"
    } else {
        fqName + arguments
    }
}
