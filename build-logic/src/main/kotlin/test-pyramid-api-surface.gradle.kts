/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Usage
import org.gradle.kotlin.dsl.getByType
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

        detektClasspath.setFrom(detektEngineClasspath)
        pluginClasspath.setFrom(detektPluginClasspath)
        classpath.setFrom(variant.compileClasspath, androidComponents.sdkComponents.bootClasspath)
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
