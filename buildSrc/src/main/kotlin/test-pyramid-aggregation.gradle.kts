/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.gradle.api.attributes.Usage
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.named

plugins {
    id("org.gradle.reporting-base")
}

// Separate scopes per role - a single shared scope would make both resolvable configs below try
// to resolve every producer project (surface AND usage), and fail hard on the ones that don't
// expose the other role's variant at all.
val testPyramidApiSurfaceAggregation = configurations.dependencyScope("testPyramidApiSurfaceAggregation")
val testPyramidApiUsageAggregation = configurations.dependencyScope("testPyramidApiUsageAggregation")

val apiSurfaceReportsConfiguration = configurations.resolvable("apiSurfaceReports") {
    extendsFrom(testPyramidApiSurfaceAggregation)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, named("test-pyramid-api-surface"))
    }
}

val apiUsageReportsConfiguration = configurations.resolvable("apiUsageReports") {
    extendsFrom(testPyramidApiUsageAggregation)
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, named("test-pyramid-api-usage"))
    }
}

// Aggregates the `apiSurface.log`/`apiUsage.log` files produced by every opted-in producer module
// into a single coverage report: which public APIs (surface) are never exercised by a test (usage).
tasks.register("checkTestPyramidCoverage") {
    group = "verification"
    description = "Aggregates test-pyramid API surface/usage reports into a coverage summary."

    inputs.files(apiSurfaceReportsConfiguration).withPathSensitivity(PathSensitivity.NONE)
    inputs.files(apiUsageReportsConfiguration).withPathSensitivity(PathSensitivity.NONE)
    val coverageReportFile = reporting.baseDirectory.file("test-pyramid/coverage.txt")
    outputs.file(coverageReportFile)
    outputs.upToDateWhen { false }

    // captured to be configuration cache compatible
    val surfaceFilesProvider = apiSurfaceReportsConfiguration.map { it.files }
    val usageFilesProvider = apiUsageReportsConfiguration.map { it.files }
    doLast {
        // A producer's task can be skipped as NO-SOURCE (e.g. a module with no unit tests at
        // all), in which case its declared output file is never actually written.
        val surfaceApis = surfaceFilesProvider.get().filter { it.exists() }
            .flatMap { it.readLines() }.filterTo(sortedSetOf()) { it.isNotBlank() }
        val usedApis = usageFilesProvider.get().filter { it.exists() }
            .flatMap { it.readLines() }.filterTo(sortedSetOf()) { it.isNotBlank() }

        val missedApis = surfaceApis - usedApis
        val hitApis = surfaceApis intersect usedApis

        val summary = when {
            surfaceApis.isEmpty() -> "⚠ Test Integration coverage: no API surface data found"
            missedApis.isEmpty() -> "✔ Test Integration coverage 100%"
            else ->
                "⚠ Test Integration coverage missed ${missedApis.size} apis " +
                    "(${"%.1f".format(hitApis.size * 100f / surfaceApis.size)} % coverage; " +
                    "${"%.1f".format(missedApis.size * 100f / surfaceApis.size)} % miss)"
        }

        val report = buildString {
            appendLine(summary)
            if (missedApis.isNotEmpty()) {
                appendLine()
                appendLine("Missed APIs:")
                missedApis.forEach { appendLine(it) }
            }
        }

        val outputFile = coverageReportFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(report)

        logger.lifecycle(summary)
        logger.lifecycle("Full report: ${outputFile.absolutePath}")
    }
}
