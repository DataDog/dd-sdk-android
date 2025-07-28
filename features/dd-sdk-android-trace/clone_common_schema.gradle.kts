/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask

tasks.register<GitCloneDependenciesTask>("cloneCommonSchema") {
    extension.apply {
        clone(
            "https://github.com/DataDog/rum-events-format.git",
            "schemas/rum",
            destinationFolder = "src/main/json/trace",
            excludedPrefixes = listOf(
                "_action-child-schema.json",
                "_perf-metric-schema.json",
                "_profiling-internal-context-schema.json",
                "_rect-schema.json",
                "_view-accessibility-schema.json",
                "_view-container-schema.json",
                "_view-performance-schema.json",
                "action-schema.json",
                "error-schema.json",
                "long_task-schema.json",
                "resource-schema.json",
                "view-schema.json",
                "vital-schema.json"
            ),
            ref = "master"
        )
    }
}
