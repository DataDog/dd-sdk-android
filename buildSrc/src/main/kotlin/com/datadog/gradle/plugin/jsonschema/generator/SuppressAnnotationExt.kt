/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec

fun TypeSpec.Builder.addSuppressAnnotation(rules: Array<String>): TypeSpec.Builder {
    return addAnnotation(
        getSuppressAnnotationSpec(rules)
    )
}

fun FunSpec.Builder.addSuppressAnnotation(rules: Array<String>): FunSpec.Builder {
    return addAnnotation(
        getSuppressAnnotationSpec(rules)
    )
}

@Suppress("SpreadOperator")
fun getSuppressAnnotationSpec(rules: Array<String>): AnnotationSpec {
    return AnnotationSpec.builder(Suppress::class)
        .addMember(rules.joinToString(", ") { "%S" }, *rules)
        .build()
}
