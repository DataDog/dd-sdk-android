/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import dev.detekt.gradle.Detekt
import org.gradle.api.attributes.Usage
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

plugins {
    // Provides the Detekt engine classpath and the defaults of the `Detekt` tasks
    id("dev.detekt")
}

dependencies {
    detektPlugins(project(":tools:detekt"))
}

val apiSurfaceLogFile = layout.buildDirectory.file("reports/detekt-test-pyramid/apiSurface.log")
val generatedConfigFile = layout.buildDirectory.file("tmp/detekt-test-pyramid/apiSurfaceTestPyramidConfig.yml")

val androidComponents = extensions.getByType<LibraryAndroidComponentsExtension>()
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    val checkApiSurfaceTask = tasks.register<Detekt>("checkTestPyramidApiSurface") {
        group = "verification"
        description = "Datadog test pyramid: collects the public API surface for coverage reporting."

        variant.sources.java?.let {
            source(
                it.all.map { dirs ->
                    dirs.filterNot { dir -> dir.asFile.path.contains("/build/") }
                }
            )
        }
        variant.sources.kotlin?.let {
            source(
                it.all.map { dirs ->
                    dirs.filterNot { dir -> dir.asFile.path.contains("/build/") }
                }
            )
        }

        classpath.setFrom(variant.compileClasspath, androidComponents.sdkComponents.bootClasspath)
        jvmTarget = "17"
        buildUponDefaultConfig = false
        // Only `datadog-test-pyramid` matters here - skip Detekt's bundled default rule sets.
        disableDefaultRuleSets = true

        reports {
            sarif.required = false
            html.required = false
            markdown.required = false
            checkstyle.required = false
        }

        // Internal working state regenerated every run, not a published output - LocalState, not @OutputFile.
        localState.register(generatedConfigFile)
        config.setFrom(generatedConfigFile)
        outputs.file(apiSurfaceLogFile)

        // captured to be configuration cache compatible
        val configFile = generatedConfigFile
        val logFileProvider = apiSurfaceLogFile
        doFirst {
            val generatedConfig = configFile.get().asFile
            generatedConfig.parentFile.mkdirs()
            generatedConfig.writeText(
                """
                datadog:
                  active: false
                datadog-test-pyramid:
                  active: true
                  ApiSurface:
                    active: true
                    outputFileName: '${logFileProvider.get().asFile.absolutePath}'
                    internalPackagePrefix: 'com.datadog'
                    ignoredAnnotations:
                      - 'com.datadog.android.lint.InternalApi'
                    ignoredClasses:
                      - 'com.datadog.android._InternalProxy'
                      - 'com.datadog.android.rum._RumInternalProxy'
                """.trimIndent()
            )

            // ApiSurface appends per-file - a fresh rule instance is created per analyzed file,
            // so truncate the shared output file once here, before analysis starts.
            val logFile = logFileProvider.get().asFile
            logFile.parentFile.mkdirs()
            logFile.writeText("")
        }
    }

    configurations.consumable("apiSurfaceReportElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, named("test-pyramid-api-surface"))
        }
        outgoing.artifact(apiSurfaceLogFile) {
            builtBy(checkApiSurfaceTask)
        }
    }

    rootProject.dependencies.add("testPyramidApiSurfaceAggregation", project(project.path))
}
