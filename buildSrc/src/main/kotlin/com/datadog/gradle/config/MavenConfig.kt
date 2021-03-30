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
import org.gradle.kotlin.dsl.findByType
import org.gradle.plugins.signing.SigningExtension

object MavenConfig {
    const val GROUP_ID = "com.datadoghq"
    const val PUBLICATION = "release"
}

@Suppress("UnstableApiUsage")
fun Project.publishingConfig() {
    val projectName = name

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
                groupId = MavenConfig.GROUP_ID
                artifactId = projectName
                version = AndroidConfig.VERSION.name
            }
        }

        signingExtension.apply {
            val privateKey = System.getenv("GPG_PRIVATE_KEY")
            val password = System.getenv("GPG_PASSWORD")
            isRequired = true
            useInMemoryPgpKeys(privateKey, password)
            sign(publishingExtension.publications.getByName(MavenConfig.PUBLICATION))
        }
    }
}
