/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import dev.detekt.gradle.Detekt
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named

plugins {
    // Provides the Detekt engine classpath and the defaults of the `Detekt` tasks
    id("dev.detekt")
}

dependencies {
    detektPlugins(project(":tools:detekt"))
}

// This plugin only uses Detekt as an engine: these modules aren't linted, so keep the root `detekt` task away from them
tasks.detekt {
    isEnabled = false
}

val apiUsageLogFile = layout.buildDirectory.file("reports/detekt-test-pyramid/apiUsage.log")
val generatedConfigFile = layout.buildDirectory.file("tmp/detekt-test-pyramid/apiUsageTestPyramidConfig.yml")

fun configureApiUsageVariant(variant: Variant, bootClasspath: Provider<List<RegularFile>>) {
    // ApiUsage only recognizes JUnit5 Jupiter annotations, which `androidTest` doesn't use.
    val unitTest = (variant as? HasUnitTest)?.unitTest ?: return

    val checkApiUsageTask = tasks.register<Detekt>("checkTestPyramidApiUsage") {
        group = "verification"
        description = "Datadog test pyramid: collects internal API usage from unit tests for coverage reporting."

        unitTest.sources.java?.let { source(it.all) }
        unitTest.sources.kotlin?.let { source(it.all) }
        classpath.setFrom(unitTest.compileClasspath, bootClasspath)
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

        localState.register(generatedConfigFile)
        config.setFrom(generatedConfigFile)
        outputs.file(apiUsageLogFile)

        // `doFirst` is serialized into the configuration cache, so it can't reach through the
        // enclosing script instance to read a top-level `val` - capture local copies instead.
        val configFile = generatedConfigFile
        val logFileProvider = apiUsageLogFile
        doFirst {
            val generatedConfig = configFile.get().asFile
            generatedConfig.parentFile.mkdirs()
            generatedConfig.writeText(
                """
                datadog:
                  active: false
                datadog-test-pyramid:
                  active: true
                  ApiUsage:
                    active: true
                    outputFileName: '${logFileProvider.get().asFile.absolutePath}'
                    internalPackagePrefix: 'com.datadog'
                """.trimIndent()
            )

            // ApiUsage appends per-file - a fresh rule instance is created per analyzed file,
            // so truncate the shared output file once here, before analysis starts.
            val logFile = logFileProvider.get().asFile
            logFile.parentFile.mkdirs()
            logFile.writeText("")
        }
    }

    configurations.consumable("apiUsageReportElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, named("test-pyramid-api-usage"))
        }
        outgoing.artifact(apiUsageLogFile) {
            builtBy(checkApiUsageTask)
        }
    }

    rootProject.dependencies.add("testPyramidApiUsageAggregation", project(project.path))
}

extensions.findByType<LibraryAndroidComponentsExtension>()?.let { components ->
    components.onVariants(components.selector().withBuildType("debug")) {
        configureApiUsageVariant(it, components.sdkComponents.bootClasspath)
    }
}
extensions.findByType<ApplicationAndroidComponentsExtension>()?.let { components ->
    components.onVariants(components.selector().withBuildType("debug")) {
        configureApiUsageVariant(it, components.sdkComponents.bootClasspath)
    }
}
