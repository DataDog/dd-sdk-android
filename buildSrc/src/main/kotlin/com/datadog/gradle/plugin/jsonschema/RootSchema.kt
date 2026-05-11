/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

/**
 * The root type read from a JSON schema, paired with the name to use when emitting the Kotlin file.
 *
 * The name is external metadata (derived from `inputNameMapping`, the schema's `title`, or the file
 * name) and is not part of the schema [TypeDefinition] itself. This allows top-level schemas whose
 * type doesn't carry a name (e.g. [TypeDefinition.Array]) to still be emitted with a chosen class
 * name.
 */
data class RootSchema(
    val name: String,
    val definition: TypeDefinition
)
