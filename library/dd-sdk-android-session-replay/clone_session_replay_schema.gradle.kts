/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask

tasks.register<GitCloneDependenciesTask>("cloneSessionReplaySchema") {
    extension.apply {
        clone(
            "https://github.com/DataDog/rum-events-format.git",
            "schemas/session-replay/",
            destinationFolder = "src/main/json/session-replay",
            ref = "master"
        )
    }
}
