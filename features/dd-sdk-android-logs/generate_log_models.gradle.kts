/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.utils.createJsonModelsGenerationTask
import java.nio.file.Paths

createJsonModelsGenerationTask("generateLogModelsFromJson") {
    inputDirPath = "src/main/json/log"
    ignoredFiles = listOf(
        "_common-schema.json"
    )
    targetPackageName = "com.datadog.android.log.model"
    extraInputWatchDir = project.layout.projectDirectory.dir(
        Paths.get("../dd-sdk-android-rum/src/main/json/rum").toString()
    )
}
