/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
import com.datadog.gradle.utils.cloneRumEventsFormat
import com.datadog.gradle.utils.createRumSchemaCloningTask

createRumSchemaCloningTask("cloneCommonSchema") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/rum",
        destinationFolder = "src/main/json/log",
        excludedPrefixes = listOf(
            "_action-child-schema.json",
            "_perf-metric-schema.json",
            "_profiling-internal-context-schema.json",
            "_rect-schema.json",
            "_stream-schema.json",
            "_view-accessibility-schema.json",
            "_view-container-schema.json",
            "_view-performance-schema.json",
            "_vital-common-schema.json",
            "action-schema.json",
            "error-schema.json",
            "long_task-schema.json",
            "resource-schema.json",
            "transition-schema.json",
            "view-schema.json",
            "vital-schema.json",
            "vital-app-launch-schema.json",
            "vital-duration-schema.json",
            "vital-operation-step-schema.json"
        )
    )
}
