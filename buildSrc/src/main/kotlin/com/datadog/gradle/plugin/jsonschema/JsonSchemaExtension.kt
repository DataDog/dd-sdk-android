/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

/**
 * The main Gradle extension.
 * This allows you to define rules for verifying Jetpack Benchmark results.
 */
open class JsonSchemaExtension {
    var targetPackageName: String = ""
    var inputDirPath: String = "resources"
    var ignoredFiles: Array<String> = emptyArray()
    var nameMapping: Map<String, String> = emptyMap()
}
