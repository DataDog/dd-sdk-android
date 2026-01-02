/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.gradle.tasks.SourceJarTask
import com.datadog.gradle.utils.createJsonModelsGenerationTask

val generateSessionReplayModelsTaskName = "generateSessionReplayModels"

createJsonModelsGenerationTask(generateSessionReplayModelsTaskName) {
    inputDirPath = "src/main/json/schemas"
    targetPackageName = "com.datadog.android.sessionreplay.model"
}

afterEvaluate {
    // need to add an explicit dependency, otherwise there is an error during publishing
    // Task ':features:dd-sdk-android-session-replay:sourceReleaseJar' uses this output of task
    // ':features:dd-sdk-android-session-replay:generateSessionReplayModelsFromJson' without
    // declaring an explicit or implicit dependency
    //
    // it is not needed for other modules with similar model generation, because they use KSP,
    // and KSP plugin see to establish link between sourcesJar and "generated" folder in general
    tasks.withType(SourceJarTask::class.java) {
        dependsOn(generateSessionReplayModelsTaskName)
    }
}
