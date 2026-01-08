/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
import com.datadog.gradle.utils.cloneRumEventsFormat
import com.datadog.gradle.utils.createRumSchemaCloneTask

createRumSchemaCloneTask("cloneSessionReplayRootSchemas") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/",
        destinationFolder = "src/main/json/schemas",
        excludedPrefixes = listOf(
            "profiling",
            "session-replay/",
            "rum",
            "mobile",
            "telemetry",
            "session-replay-schema",
            "session-replay-browser-schema"
        )
    )
}

createRumSchemaCloneTask("cloneSessionReplayMobileSchemas") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/session-replay/mobile",
        destinationFolder = "src/main/json/schemas/session-replay/mobile"
    )
}

createRumSchemaCloneTask("cloneSessionReplayCommonSchemas") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/session-replay/common",
        destinationFolder = "src/main/json/schemas/session-replay/common"
    )
}

tasks.register("cloneSessionReplaySchemas") {
    dependsOn("cloneSessionReplayRootSchemas")
    dependsOn("cloneSessionReplayMobileSchemas")
    dependsOn("cloneSessionReplayCommonSchemas")
}
