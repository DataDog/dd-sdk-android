/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateLogModelsTaskName = "generateLogModelsFromJson"

tasks.register(
    generateLogModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/log"
    targetPackageName = "com.datadog.android.log.model"
    ignoredFiles = arrayOf("logs-collection-schema.json")

}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_KOTLIN_API_SURFACE)
        ?.dependsOn(generateLogModelsTaskName)
    tasks.withType(KotlinCompile::class.java).configureEach {
        dependsOn(generateLogModelsTaskName)
    }
}
