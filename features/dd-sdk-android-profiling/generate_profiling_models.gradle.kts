/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.utils.createJsonModelsGenerationTask

createJsonModelsGenerationTask("generateProfilingModelsFromJson") {
    inputDirPath = "src/main/json/profiling/mobile"
    // watch for changes in the referenced schema
    extraInputWatchDir = project.layout.projectDirectory.dir("src/main/json/profiling")
    inputNameMapping = mapOf(
        "profile-event-schema.json" to "ProfileEvent",
        "rum-mobile-events-schema.json" to "RumMobileEvents"
    )
    targetPackageName = "com.datadog.android.profiling.model"
}
