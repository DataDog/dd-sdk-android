/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateRumModelsTaskName = "generateRumModelsFromJson"

tasks.register(
    generateRumModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/rum"
    targetPackageName = "com.datadog.android.rum.model"
    ignoredFiles = arrayOf(
        "_common-schema.json",
        "_action-child-schema.json",
        "_perf-metric-schema.json",
        "_view-container-schema.json"
    )
    inputNameMapping = mapOf(
        "action-schema.json" to "ActionEvent",
        "error-schema.json" to "ErrorEvent",
        "resource-schema.json" to "ResourceEvent",
        "view-schema.json" to "ViewEvent",
        "long_task-schema.json" to "LongTaskEvent"
    )
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_KOTLIN_API_SURFACE)
        ?.dependsOn(generateRumModelsTaskName)
    tasks.withType(KotlinCompile::class.java).configureEach {
        dependsOn(generateRumModelsTaskName)
    }
}
