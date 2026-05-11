/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec

internal const val PARSE_ERROR_MSG = "Unable to parse json into type"

internal val STRING_DESERIALIZER_EXCEPTIONS = arrayOf(
    ClassNameRef.IllegalStateException
)

internal val ELEMENT_DESERIALIZER_EXCEPTIONS = arrayOf(
    ClassNameRef.IllegalStateException,
    ClassNameRef.NumberFormatException,
    ClassNameRef.NullPointerException
)

/**
 * Wraps [body] in a `try` block followed by one `catch` block per entry in [caughtExceptions].
 * Each catch rethrows as a `JsonParseException` referencing [returnType] and chaining the cause.
 */
internal fun FunSpec.Builder.wrapInDeserializerTryCatch(
    returnType: ClassName,
    caughtExceptions: Array<ClassName>,
    body: FunSpec.Builder.() -> Unit
) {
    beginControlFlow("try")
    body()
    caughtExceptions.forEach { exception ->
        nextControlFlow(
            "catch (%L: %T)",
            Identifier.CAUGHT_EXCEPTION,
            exception
        )
        addStatement("throw %T(", ClassNameRef.JsonParseException)
        addStatement("    \"$PARSE_ERROR_MSG %T\",", returnType)
        addStatement("    %L", Identifier.CAUGHT_EXCEPTION)
        addStatement(")")
    }
    endControlFlow()
}
