package com.datadog.gradle.config

import java.io.File
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask

fun Project.javadocConfig() {

    tasks.register("generateJavadoc", Jar::class.java) {
        dependsOn(":dd-sdk-android:dokka")
        archiveClassifier.convention("javadoc")
        from("${buildDir.canonicalPath}/reports/javadoc")
    }

    tasks.withType(DokkaTask::class.java) {
        outputFormat = "javadoc"
        outputDirectory = "${buildDir.canonicalPath}/reports/javadoc"
        doFirst {
            val outputDir = File(outputDirectory)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
        }
    }
}
