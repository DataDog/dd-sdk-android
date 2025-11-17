/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.gradle.tasks.SourceJarTask
import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Paths

val generateSessionReplayModelsTaskName = "generateSessionReplayModels"
val generateMobileSegmentConstantsTaskName = "generateMobileSegmentConstants"

tasks.register(
    generateSessionReplayModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json/schemas"
    targetPackageName = "com.datadog.android.sessionreplay.model"
}

tasks.register(
    generateMobileSegmentConstantsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateMobileSegmentConstantsTask::class.java
) {
    val generatedModelsDir = project.layout.buildDirectory
        .dir(Paths.get("generated", "json2kotlin", "main", "kotlin").toString())

    generatedMobileSegmentFile.set(
        generatedModelsDir.map { it.file("com/datadog/android/sessionreplay/model/MobileSegment.kt") }
    )

    outputConstantsFile.set(
        generatedModelsDir.map { it.file("com/datadog/android/sessionreplay/model/MobileSegmentConstants.kt") }
    )

    dependsOn(generateSessionReplayModelsTaskName)
}

afterEvaluate {
    tasks.findByName(ApiSurfacePlugin.TASK_GEN_KOTLIN_API_SURFACE)
        ?.dependsOn(
            generateSessionReplayModelsTaskName,
            generateMobileSegmentConstantsTaskName
        )
    tasks.withType(KotlinCompile::class.java) {
        dependsOn(
            generateSessionReplayModelsTaskName,
            generateMobileSegmentConstantsTaskName
        )
    }

    // need to add an explicit dependency, otherwise there is an error during publishing
    // Task ':features:dd-sdk-android-session-replay:sourceReleaseJar' uses this output of task
    // ':features:dd-sdk-android-session-replay:generateSessionReplayModelsFromJson' without
    // declaring an explicit or implicit dependency
    //
    // it is not needed for other modules with similar model generation, because they use KSP,
    // and KSP plugin see to establish link between sourcesJar and "generated" folder in general
    tasks.withType(SourceJarTask::class.java) {
        dependsOn(generateSessionReplayModelsTaskName, generateMobileSegmentConstantsTaskName)
    }
}
