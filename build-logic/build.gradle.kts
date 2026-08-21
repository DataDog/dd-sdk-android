/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    alias(libs.plugins.versionsGradlePlugin)
    alias(libs.plugins.ktlintGradlePlugin)
}

buildscript {
    repositories {
        // Magic Mirror Depot proxy (only set in CI via `.gitlab-ci.yml`).
        listOf("gradlePluginProxy", "mavenRepositoryProxy")
            .mapNotNull { providers.gradleProperty(it).orNull?.takeIf { url -> url.isNotBlank() } }
            .forEach { url -> maven { setUrl(url) } }
        mavenCentral()
    }
}

repositories {
    // Magic Mirror Depot proxy (only set in CI via `.gitlab-ci.yml`).
    listOf("gradlePluginProxy", "mavenRepositoryProxy")
        .mapNotNull { providers.gradleProperty(it).orNull?.takeIf { url -> url.isNotBlank() } }
        .forEach { url -> maven { setUrl(url) } }
    mavenCentral()
    google()
    gradlePluginPortal()
    maven { setUrl("https://jitpack.io") }
}

dependencies {

    // Plugins whose types are only referenced when configuring a consuming project. They are
    // `compileOnly` on purpose: the consumer already applies them (the root project puts them on
    // the buildscript classpath via `apply false` aliases), and shipping a second copy from here
    // would put two AGP/KGP resolutions on the same classpath.
    compileOnly(libs.kotlinGradlePluginApi)
    compileOnly(libs.androidToolsGradlePluginApi)
    compileOnly(libs.versionsGradlePlugin)
    compileOnly(libs.dokkaGradlePlugin)
    compileOnly(libs.detektGradlePlugin)
    compileOnly(libs.ktlintGradlePlugin)

    // check api surface
    implementation(libs.kotlinGrammarParser)

    // JsonSchema 2 Poko
    implementation(libs.gson)
    implementation(libs.kotlinPoet)

    // Verification Metadata XML
    implementation(libs.kotlinXmlBuilder)

    // Tests
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.assertJ)
    testImplementation(libs.elmyr)
    testImplementation(libs.elmyrInject)
    testImplementation(libs.elmyrJUnit5)
    testImplementation(libs.elmyrJVM)
    // JSON Schema validation
    testImplementation(libs.jsonSchemaValidator)
}

gradlePlugin {
    plugins {
        register("datadogBuildConfig") {
            id = "datadogBuildConfig" // the alias
            implementationClass = "com.datadog.gradle.plugin.config.DatadogBuildConfigPlugin"
        }
        register("apiSurface") {
            id = "apiSurface" // the alias
            implementationClass = "com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin"
        }
        register("cloneDependencies") {
            id = "cloneDependencies" // the alias
            implementationClass = "com.datadog.gradle.plugin.gitclone.GitCloneDependenciesPlugin"
        }
        register("transitiveDependencies") {
            id = "transitiveDependencies" // the alias
            implementationClass = "com.datadog.gradle.plugin.transdeps.TransitiveDependenciesPlugin"
        }
        register("verificationXml") {
            id = "verificationXml" // the alias
            implementationClass = "com.datadog.gradle.plugin.verification.VerificationXmlPlugin"
        }
    }
}

java.targetCompatibility = JavaVersion.VERSION_17
java.sourceCompatibility = JavaVersion.VERSION_17

ktlint {
    version = provider { libs.versions.ktlint.get() }
    filter {
        exclude { it.file.invariantSeparatorsPath.contains("/build/generated") }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks {
    val copyTestRes = register<Copy>("copyTestRes") {
        from("$projectDir/src/test/kotlin/com/example/model")
        into("$projectDir/src/test/resources/output")
    }

    val deleteTestRes = register<Delete>("deleteTestRes") {
        delete("$projectDir/src/test/resources/output/")
    }

    named("processTestResources") {
        dependsOn(copyTestRes)
    }

    named("test") {
        dependsOn(copyTestRes)
        finalizedBy(deleteTestRes)
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
    )
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }
}
