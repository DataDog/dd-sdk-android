/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.utils.createJsonModelsGenerationTask

createJsonModelsGenerationTask("generateRumModelsFromJson") {
    inputDirPath = "src/main/json/rum"
    targetPackageName = "com.datadog.android.rum.model"
    ignoredFiles = listOf(
        "_common-schema.json",
        "_action-child-schema.json",
        "_perf-metric-schema.json",
        "_profiling-internal-context-schema.json",
        "_rect-schema.json",
        "_view-container-schema.json",
        "_view-accessibility-schema.json",
        "_view-performance-schema.json",
        "_vital-common-schema.json"
    )
    inputNameMapping = mapOf(
        "action-schema.json" to "ActionEvent",
        "error-schema.json" to "ErrorEvent",
        "resource-schema.json" to "ResourceEvent",
        "view-schema.json" to "ViewEvent",
        "long_task-schema.json" to "LongTaskEvent",
        "vital-app-launch-schema.json" to "VitalAppLaunchEvent",
        "vital-operation-step-schema.json" to "VitalOperationStepEvent"
    )
}
