/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.slf4j.LoggerFactory

/**
 * Plugin adding tasks for Android projects using Datadog's SDK for Android.
 */
class DdAndroidGradlePlugin : Plugin<Project> {

    // region Plugin

    /** @inheritdoc */
    override fun apply(target: Project) {
        val androidExtension = target.extensions.findByType(AppExtension::class.java)
        if (androidExtension == null) {
            System.err.println(ERROR_NOT_ANDROID)
            return
        }

        val extension = target.extensions.create(EXT_NAME, DdExtension::class.java)
        val apiKey = target.findProperty("DD_API_KEY")?.toString().orEmpty()

        target.afterEvaluate {
            androidExtension.applicationVariants.forEach {
                if (it.name.endsWith("Release")) {
                    configureVariant(target, it, apiKey, extension)
                }
            }
        }
    }

    // endregion

    // region Internal

    @Suppress("DefaultLocale")
    private fun configureVariant(
        target: Project,
        variant: ApplicationVariant,
        apiKey: String,
        extension: DdExtension
    ) {
        val flavorName = variant.name.removeSuffix("Release")
        val uploadTaskName = UPLOAD_TASK_NAME + flavorName.capitalize()

        val uploadTask = target.tasks.create(
            uploadTaskName,
            DdMappingFileUploadTask::class.java
        )
        uploadTask.apiKey = apiKey
        uploadTask.site = extension.site
        uploadTask.envName = extension.environmentName
        uploadTask.variantName = flavorName
        uploadTask.versionName = variant.versionName
        uploadTask.serviceName = variant.applicationId

        val outputsDir = File(target.buildDir, "outputs")
        val mappingDir = File(outputsDir, "mapping")
        val flavorDir = File(mappingDir, variant.name)
        uploadTask.mappingFilePath = File(flavorDir, "mapping.txt").path
    }

    // endregion

    companion object {

        internal val LOGGER = LoggerFactory.getLogger("DdAndroidGradlePlugin")

        private const val EXT_NAME = "datadog"

        private const val UPLOAD_TASK_NAME = "uploadMapping"

        private const val ERROR_NOT_ANDROID = "The dd-android-gradle-plugin has been applied on " +
            "a non android application project"
    }
}
