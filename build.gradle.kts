/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.depotProxied
import com.datadog.gradle.config.registerSubModuleAggregationTask

plugins {
    `maven-publish`
    alias(libs.plugins.ktlintGradlePlugin) apply false
    id("ktlint")
    id("test-pyramid-aggregation")
    alias(libs.plugins.nexusPublishGradlePlugin)
    alias(libs.plugins.dependencyLicenseGradlePlugin)

    // just load into the classpath, so that we can use version-less id(string) in submodules
    // ideally we need to use aliases in submodules
    alias(libs.plugins.kotlinSPGradlePlugin) apply false
    alias(libs.plugins.binaryCompatibilityGradlePlugin) apply false
    alias(libs.plugins.kotlinxSerializationPlugin) apply false
    alias(libs.plugins.koverPlugin) apply false
    alias(libs.plugins.androidLibraryPlugin) apply false
    alias(libs.plugins.kotlinAndroidPlugin) apply false
    alias(libs.plugins.dokkaGradlePlugin) apply false
    alias(libs.plugins.detektGradlePlugin) apply false
}

version = AndroidConfig.VERSION.name

buildscript {
    repositories {
        // Magic Mirror Depot proxy (only set in CI via `.gitlab-ci.yml`).
        // Inlined here because `buildscript {}` is resolved before the `build-logic`
        // convention plugins are on the classpath.
        listOf("gradlePluginProxy", "mavenRepositoryProxy")
            .mapNotNull { providers.gradleProperty(it).orNull?.takeIf { url -> url.isNotBlank() } }
            .forEach { url -> maven { setUrl(url) } }
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath(libs.unmockGradlePlugin)
    }
}

allprojects {
    repositories.depotProxied(providers) {
        google()
        mavenCentral()
        maven { setUrl(com.datadog.gradle.Dependencies.Repositories.Jitpack) }
    }
}

nexusPublishing {
    this.repositories {
        sonatype {
            stagingProfileId = "378eecbbe2cf9"
            val sonatypeUsername = System.getenv("CENTRAL_PUBLISHER_USERNAME")
            val sonatypePassword = System.getenv("CENTRAL_PUBLISHER_PASSWORD")
            if (sonatypeUsername != null) username.set(sonatypeUsername)
            if (sonatypePassword != null) password.set(sonatypePassword)
            // see https://github.com/gradle-nexus/publish-plugin#publishing-to-maven-central-via-sonatype-central
            // For official documentation:
            // staging repo publishing https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
            // snapshot publishing https://central.sonatype.org/publish/publish-portal-snapshots/#publishing-via-other-methods
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}

dependencyLicenses {
    transitiveDependencies = true
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("checkAll") {
    dependsOn(
        "lintCheckAll",
        "unitTestAll",
        "instrumentTestAll"
    )
}

registerSubModuleAggregationTask("assembleLibraries", "assemble")
registerSubModuleAggregationTask("assembleLibrariesDebug", "assembleDebug")
registerSubModuleAggregationTask("assembleLibrariesRelease", "assembleRelease")

registerSubModuleAggregationTask("unitTestRelease", "testReleaseUnitTest")
registerSubModuleAggregationTask(
    "unitTestReleaseFeatures",
    "testReleaseUnitTest",
    ":features:"
)
registerSubModuleAggregationTask("unitTestReleaseIntegrations", "testReleaseUnitTest", ":integrations:")

registerSubModuleAggregationTask("unitTestDebug", "testDebugUnitTest")
registerSubModuleAggregationTask(
    "unitTestDebugFeatures",
    "testDebugUnitTest",
    ":features:"
)
registerSubModuleAggregationTask("unitTestDebugIntegrations", "testDebugUnitTest", ":integrations:")
tasks.register("unitTestDebugSamples") {
    dependsOn(
        ":sample:benchmark:testDebugUnitTest"
    )
}

tasks.register("assembleSampleRelease") {
    dependsOn(
        ":sample:kotlin:assembleUs1Release",
        ":sample:wear:assembleUs1Release",
        ":sample:vendor-lib:assembleRelease",
        ":sample:automotive:assembleRelease",
        ":sample:tv:assembleRelease"
    )
}

tasks.register("unitTestTools") {
    dependsOn(
        ":tools:unit:testJvmReleaseUnitTest",
        ":tools:detekt:test",
        ":tools:lint:test",
        ":tools:noopfactory:test",
        ":tools:benchmark:test"
    )
}

tasks.register("unitTestAll") {
    dependsOn(
        ":unitTestDebug",
        ":unitTestRelease",
        ":unitTestTools"
    )
}

registerSubModuleAggregationTask(
    "lintCheckAll",
    "lintRelease",
    // lint all modules, not only for published ones
    subModuleNamePrefix = ""
) {
    dependsOn(":tools:lint:lint")
}

registerSubModuleAggregationTask(
    "checkDependencyLicensesAll",
    "checkDependencyLicenses",
    // check licenses for all modules, not only for published ones
    subModuleNamePrefix = ""
)

registerSubModuleAggregationTask("checkApiSurfaceChangesAll", "checkApiSurfaceChanges")

registerSubModuleAggregationTask("checkCompilerMetadataChangesAll", "checkCompilerMetadataChanges")

registerSubModuleAggregationTask("checkAarMetadataInfoChangesAll", "checkAarMetadataInfoChanges")

registerSubModuleAggregationTask("checkTransitiveDependenciesListAll", "checkTransitiveDependenciesList")

tasks.register("checkGeneratedFiles") {
    dependsOn("checkDependencyLicensesAll")
    dependsOn("checkApiSurfaceChangesAll")
    dependsOn("checkCompilerMetadataChangesAll")
    dependsOn("checkAarMetadataInfoChangesAll")
    dependsOn("checkTransitiveDependenciesListAll")
}

registerSubModuleAggregationTask("koverReportAll", "koverXmlReportRelease")
registerSubModuleAggregationTask("koverReportFeatures", "koverXmlReportRelease", ":features:")
registerSubModuleAggregationTask("koverReportIntegrations", "koverXmlReportRelease", ":integrations:")

tasks.register("instrumentTestAll") {
    dependsOn(":instrumented:integration:connectedCheck")
}

tasks.register("buildIntegrationTestsArtifacts") {
    dependsOn(":instrumented:integration:assembleDebugAndroidTest")
    dependsOn(":instrumented:integration:assembleDebug")
}

tasks.register("buildNdkIntegrationTestsArtifacts") {
    dependsOn(":features:dd-sdk-android-ndk:assembleDebugAndroidTest")
    // we need this artifact to trick Bitrise
    dependsOn(":instrumented:integration:assembleDebug")
}

tasks.register("listAllPublishedArtifactIds") {
    doLast {
        val artifactIds = rootProject.subprojects.flatMap { subproject ->
            val publishing = subproject.extensions.findByType<PublishingExtension>()
            publishing?.publications?.mapNotNull { publication ->
                if (publication is MavenPublication) {
                    publication.artifactId
                } else {
                    null
                }
            }.orEmpty()
        }
        artifactIds.forEach {
            println(it)
        }
    }
}
