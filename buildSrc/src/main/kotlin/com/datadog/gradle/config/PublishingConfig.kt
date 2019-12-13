/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.jvm.tasks.Jar

fun Project.publishingConfig(localRepo: String) {

    javadocConfig()
    val projectName = name

    extensionConfig<PublishingExtension> {
        repositories {
            maven {
                setUrl(localRepo)
            }
        }

        publications {
            register("aar", MavenPublication::class.java) {
                groupId = MavenConfig.GROUP_ID
                artifactId = projectName
                version = AndroidConfig.VERSION.name
                artifact("$buildDir/outputs/aar/$projectName-release.aar")
                artifact(tasks.findByName("sourcesJar"))
                artifact(tasks.findByName("generateJavadoc"))

                // publishing AAR doesn't fill the pom.xml dependencies.
                pom.withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations.named("implementation").get().allDependencies.forEach {
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", it.group)
                        dependencyNode.appendNode("artifactId", it.name)
                        dependencyNode.appendNode("version", it.version)
                    }
                }
            }
        }
    }

    tasks.register("sourcesJar", Jar::class.java) {
        archiveClassifier.convention("sources")
        from("${projectDir.canonicalPath}/src/main")
    }

    tasks.withType(AbstractPublishToMaven::class.java) {
        this.dependsOn("bundleReleaseAar")
    }

    task("publishLocalAndRemote").apply {
        this.group = "publishing"
        this.dependsOn("publish")
        this.dependsOn("publishToMavenLocal")
    }
}
