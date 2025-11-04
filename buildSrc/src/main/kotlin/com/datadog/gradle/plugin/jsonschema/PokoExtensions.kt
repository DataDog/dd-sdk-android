/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import com.datadog.gradle.utils.joinToCamelCaseAsVar
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.NOTHING
import com.squareup.kotlinpoet.NUMBER
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName

internal val NOTHING_NULLABLE = NOTHING.copy(nullable = true)

@Suppress("ReturnCount")
internal fun String.variableName(): String {
    val split = this.split("_").filter { it.isNotBlank() }
    if (split.isEmpty()) return ""
    if (split.size == 1) return split[0]
    return split.joinToCamelCaseAsVar()
}

internal fun JsonType?.asKotlinTypeName(): TypeName {
    return when (this) {
        null,
        JsonType.NULL -> NOTHING_NULLABLE
        JsonType.BOOLEAN -> BOOLEAN
        JsonType.NUMBER -> NUMBER
        JsonType.STRING -> STRING
        JsonType.INTEGER -> LONG
        JsonType.OBJECT,
        JsonType.ARRAY -> throw IllegalArgumentException(
            "Cannot convert $this to a KotlinTypeName"
        )
    }
}

internal fun JsonPrimitiveType?.asKotlinTypeName(): TypeName {
    return when (this) {
        JsonPrimitiveType.BOOLEAN -> BOOLEAN
        JsonPrimitiveType.STRING -> STRING
        JsonPrimitiveType.INTEGER -> LONG
        JsonPrimitiveType.NUMBER -> NUMBER
        null -> NOTHING_NULLABLE
    }
}

internal fun TypeName.asNullable() = copy(nullable = true)
