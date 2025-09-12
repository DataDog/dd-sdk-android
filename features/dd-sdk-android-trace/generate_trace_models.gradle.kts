/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateTraceModelsTaskName = "generateTraceModelsFromJson"

tasks.register(
    generateTraceModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/trace"
    ignoredFiles = arrayOf(
        "_common-schema.json"
    )
    targetPackageName = "com.datadog.android.trace.model"
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_KOTLIN_API_SURFACE)
        ?.dependsOn(generateTraceModelsTaskName)
    tasks.withType(KotlinCompile::class.java).configureEach {
        dependsOn(generateTraceModelsTaskName)
    }
}
