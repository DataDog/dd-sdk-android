/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import java.util.Locale

fun List<String>.joinToCamelCase(): String = when (size) {
    0 -> throw IllegalArgumentException("invalid section size, cannot be zero")
    1 -> this[0].toCamelCase()
    else -> this.joinToString("", transform = String::toCamelCase)
}

fun List<String>.joinToCamelCaseAsVar(): String = when (size) {
    0 -> throw IllegalArgumentException("invalid section size, cannot be zero")
    1 -> this[0].toCamelCaseAsVar()
    else -> get(0).toCamelCaseAsVar() + drop(1).joinToCamelCase()
}

@Suppress("ReturnCount")
fun String.toCamelCase(): String {
    val split = this.split("_")
    if (split.size == 0) return ""
    if (split.size == 1) {
        return split[0].replaceFirstChar {
            if (it.isLowerCase()) {
                it.titlecase(
                    Locale.US
                )
            } else {
                it.toString()
            }
        }
    }
    return split.joinToCamelCase()
}

@Suppress("ReturnCount")
fun String.toCamelCaseAsVar(): String {
    val split = this.split("_")
    if (split.isEmpty()) return ""
    if (split.size == 1) return split[0]
    return split.joinToCamelCaseAsVar()
}
