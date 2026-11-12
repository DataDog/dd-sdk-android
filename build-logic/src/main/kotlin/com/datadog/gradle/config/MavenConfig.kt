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
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.aws.AwsImAuthentication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension

private const val ORGANIZATION_NAME = "Datadog"
private const val ORGANIZATION_URL = "https://www.datadoghq.com/"
private const val PROJECT_URL = "https://github.com/DataDog/dd-sdk-android/"
private const val SCM_CONNECTION = "scm:git:git@github.com:Datadog/dd-sdk-android.git"
private const val PUBLISHED_VARIANT = "release"

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
        singleVariant(PUBLISHED_VARIANT) {
            withSourcesJar()
            withJavadocJar()
        }
    }

    // Android creates the release component during project evaluation.
    afterEvaluate {
        val publishingExtension = extensions.findByType(PublishingExtension::class)
        val signingExtension = extensions.findByType(SigningExtension::class)
        if (publishingExtension == null || signingExtension == null) {
            logger.error("Missing publishing or signing extension for $projectName")
            return@afterEvaluate
        }

        val publication = publishingExtension.publications.create<MavenPublication>(MavenConfig.PUBLICATION) {
            from(components.getByName(PUBLISHED_VARIANT))

            groupId = MavenConfig.GROUP_ID
            artifactId = customArtifactId
            version = AndroidConfig.VERSION.name

            pom {
                configureMetadata(projectName, projectDescription)
            }
        }

        // Only dogfood versions register the internal repository, so other builds can't be uploaded there.
        if (AndroidConfig.VERSION.type is Version.Type.Dogfood) {
            publishingExtension.configureDogfoodRepository()
        }

        signingExtension.configureSigning(publication, signingRequired = !hasProperty("dd-skip-signing"))
    }
}

private fun MavenPom.configureMetadata(projectName: String, projectDescription: String) {
    name.set(projectName)
    description.set(projectDescription)
    url.set(PROJECT_URL)

    licenses {
        license {
            name.set("Apache-2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0")
        }
    }

    organization {
        name.set(ORGANIZATION_NAME)
        url.set(ORGANIZATION_URL)
    }

    developers {
        developer {
            name.set(ORGANIZATION_NAME)
            email.set("info@datadoghq.com")
            organization.set(ORGANIZATION_NAME)
            organizationUrl.set(ORGANIZATION_URL)
        }
    }

    scm {
        url.set(PROJECT_URL)
        connection.set(SCM_CONNECTION)
        developerConnection.set(SCM_CONNECTION)
    }
}

private fun PublishingExtension.configureDogfoodRepository() {
    // Dogfood builds go to binaries.ddbuild.io (internal only), using the CI runner's IAM role.
    repositories.maven {
        name = MavenConfig.DOGFOOD_REPOSITORY
        setUrl(MavenConfig.DOGFOOD_REPOSITORY_URL)
        authentication {
            register<AwsImAuthentication>("awsIm")
        }
    }
}

private fun SigningExtension.configureSigning(publication: MavenPublication, signingRequired: Boolean) {
    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val password = System.getenv("GPG_PASSWORD")
    isRequired = signingRequired
    useInMemoryPgpKeys(privateKey, password)
    sign(publication)
}
