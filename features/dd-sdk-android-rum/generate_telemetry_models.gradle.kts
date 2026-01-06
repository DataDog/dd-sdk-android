/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.utils.createJsonModelsGenerationTask

createJsonModelsGenerationTask("generateTelemetryModelsFromJson") {
    inputDirPath = "src/main/json/telemetry"
    targetPackageName = "com.datadog.android.telemetry.model"
    ignoredFiles = arrayOf(
        "_common-schema.json"
    )
    inputNameMapping = mapOf(
        "debug-schema.json" to "TelemetryDebugEvent",
        "error-schema.json" to "TelemetryErrorEvent"
    )
}
