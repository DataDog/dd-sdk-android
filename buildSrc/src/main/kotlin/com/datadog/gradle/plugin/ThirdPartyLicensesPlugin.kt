package com.datadog.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

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
