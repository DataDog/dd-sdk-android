/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import com.android.build.api.dsl.LibraryExtension
import com.datadog.gradle.utils.Version
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.aws.AwsImAuthentication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.plugins.signing.SigningExtension

object MavenConfig {
    const val GROUP_ID = "com.datadoghq"
    const val PUBLICATION = "release"
    const val DOGFOOD_REPOSITORY = "dogfood"
    const val DOGFOOD_REPOSITORY_URL = "s3://binaries-ddbuild-io-prod/dd-sdk-android/dogfood"
}

internal fun Project.publishingConfig(
    projectDescription: String,
    customArtifactId: String = name
) {
    val projectName = name

    val androidExtension =
        extensions.findByType(LibraryExtension::class.java)
    if (androidExtension == null) {
        logger.error("Missing android library extension for $projectName")
        return
    }

    androidExtension.publishing {
        singleVariant(MavenConfig.PUBLICATION) {
            withSourcesJar()
            withJavadocJar()
        }
    }

    afterEvaluate {
        val publishingExtension = extensions.findByType(PublishingExtension::class)
        val signingExtension = extensions.findByType(SigningExtension::class)
        if (publishingExtension == null || signingExtension == null) {
            logger.error("Missing publishing or signing extension for $projectName")
            return@afterEvaluate
        }

        publishingExtension.apply {
            publications.create<MavenPublication>(MavenConfig.PUBLICATION) {
                from(components.getByName("release"))

                groupId = MavenConfig.GROUP_ID
                artifactId = customArtifactId
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

            // Dogfood builds go to binaries.ddbuild.io (internal only), using the CI runner's IAM role.
            // Only registered for dogfood versions, so other builds can't be uploaded there.
            if (AndroidConfig.VERSION.type is Version.Type.Dogfood) {
                repositories {
                    maven {
                        name = MavenConfig.DOGFOOD_REPOSITORY
                        url = uri(MavenConfig.DOGFOOD_REPOSITORY_URL)
                        authentication {
                            create<AwsImAuthentication>("awsIm")
                        }
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
