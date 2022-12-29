/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.detekt.ext

import io.gitlab.arturbosch.detekt.rules.fqNameOrNull
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.scopes.receivers.ClassQualifier
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.types.FlexibleType
import org.jetbrains.kotlin.types.lowerIfFlexible
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

@Suppress("ReturnCount")
internal fun Receiver?.type(bindingContext: BindingContext): String? {
    if (this == null) return null
    if (this is ExpressionReceiver) {
        val resolvedType = expression.getType(bindingContext)
        return if (resolvedType is FlexibleType) {
            // types from java are flexible because the Kotlin Compiler doesn't know whether
            // they're nullable or not.Using the lowerBound makes it nonNullable
            resolvedType.lowerIfFlexible().fullType()
        } else {
            // Convert all types to nonNullable
            resolvedType?.makeNotNullable()?.fullType()
        }
    } else if (this is ClassQualifier) {
        return descriptor.fqNameOrNull()?.toString()
    } else if (this is ImplicitReceiver) {
        return type.fqNameOrNull()?.toString()
    } else {
        println("DD: Unknown receiver type ${this.javaClass}")
        return null
    }
}
