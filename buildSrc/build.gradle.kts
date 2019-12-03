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
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.61")
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
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.61")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.1.1")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:9.1.0")
    implementation("com.android.tools.build:gradle:3.5.2")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.27.0")
    implementation("me.xdrop:fuzzywuzzy:1.2.0")

    testImplementation("junit:junit:4.12")
}

gradlePlugin {
    plugins {
        register("thirdPartyLicences") {
            id = "thirdPartyLicences" // the alias
            implementationClass = "com.datadog.gradle.plugin.ThirdPartyLicensesPlugin"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}
