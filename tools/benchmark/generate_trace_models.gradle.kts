/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.gradle.tasks.SourceJarTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val generateTraceModelsTaskName = "generateTraceModelsFromJson"

tasks.register(
    generateTraceModelsTaskName,
    com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask::class.java
) {
    inputDirPath = "src/main/json"
    targetPackageName = "com.datadog.benchmark.internal.model"
}

afterEvaluate {
    tasks.withType(KotlinCompile::class.java).configureEach {
        dependsOn(generateTraceModelsTaskName)
    }

    // need to add an explicit dependency, otherwise there is an error during publishing
    // Task ':tools:benchmark:sourceReleaseJar' uses this output of task
    // ':tools:benchmark:generateTraceModelsFromJson' without
    // declaring an explicit or implicit dependency
    //
    // it is not needed for other modules with similar model generation, because they use KSP,
    // and KSP plugin see to establish link between sourcesJar and "generated" folder in general
    tasks.withType(SourceJarTask::class.java) {
        dependsOn(generateTraceModelsTaskName)
    }
}
