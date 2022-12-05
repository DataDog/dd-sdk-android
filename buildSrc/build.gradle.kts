/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    @Suppress("DSL_SCOPE_VIOLATION", "UnstableApiUsage")
    alias(libs.plugins.versionsGradlePlugin)
}

buildscript {
    repositories {
        mavenCentral()
    }
}

repositories {
    mavenCentral()
    google()
    maven { setUrl("https://plugins.gradle.org/m2/") }
    maven { setUrl("https://maven.google.com") }
    maven { setUrl("https://jitpack.io") }
}

dependencies {

    // Dependencies used to configure the gradle plugins
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.detektGradlePlugin)
    implementation(libs.androidToolsGradlePlugin)
    implementation(libs.versionsGradlePlugin)
    implementation(libs.fuzzyWuzzy)
    implementation(libs.dokkaGradlePlugin)
    implementation(libs.mavenModel)
    implementation(libs.nexusPublishGradlePlugin)
    implementation(libs.kover)

    // check api surface
    implementation(libs.kotlinGrammarParser)

    // JsonSchema 2 Poko
    implementation(libs.gson)
    implementation(libs.kotlinPoet)

    // Tests
    testImplementation(libs.jUnit4)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.assertJ)
    testImplementation(libs.elmyr)
    testImplementation(libs.elmyrInject)
    testImplementation(libs.elmyrJUnit4)
    testImplementation(libs.elmyrJVM)
    // Json Schema validation
    testImplementation(libs.jsonSchemaValidator)
}

gradlePlugin {
    plugins {
        register("thirdPartyLicences") {
            id = "thirdPartyLicences" // the alias
            implementationClass = "com.datadog.gradle.plugin.checklicenses.ThirdPartyLicensesPlugin"
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
        register("wiki") {
            id = "wiki" // the alias
            implementationClass = "com.datadog.gradle.plugin.wiki.WikiPlugin"
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
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
