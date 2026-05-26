/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.datadog.tools.annotation.NoOpImplementation
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration

// KSP2 omits annotation arguments that rely on their default value from KSAnnotation.arguments,
// so the typed proxy built by getAnnotationsByType throws NoSuchElementException for those args.
// See https://github.com/google/ksp/issues/2356
internal inline fun <T> safeAnnotationArg(default: T, block: () -> T): T =
    @Suppress("SwallowedException")
    try { block() } catch (_: NoSuchElementException) { default }

@OptIn(KspExperimental::class)
internal fun KSClassDeclaration.noOpName(): String =
    getAnnotationsByType(NoOpImplementation::class).firstOrNull()
        ?.let { annotation -> safeAnnotationArg("") { annotation.customName } }
        ?.takeIf { it.isNotEmpty() }
        ?: defaultNoOpName()

internal fun KSClassDeclaration.defaultNoOpName(): String {
    val names = mutableListOf(simpleName.asString())
    var enclosing = parentDeclaration as? KSClassDeclaration
    while (enclosing != null) {
        names.add(0, enclosing.simpleName.asString())
        enclosing = enclosing.parentDeclaration as? KSClassDeclaration
    }
    return "NoOp${names.joinToString("")}"
}
