/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import org.gradle.api.Project
import org.gradle.api.plugins.MavenRepositoryHandlerConvention
import org.gradle.api.tasks.Upload
import org.gradle.internal.impldep.org.sonatype.aether.repository.Authentication
import org.gradle.internal.impldep.org.sonatype.aether.repository.RemoteRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension

object MavenConfig {
    const val GROUP_ID = "com.datadoghq"
    const val PASSWORD = "ossrhPassword"
    const val USER_NAME = "ossrhUsername"
}

fun Project.mavenConfig() {

    version = AndroidConfig.VERSION.name
    group = MavenConfig.GROUP_ID
    val projectName = name

    // region Artifact Definition

    @Suppress("UnstableApiUsage")
    tasks.register("sourcesJar", Jar::class.java) {
        @Suppress("DEPRECATION")
        classifier = "sources"
        archiveClassifier.convention("sources")
        from("${projectDir.canonicalPath}/src/main")
    }

    // endregion

    // region Upload Configuration

    taskConfig<Upload> {
        repositories {
            val mavenHandler = this as? MavenRepositoryHandlerConvention ?: return@repositories

            mavenHandler.mavenDeployer {
                beforeDeployment {
                    @Suppress("DEPRECATION")
                    this@mavenConfig.extensions.findByType(SigningExtension::class.java)
                        ?.signPom(this)
                }

                repository = RemoteRepository().apply {
                    url = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                    // To publish to local URL, uncomment next line
                    // url = "http://localhost:8081/nexus/service/local/staging/deploy/maven2/"

                    val username = System.getenv("OSSRH_USERNAME")
                    val password = System.getenv("OSSRH_PASSWORD")
                    if ((!username.isNullOrEmpty()) && (!password.isNullOrEmpty())) {
                        authentication = Authentication(username, password)
                    }
                }

                pom.groupId = MavenConfig.GROUP_ID
                pom.artifactId = projectName
                pom.version = AndroidConfig.VERSION.name
                pom.project {
                    check(this is Model)
                    name = projectName
                    packaging = "aar"
                    url = "https://www.datadoghq.com/"
                    scm = Scm().apply {
                        url = "https://github.com/DataDog/dd-sdk-android.git"
                        connection = "scm:git@github.com:DataDog/dd-sdk-android.git"
                        developerConnection = "scm:git@github.com:DataDog/dd-sdk-android.git"
                    }
                    addLicense(License().apply {
                        name = "The Apache Software License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    })

                    addDeveloper(Developer().apply {
                        id = "DataDog"
                        name = "Datadog, Inc."
                    })
                }
            }
        }
    }

    afterEvaluate {
        artifacts {
            add("archives", tasks.findByName("sourcesJar")!!)
        }
    }

    @Suppress("UnstableApiUsage")
    extensionConfig<SigningExtension> {
        val privateKey = System.getenv("GPG_PRIVATE_KEY")
        val password = System.getenv("GPG_PASSWORD")
        isRequired = true
        useInMemoryPgpKeys(privateKey, password)
        sign(configurations.getByName("archives"))
    }

    // endregion
}
