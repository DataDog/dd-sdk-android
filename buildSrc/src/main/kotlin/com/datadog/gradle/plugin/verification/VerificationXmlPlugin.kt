/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.verification

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import kotlin.io.path.Path

class VerificationXmlPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val genTask = target.tasks
            .register<GenerateVerificationXmlTask>(TASK_GEN_VERIFICATION_XML) {
                projectName.set(project.name)
                projectGroup.set(project.group.toString())

                aarFile.set(
                    project.layout.buildDirectory.file(
                        Path(
                            "outputs",
                            "aar",
                            "${project.name}-release.aar"
                        ).toString()
                    )
                )

                moduleFile.set(
                    project.layout.buildDirectory.file(
                        Path("publications", "release", "module.json").toString()
                    )
                )

                pomFile.set(
                    project.layout.buildDirectory.file(
                        Path("publications", "release", "pom-default.xml").toString()
                    )
                )

                outputFile.set(project.layout.projectDirectory.file(XML_FILE_NAME))

                dependsOn("bundleReleaseAar")
                dependsOn("javaDocReleaseJar")
                dependsOn("sourceReleaseJar")
                dependsOn("generatePomFileForReleasePublication")
                dependsOn("generateMetadataFileForReleasePublication")
                dependsOn("signReleasePublication")
            }

        target.tasks.named { it == "publishToSonatype" }.configureEach {
            dependsOn(genTask)
        }

        target.tasks.named { it == "publishToMavenLocal" }.configureEach {
            dependsOn(genTask)
        }
    }

    companion object {

        const val TASK_GEN_VERIFICATION_XML = "generateVerificationXml"
        const val XML_FILE_NAME = "verification-metadata.xml"
    }
}
