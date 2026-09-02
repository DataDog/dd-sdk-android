/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.aarmetadata

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * Extracts the AAR metadata properties (`META-INF/com/android/build/gradle/aar-metadata.properties`)
 * from the produced debug AAR into a file checked into the repository, so that any change of the
 * consumer constraints (min compile SDK, min AGP version, ...) is visible in the diff.
 */
class AarMetadataPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN) {
            registerTasks(target)
        }
    }

    private fun registerTasks(target: Project) {
        val aarMetadataFile = target.layout.projectDirectory
            .dir("api")
            .file(FILE_NAME)

        val generateAarMetadataTask = target.tasks
            .register<GenerateAarMetadataInfoTask>(TASK_GEN_AAR_METADATA) {
                this.aarMetadataFile.set(aarMetadataFile)
            }

        target.tasks
            .register<CheckAarMetadataInfoTask>(TASK_CHECK_AAR_METADATA) {
                this.aarMetadataFile.set(aarMetadataFile)
                dependsOn(generateAarMetadataTask)
            }

        target.extensions.configure<LibraryAndroidComponentsExtension>("androidComponents") {
            onVariants(selector().withName(VARIANT_NAME)) { variant ->
                generateAarMetadataTask.configure {
                    this.aarFile.set(variant.artifacts.get(SingleArtifact.AAR))
                }
            }
        }

        target.tasks.matching { it.name == BUNDLE_AAR_TASK }.configureEach {
            finalizedBy(generateAarMetadataTask)
        }
    }

    companion object {
        const val TASK_GEN_AAR_METADATA = "generateAarMetadataInfo"
        const val TASK_CHECK_AAR_METADATA = "checkAarMetadataInfoChanges"
        const val FILE_NAME = "aar-metadata.txt"
        const val ANDROID_LIBRARY_PLUGIN = "com.android.library"
        const val VARIANT_NAME = "debug"
        const val BUNDLE_AAR_TASK = "bundleDebugAar"
    }
}
