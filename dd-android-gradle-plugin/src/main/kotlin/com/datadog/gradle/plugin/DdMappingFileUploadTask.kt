/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.datadog.gradle.plugin.internal.OkHttpUploader
import com.datadog.gradle.plugin.internal.Uploader
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

open class DdMappingFileUploadTask : DefaultTask() {

    @get:Internal
    internal var uploader: Uploader = OkHttpUploader()

    @get:Input
    var variantName: String = ""

    @get: Input
    var apiKey: String = ""

    @get: Input
    var versionName: String = ""

    @get: Input
    var serviceName: String = ""

    @get: Input
    var envName: String = ""

    @get: Input
    var site: String = ""

    @get:Input
    var mappingFilePath: String = ""

    init {
        group = "datadog"
        description = "Uploads the Proguard/R8 mapping file to Datadog"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        validateConfiguration()

        val mappingFile = File(mappingFilePath)
        if (!validateMappingFile(mappingFile)) return

        println(
            "Uploading mapping file for configuration:\n" +
                "- envName: {$envName}\n" +
                "- versionName: {$versionName}\n" +
                "- variantName: {$variantName}\n" +
                "- serviceName: {$serviceName}\n"
        )

        val configuration = DdConfiguration(
            site = DdConfiguration.Site.valueOf(site),
            apiKey = apiKey
        )
        uploader.upload(
            configuration.buildUrl(),
            mappingFile,
            DdAppIdentifier(
                serviceName = serviceName,
                envName = envName,
                version = versionName,
                variant = variantName
            )
        )
    }

    private fun validateConfiguration() {
        if (apiKey.isBlank()) {
            throw IllegalStateException("You need to provide a valid client token")
        }

        val validSiteIds = DdConfiguration.Site.values().map { it.name }
        if (site !in validSiteIds) {
            throw IllegalStateException("You need to provide a valid site (one of ${validSiteIds.joinToString()})")
        }
    }

    private fun validateMappingFile(mappingFile: File): Boolean {
        if (!mappingFile.exists()) {
            println("There's no mapping file $mappingFilePath, nothing to upload")
            return false
        }
        if (!mappingFile.isFile) {
            throw IllegalStateException("Expected $mappingFilePath to be a file")
        }

        if (!mappingFile.canRead()) {
            throw IllegalStateException("Cannot read file $mappingFilePath")
        }
        return true
    }

    // endregion
}
