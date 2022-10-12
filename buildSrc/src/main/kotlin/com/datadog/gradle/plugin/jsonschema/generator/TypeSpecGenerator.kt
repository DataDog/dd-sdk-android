/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema.generator

import com.datadog.gradle.plugin.jsonschema.TypeDefinition
import com.squareup.kotlinpoet.TypeSpec

abstract class TypeSpecGenerator<T : TypeDefinition>(
    packageName: String,
    knownTypes: MutableSet<KotlinTypeWrapper>
) : KotlinSpecGenerator<T, TypeSpec.Builder>(
    packageName,
    knownTypes
)
