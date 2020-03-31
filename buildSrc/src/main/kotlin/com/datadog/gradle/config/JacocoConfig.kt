/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.datadog.gradle.Dependencies
import java.math.BigDecimal
import org.gradle.api.Project
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

fun Project.jacocoConfig() {

    val jacocoTestDebugUnitTestReport = tasks.create("jacocoTestDebugUnitTestReport", JacocoReport::class.java)
    jacocoTestDebugUnitTestReport.reports {
        csv.isEnabled = false
        xml.isEnabled = true
        html.isEnabled = true
        html.destination = file("${buildDir.path}/reports/jacoco/jacocoTestDebugUnitTestReport/html")
    }

    val jacocoTestReleaseUnitTestReport = tasks.create("jacocoTestReleaseUnitTestReport", JacocoReport::class.java)
    jacocoTestReleaseUnitTestReport.reports {
        csv.isEnabled = false
        xml.isEnabled = true
        html.isEnabled = true
        html.destination = file("${buildDir.path}/reports/jacoco/jacocoTestReleaseUnitTestReport/html")
    }

    val jacocoTestCoverageVerification =
        tasks.create("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java)
    jacocoTestCoverageVerification.violationRules {
        rule {
            limit {
                minimum = BigDecimal(0.85)
            }
        }
    }

    listOf(
        jacocoTestDebugUnitTestReport,
        jacocoTestReleaseUnitTestReport,
        jacocoTestCoverageVerification
    ).forEach { task ->
        val excludeFilters = arrayOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/data/models/*"
        )

        val debugTree = fileTree("${buildDir.path}/intermediates/classes/debug").apply {
            exclude(*excludeFilters)
        }
        val kotlinDebugTree = fileTree("${buildDir.path}/tmp/kotlin-classes/debug").apply {
            exclude(*excludeFilters)
        }
        val mainSrc = "${project.projectDir}/src/main/kotlin"

        task.classDirectories.setFrom(files(debugTree, kotlinDebugTree))
        task.executionData.setFrom(files("${buildDir.path}/jacoco/testDebugUnitTest.exec"))
        task.sourceDirectories.setFrom(files(mainSrc))
    }
    jacocoTestDebugUnitTestReport.dependsOn("testDebugUnitTest")
    jacocoTestReleaseUnitTestReport.dependsOn("testReleaseUnitTest")
    jacocoTestCoverageVerification.dependsOn(jacocoTestDebugUnitTestReport)

    extensionConfig<JacocoPluginExtension> {
        toolVersion = Dependencies.Versions.Jacoco
        reportsDir = file("$buildDir/jacoco") // Jacoco's output root.
    }

    tasks.named("check") {
        dependsOn(jacocoTestDebugUnitTestReport)
        dependsOn(jacocoTestReleaseUnitTestReport)
        dependsOn(jacocoTestDebugUnitTestReport)
    }
}
