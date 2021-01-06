/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.delegateClosureOf

const val MAVEN_PUBLICATION = "aar"
const val BINTRAY_USER = "bintrayUser"
const val BINTRAY_API_KEY = "bintrayApiKey"

fun Project.publishingConfig(
    localRepo: String,
    asAar: Boolean = true
) {

    version = AndroidConfig.VERSION.name
    group = MavenConfig.GROUP_ID

    val projectName = name

    extensionConfig<PublishingExtension> {
        repositories {
            maven {
                setUrl(localRepo)
            }
        }

        publications {
            register(MAVEN_PUBLICATION, MavenPublication::class.java) {
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

    @Suppress("UnstableApiUsage")
    tasks.register("sourcesJar", Jar::class.java) {
        archiveClassifier.convention("sources")
        from("${projectDir.canonicalPath}/src/main")
    }

    if (asAar) {
        tasks.withType(AbstractPublishToMaven::class.java) {
            this.dependsOn("bundleReleaseAar")
            this.dependsOn("sourcesJar")
            this.dependsOn("generateJavadoc")
        }
    } else {
        tasks.withType(AbstractPublishToMaven::class.java) {
            this.dependsOn("jar")
            this.dependsOn("sourcesJar")
            this.dependsOn("generateJavadoc")
        }
    }

    task("publishLocalAndRemote").apply {
        this.group = "publishing"
        this.dependsOn("publish")
        this.dependsOn("publishToMavenLocal")
    }
}

fun Project.bintrayConfig() {
    val projectName = name

    extensionConfig<BintrayExtension> {

        user = this@bintrayConfig.findProperty(BINTRAY_USER)?.toString()
        key = this@bintrayConfig.findProperty(BINTRAY_API_KEY)?.toString()

        setPublications(MAVEN_PUBLICATION)

        // dryRun = true
        override = true
        publish = true

        pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
            repo = "datadog-maven"
            name = projectName
            userOrg = "datadog"
            desc = "Datadog SDK fot Android"
            websiteUrl = "https://www.datadoghq.com/"
            setLicenses("Apache-2.0")
            githubRepo = "DataDog/dd-sdk-android"
            githubReleaseNotesFile = "README.md"
            vcsUrl = "https://github.com/DataDog/dd-sdk-android.git"

            version(delegateClosureOf<BintrayExtension.VersionConfig> {
                name = AndroidConfig.VERSION.name
            })
        })
    }
}
