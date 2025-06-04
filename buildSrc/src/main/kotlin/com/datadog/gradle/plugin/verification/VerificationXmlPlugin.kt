/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.verification

import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.Plugin
import org.gradle.api.Project

class VerificationXmlPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val genTask = target.tasks.register(TASK_GEN_VERIFICATION_XML, GenerateVerificationXmlTask::class.java)

        target.afterEvaluate {
            genTask.dependsOn("bundleReleaseAar")
            genTask.dependsOn("javaDocReleaseJar")
            genTask.dependsOn("sourceReleaseJar")
            genTask.dependsOn("generatePomFileForReleasePublication")
            genTask.dependsOn("generateMetadataFileForReleasePublication")
            // TODO RUM-3104 depends on "signReleasePublication"

            getTasksByName("publishToSonatype", false).forEach {
                it.dependsOn(genTask)
            }
            getTasksByName("publishToMavenLocal", false).forEach {
                it.dependsOn(genTask)
            }
        }
    }

    companion object {

        const val TASK_GEN_VERIFICATION_XML = "generateVerificationXml"
        const val XML_FILE_NAME = "verification-metadata.xml"
    }
}
