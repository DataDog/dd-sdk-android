/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.datadog.gradle.plugin.internal.DdAppIdentifier
import com.datadog.gradle.plugin.internal.DdConfiguration
import com.datadog.gradle.plugin.internal.OkHttpUploader
import com.datadog.gradle.plugin.internal.Uploader
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * A Gradle task to upload a Proguard/R8 mapping file to Datadog servers.
 */
open class DdMappingFileUploadTask : DefaultTask() {

    @get:Internal
    internal var uploader: Uploader = OkHttpUploader()

    /**
     * The API Key used to upload the data.
     */
    @get: Input
    var apiKey: String = ""

    /**
     * The variant name of the application.
     */
    @get:Input
    var variantName: String = ""

    /**
     * The version name of the application.
     */
    @get: Input
    var versionName: String = ""

    /**
     * The service name of the application (by default, it is your app's package name).
     */
    @get: Input
    var serviceName: String = ""

    /**
     * The environment name.
     */
    @get: Input
    var envName: String = ""

    /**
     * The Datadog site to upload to (one of "US", "EU", "GOV").
     */
    @get: Input
    var site: String = ""

    /**
     * The path to the mapping file to upload.
     */
    @get:Input
    var mappingFilePath: String = ""

    init {
        group = "datadog"
        description = "Uploads the Proguard/R8 mapping file to Datadog"
    }

    // region Task

    /**
     * Uploads the mapping file to Datadog.
     */
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

    // endregion

    // region Internal

    @Suppress("CheckInternal")
    private fun validateConfiguration() {
        check(apiKey.isNotBlank()) { "You need to provide a valid client token" }

        val validSiteIds = DdConfiguration.Site.values().map { it.name }
        check(site in validSiteIds) {
            "You need to provide a valid site (one of ${validSiteIds.joinToString()})"
        }
    }

    @Suppress("CheckInternal")
    private fun validateMappingFile(mappingFile: File): Boolean {
        if (!mappingFile.exists()) {
            println("There's no mapping file $mappingFilePath, nothing to upload")
            return false
        }

        check(mappingFile.isFile) { "Expected $mappingFilePath to be a file" }

        check(mappingFile.canRead()) { "Cannot read file $mappingFilePath" }

        return true
    }

    // endregion
}
