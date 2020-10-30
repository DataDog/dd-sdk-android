/*
 * Unless explicitly stated otherwise all pomFilesList in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
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

repositories {
    mavenCentral()
    google()
    maven { setUrl("https://plugins.gradle.org/m2/") }
    maven { setUrl("https://maven.google.com") }
    maven { setUrl("https://jitpack.io") }
}

dependencies {

    // Dependencies used to configure the gradle plugins
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.61")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.61")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.1.1")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:9.4.0")
    implementation("com.android.tools.build:gradle:4.0.1")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.27.0")
    implementation("me.xdrop:fuzzywuzzy:1.2.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.4.10")
    implementation("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.4")

    // check api surface
    implementation("com.github.kotlinx.ast:grammar-kotlin-parser-antlr-kotlin-jvm:c35b50fa44")

    // JsonSchema 2 Poko
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.squareup:kotlinpoet:1.6.0")

    // Tests
    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
    testImplementation("net.wuerl.kotlin:assertj-core-kotlin:0.2.1")
    testImplementation("com.github.xgouchet.Elmyr:core:1.0.0")
    testImplementation("com.github.xgouchet.Elmyr:inject:1.0.0")
    testImplementation("com.github.xgouchet.Elmyr:junit4:1.0.0")
    testImplementation("com.github.xgouchet.Elmyr:jvm:1.0.0")
    // Json Schema validation
    testImplementation("com.github.everit-org.json-schema:org.everit.json.schema:1.12.1")
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
        register("apiSurface") {
            id = "apiSurface" // the alias
            implementationClass = "com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin"
        }
        register("cloneDependencies") {
            id = "cloneDependencies" // the alias
            implementationClass = "com.datadog.gradle.plugin.gitclone.GitCloneDependenciesPlugin"
        }
        register("jsonschema2poko") {
            id = "jsonschema2poko" // the alias
            implementationClass = "com.datadog.gradle.plugin.jsonschema.JsonSchemaPlugin"
        }
        register("transitiveDependencies") {
            id = "transitiveDependencies" // the alias
            implementationClass = "com.datadog.gradle.plugin.transdeps.TransitiveDependenciesPlugin"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks {
    register<Copy>("copyTestRes") {
        from("$projectDir/src/test/kotlin/com/example/model")
        into("$projectDir/src/test/resources/output")
    }

    register<Delete>("deleteTestRes") {
        delete("$projectDir/src/test/resources/output/")
    }
}

tasks.named("test") {
    dependsOn("copyTestRes")
    finalizedBy("deleteTestRes")
}
