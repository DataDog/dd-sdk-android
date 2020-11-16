/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask

fun Project.javadocConfig() {

    @Suppress("UnstableApiUsage")
    tasks.register("generateJavadoc", Jar::class.java) {
        dependsOn("dokkaJavadoc")
        archiveClassifier.convention("javadoc")
        from("${buildDir.canonicalPath}/reports/javadoc")
    }

    tasks.withType(DokkaTask::class.java) {
        val toOutputDirectory = file("${buildDir.canonicalPath}/reports/javadoc")
        outputDirectory.set(toOutputDirectory)
        doFirst {
            if (!toOutputDirectory.exists()) {
                toOutputDirectory.mkdirs()
            }
        }
    }
}
