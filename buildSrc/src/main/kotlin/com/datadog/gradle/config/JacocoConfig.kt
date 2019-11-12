/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.testing.jacoco.tasks.JacocoReport

fun Project.jacocoConfig() {

    val jacocoTestDebugUnitTestReport = tasks.create("jacocoTestDebugUnitTestReport", JacocoReport::class.java)

    jacocoTestDebugUnitTestReport.apply {
        reports {
            csv.isEnabled = false
            xml.isEnabled = true
            html.isEnabled = true
            html.destination = file("${buildDir.path}/reports/jacoco/jacocoTestDebugUnitTestReport/html")
        }

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

        classDirectories.setFrom(files(debugTree, kotlinDebugTree))
        executionData.setFrom(files("${buildDir.path}/jacoco/testDebugUnitTest.exec"))
        sourceDirectories.setFrom(files(mainSrc))
    }
    jacocoTestDebugUnitTestReport.dependsOn("testDebugUnitTest")

    //
    // task jacocoTestReport(type: JacocoReport, dependsOn: ['testDebugUnitTest']) {
    //     reports {
    //         xml.enabled = true
    //         html.enabled = true
    //     }
    //
    //     def fileFilter = ['**/R.class',
    //         '**/R$*.class',
    //         '**/BuildConfig.*',
    //         '**/Manifest*.*',
    //         '**/*Test*.*',
    //         'android/**/*.*',
    //         '**/data/models/*']
    //     def debugTree = fileTree(dir: "${buildDir}/intermediates/classes/debug", excludes: fileFilter)
    //     def mainSrc = "${project.projectDir}/src/main/java"
    //
    //     sourceDirectories = files([mainSrc])
    //     classDirectories = files([debugTree])
    //     executionData = fileTree(dir: "$buildDir", includes: [
    //     "jacoco/testDebugUnitTest.exec",
    //     "outputs/code-coverage/connected/*coverage.ec"
    //     ])
    //
    // }

    // extensionConfig<JacocoPluginExtension> {
    //     toolVersion = Dependencies.Versions.Jacoco
    //     reportsDir = file("$buildDir/jacoco") // Jacoco's output root.
    // }

    // taskConfig<JacocoReport> {
    //     reports {
    //         csv.isEnabled = false
    //         xml.isEnabled = true
    //         html.isEnabled = true
    //         html.destination = file("$buildDir/jacoco/html")
    //     }
    // }

//    tasks.withType(JacocoCoverageVerification::class.java) {
//        violationRules {
//            rule {
//                limit {
//                    minimum = 0.85.toBigDecimal()
//                }
//            }
//        }
//    }

    tasks.named("check") {
        dependsOn(jacocoTestDebugUnitTestReport)
    }
}
