/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask

tasks.register<GitCloneDependenciesTask>("cloneDdTrace") {
    extension.apply {
        clone(
            "https://github.com/DataDog/dd-trace-java.git",
            "dd-trace-ot",
            "v0.50.0",
            listOf(
                "dd-trace-ot.gradle",
                "README.md",
                "jfr-openjdk/",
                "src/jmh/", // JVM based benchmark, not relevant for ART/Dalvik
                "src/traceAgentTest/",
                "src/ot33CompatabilityTest/",
                "src/ot31CompatabilityTest/",
                "src/test/resources/",
                "src/main/java/datadog/trace/common/processor/",
                "src/main/java/datadog/trace/common/sampling/RuleBasedSampler.java",
                "src/main/java/datadog/trace/common/serialization",
                "src/main/java/datadog/trace/common/writer/unixdomainsockets",
                "src/main/java/datadog/trace/common/writer/ddagent",
                "src/main/java/datadog/trace/common/writer/DDAgentWriter.java",
                "src/main/java/datadog/opentracing/resolver",
                "src/main/java/datadog/opentracing/ContainerInfo.java",
                "src/test"
            )
        )
        clone(
            "https://github.com/DataDog/dd-trace-java.git",
            "dd-trace-api",
            "v0.50.0",
            listOf(
                "dd-trace-api.gradle",
                "src/main/java/datadog/trace/api/GlobalTracer.java",
                "src/main/java/datadog/trace/api/CorrelationIdentifier.java",
                "src/test"
            )
        )
        clone(
            "https://github.com/DataDog/dd-trace-java.git",
            "utils/thread-utils",
            "v0.50.0",
            listOf(
                "thread-utils.gradle",
                "src/test/"
            )
        )
    }
}
