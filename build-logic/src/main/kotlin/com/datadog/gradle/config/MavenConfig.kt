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

    private const val INTERNAL_REPOSITORY_UPLOAD_BASE = "s3://binaries-ddbuild-io-prod/dd-sdk-android"

    const val DOGFOOD_REPOSITORY = "dogfood"
    const val DOGFOOD_REPOSITORY_URL = "$INTERNAL_REPOSITORY_UPLOAD_BASE/$DOGFOOD_REPOSITORY"
    const val BENCHMARK_REPOSITORY = "benchmark"
    const val BENCHMARK_REPOSITORY_URL = "$INTERNAL_REPOSITORY_UPLOAD_BASE/$BENCHMARK_REPOSITORY"

    /**
     * Task uploading the [PUBLICATION] to the `sonatype` repository, which the root
     * `io.github.gradle-nexus.publish-plugin` adds to every published module.
     */
    const val SONATYPE_PUBLISH_TASK = "publishReleasePublicationToSonatypeRepository"
}

/** Where the releases and snapshots of a module are published. */
enum class PublishingDestination {
    /** Public modules, published to Maven Central through Sonatype. */
    MAVEN_CENTRAL,

    /** Published to the benchmark repository on binaries.ddbuild.io, which is only reachable from Datadog's network. */
    INTERNAL_BENCHMARK
}

internal data class InternalMavenRepository(val name: String, val url: String)

/**
 * Dogfood versions get the dogfood repository whatever the destination, so every module is available there at the
 * same version.
 * @return the internal binaries.ddbuild.io repository registered for a build, or null if there is none.
 */
internal fun internalRepositoryFor(
    versionType: Version.Type,
    destination: PublishingDestination
): InternalMavenRepository? {
    return when {
        versionType is Version.Type.Dogfood -> InternalMavenRepository(
            MavenConfig.DOGFOOD_REPOSITORY,
            MavenConfig.DOGFOOD_REPOSITORY_URL
        )

        destination == PublishingDestination.INTERNAL_BENCHMARK -> InternalMavenRepository(
            MavenConfig.BENCHMARK_REPOSITORY,
            MavenConfig.BENCHMARK_REPOSITORY_URL
        )

        else -> null
    }
}

internal fun Project.publishingConfig(
    projectDescription: String,
    customArtifactId: String = name,
    destination: PublishingDestination = PublishingDestination.MAVEN_CENTRAL
) {
    val projectName = name

    if (destination != PublishingDestination.MAVEN_CENTRAL) {
        // The root nexus plugin adds the Sonatype repository to every published module and has no opt-out,
        // so the upload task is disabled instead. Matching by name keeps this lazy and independent of the
        // order in which the task is registered.
        tasks.named { it == MavenConfig.SONATYPE_PUBLISH_TASK }.configureEach {
            enabled = false
        }
    }

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

            // Internal builds go to binaries.ddbuild.io (internal only), using the CI runner's IAM role.
            // Only registered when the version and destination allow it, so other builds can't be uploaded there.
            val internalRepository = internalRepositoryFor(AndroidConfig.VERSION.type, destination)
            if (internalRepository != null) {
                repositories {
                    maven {
                        name = internalRepository.name
                        url = uri(internalRepository.url)
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
