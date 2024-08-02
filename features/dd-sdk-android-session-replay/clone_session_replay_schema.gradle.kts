/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask

val eventFormatRepo = "https://github.com/DataDog/rum-events-format.git"
val eventFormatVersion = "master"

tasks.register<GitCloneDependenciesTask>("cloneSessionReplayRootSchemas") {
    extension.apply {
        clone(
            eventFormatRepo,
            "schemas/",
            destinationFolder = "src/main/json/schemas",
            excludedPrefixes = listOf(
                "session-replay/",
                "rum",
                "mobile",
                "telemetry",
                "session-replay-schema",
                "session-replay-browser-schema"
            ),
            ref = eventFormatVersion
        )
    }
}

tasks.register<GitCloneDependenciesTask>("cloneSessionReplayMobileSchemas") {
    extension.apply {
        clone(
            eventFormatRepo,
            "schemas/session-replay/mobile",
            destinationFolder = "src/main/json/schemas/session-replay/mobile",
            ref = eventFormatVersion
        )
    }
}

tasks.register<GitCloneDependenciesTask>("cloneSessionReplayCommonSchemas") {
    extension.apply {
        clone(
            eventFormatRepo,
            "schemas/session-replay/common",
            destinationFolder = "src/main/json/schemas/session-replay/common",
            ref = eventFormatVersion
        )
    }
}

tasks.register("cloneSessionReplaySchemas") {
    dependsOn("cloneSessionReplayRootSchemas")
    dependsOn("cloneSessionReplayMobileSchemas")
    dependsOn("cloneSessionReplayCommonSchemas")
}
