package com.datadog.gradle.config

import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.File
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask

fun Project.javadocConfig() {

    @Suppress("UnstableApiUsage")
    val javadocTask = tasks.register("generateJavadoc", Jar::class.java) {
        dependsOn("dokka")
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

        javadocTask.dependsOn(this)
    }
}
