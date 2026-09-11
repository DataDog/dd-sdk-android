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
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Usage
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the

// Applying `dev.detekt` would also register its own detekt tasks and apply detekt.yml
// - unwanted, since this plugin isn't linting, only using Detekt as an engine. All our own task actually needs
// from the plugin is the engine classpath, so provide that directly via detached configurations instead of applying it.
val detektVersion = the<VersionCatalogsExtension>().named("libs").findVersion("detekt").get().requiredVersion
val detektEngineClasspath = configurations.detachedConfiguration(
    dependencies.create("dev.detekt:detekt-cli:$detektVersion")
)
val detektPluginClasspath = configurations.detachedConfiguration(
    dependencies.create(project(":tools:detekt"))
)

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
        detektClasspath.setFrom(detektEngineClasspath)
        pluginClasspath.setFrom(detektPluginClasspath)
        classpath.setFrom(unitTest.compileClasspath, bootClasspath)
        jvmTarget = "17"
        buildUponDefaultConfig = false
        // Only `datadog-test-pyramid` matters here - skip Detekt's bundled default rule sets.
        disableDefaultRuleSets = true

        // This plugin deliberately does not apply `dev.detekt`, so the conventions the plugin would put
        // on every Detekt task are not applied either. These mirror `setDetektTaskDefaults` in the Detekt
        // plugin and must be kept in sync with it: without them Gradle fails task validation because the
        // properties have no value. Only the ones this task does not set explicitly are listed.
        debug = false
        parallel = false
        autoCorrect = false
        ignoreFailures = false
        failOnSeverity = FailOnSeverity.Error
        allRules = false
        noJdk = false
        multiPlatformEnabled = false
        basePath = rootProject.projectDir.absolutePath

        reports {
            // outputLocation has to be set even for a disabled report, otherwise Gradle task validation
            // fails on it; the Detekt plugin normally supplies these conventions. Nothing is written.
            val unusedReports = layout.buildDirectory.dir("reports/detekt-test-pyramid/unused")
            sarif.required = false
            sarif.outputLocation = unusedReports.map { it.file("report.sarif") }
            html.required = false
            html.outputLocation = unusedReports.map { it.file("report.html") }
            checkstyle.required = false
            checkstyle.outputLocation = unusedReports.map { it.file("report.xml") }
            markdown.required = false
            markdown.outputLocation = unusedReports.map { it.file("report.md") }
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
