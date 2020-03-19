/*
 * Unless explicitly stated otherwise all pomFilesList in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("com.github.ben-manes.versions") version ("0.27.0")
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.70")
    }
    repositories {
        mavenCentral()
    }
}

apply(plugin = "kotlin")
apply(plugin = "java-gradle-plugin")

repositories {
    mavenCentral()
    google()
    maven { setUrl("https://plugins.gradle.org/m2/") }
    maven { setUrl("https://maven.google.com") }
}

dependencies {

    // Dependencies used to configure the gradle plugins
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.61")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.70")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.1.1")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:9.1.0")
    implementation("com.android.tools.build:gradle:3.6.1")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.27.0")
    implementation("me.xdrop:fuzzywuzzy:1.2.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:0.10.0")
    implementation("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4")

    // check api surface
    implementation("net.java.dev.jna:jna:4.2.2")
    implementation("com.github.cretz.kastree:kastree-ast-jvm:0.4.0")
    implementation("com.github.cretz.kastree:kastree-ast-psi:0.4.0")

    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("net.wuerl.kotlin:assertj-core-kotlin:0.2.1")
}

gradlePlugin {
    plugins {
        register("reviewBenchmark") {
            id = "reviewBenchmark" // the alias
            implementationClass = "com.datadog.gradle.plugin.benchmark.ReviewBenchmarkPlugin"
        }
        register("thirdPartyLicences") {
            id = "thirdPartyLicences" // the alias
            implementationClass = "com.datadog.gradle.plugin.checklicenses.ThirdPartyLicensesPlugin"
        }
        register("gitDiffConditional") {
            id = "gitDiffConditional" // the alias
            implementationClass = "com.datadog.gradle.plugin.gitdiff.GitConditionalDependencyPlugin"
        }
        register("apiSurface") {
            id = "apiSurface" // the alias
            implementationClass = "com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin"
        }
        register("cloneDependencies") {
            id = "cloneDependencies" // the alias
            implementationClass = "com.datadog.gradle.plugin.gitclone.GitCloneDependenciesPlugin"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}
