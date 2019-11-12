/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.gradle.plugin

import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project

class ThirdPartyLicensesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions
            .create(EXT_NAME, ThirdPartyLicensesExtension::class.java)
        extension.output = File(target.rootDir, ThirdPartyLicensesExtension.DEFAULT_TP_LICENCE_FILENAME)

        val updateTask = target.tasks
            .create(TASK_UPDATE_NAME, UpdateThirdPartyLicensesTask::class.java)
        updateTask.extension = extension
    }

    companion object {
        const val EXT_NAME = "thirdPartyLicences"

        const val TASK_UPDATE_NAME = "updateThirdPartyLicences"
    }
}
