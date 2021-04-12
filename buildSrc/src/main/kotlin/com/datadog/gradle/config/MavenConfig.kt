/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import java.net.URI
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.findByType
import org.gradle.plugins.signing.SigningExtension

object MavenConfig {
    const val GROUP_ID = "com.datadoghq"
    const val PUBLICATION = "release"
}

@Suppress("UnstableApiUsage")
fun Project.publishingConfig(projectDescription: String) {
    val projectName = name

    @Suppress("UnstableApiUsage")
    tasks.register("generateJavadocJar", Jar::class.java) {
        group = "publishing"
        dependsOn("dokkaJavadoc")
        archiveClassifier.convention("javadoc")
        from("${buildDir.canonicalPath}/reports/javadoc")
    }

    @Suppress("UnstableApiUsage")
    tasks.register("generateSourcesJar", Jar::class.java) {
        group = "publishing"
        archiveClassifier.convention("sources")
        from("${projectDir.canonicalPath}/src/main")
    }

    afterEvaluate {
        val publishingExtension = extensions.findByType(PublishingExtension::class)
        val signingExtension = extensions.findByType(SigningExtension::class)
        if (publishingExtension == null || signingExtension == null) {
            System.err.println("Missing publishing or signing extension for $projectName")
            return@afterEvaluate
        }

        publishingExtension.apply {
            repositories.maven {
                url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                val username = System.getenv("OSSRH_USERNAME")
                val password = System.getenv("OSSRH_PASSWORD")
                if ((!username.isNullOrEmpty()) && (!password.isNullOrEmpty())) {
                    credentials(PasswordCredentials::class.java) {
                        setUsername(username)
                        setPassword(password)
                    }
                } else {
                    System.err.println("Missing publishing credentials for $projectName")
                }
            }

            publications.create(MavenConfig.PUBLICATION, MavenPublication::class.java) {
                from(components.getByName("release"))
                artifact(tasks.findByName("generateSourcesJar"))
                artifact(tasks.findByName("generateJavadocJar"))

                groupId = MavenConfig.GROUP_ID
                artifactId = projectName
                version = AndroidConfig.VERSION.name

                pom {
                    name.set(projectName)
                    description.set(projectDescription)
                    url.set("https://github.com/DataDog/dd-sdk-android/")

                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    organization {
                        name.set("Datadog")
                        url.set("https://www.datadoghq.com/")
                    }
                    developers {
                        developer {
                            name.set("Datadog")
                            email.set("info@datadoghq.com")
                            organization.set("Datadog")
                            organizationUrl.set("https://www.datadoghq.com/")
                        }
                    }

                    scm {
                        url.set("https://github.com/DataDog/dd-sdk-android/")
                        connection.set("scm:git:git@github.com:Datadog/dd-sdk-android.git")
                        developerConnection.set("scm:git:git@github.com:Datadog/dd-sdk-android.git")
                    }
                }
            }
        }

        signingExtension.apply {
            val privateKey = System.getenv("GPG_PRIVATE_KEY")
            val password = System.getenv("GPG_PASSWORD")
            isRequired = !hasProperty("dd-skip-signing")
            useInMemoryPgpKeys(privateKey, password)
            sign(publishingExtension.publications.getByName(MavenConfig.PUBLICATION))
        }
    }
}
