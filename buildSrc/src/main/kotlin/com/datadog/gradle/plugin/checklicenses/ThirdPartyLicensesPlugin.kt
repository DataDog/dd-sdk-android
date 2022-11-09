/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.checklicenses

import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class ThirdPartyLicensesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions
            .create(EXT_NAME, ThirdPartyLicensesExtension::class.java)
        extension.csvFile = File(
            target.rootDir,
            ThirdPartyLicensesExtension.DEFAULT_TP_LICENCE_FILENAME
        )

        target.tasks
            .register(TASK_UPDATE_NAME, UpdateThirdPartyLicensesTask::class.java) {
                this.extension = extension
            }

        target.tasks
            .register(TASK_CHECK_NAME, CheckThirdPartyLicensesTask::class.java) {
                this.extension = extension
            }

        target.tasks.named("check").dependsOn(TASK_CHECK_NAME)
    }

    companion object {
        const val EXT_NAME = "thirdPartyLicences"

        const val TASK_UPDATE_NAME = "updateThirdPartyLicences"
        const val TASK_CHECK_NAME = "checkThirdPartyLicences"
    }
}
