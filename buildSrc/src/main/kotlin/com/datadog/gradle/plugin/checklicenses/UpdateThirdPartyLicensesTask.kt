/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.checklicenses

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

open class UpdateThirdPartyLicensesTask : DefaultTask() {

    @get: Input
    internal var extension: ThirdPartyLicensesExtension =
        ThirdPartyLicensesExtension()
    private val provider: DependenciesLicenseProvider =
        DependenciesLicenseProvider()

    init {
        group = "datadog"
        description = "Lists Third Party Licences in a csv file"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        val dependencies = provider.getThirdPartyDependencies(
            project,
            extension.transitiveDependencies,
            extension.listDependencyOnce
        )

        extension.csvFile.printWriter().use { writer ->
            writer.println("Component,Origin,License,Copyright")
            dependencies
                .forEach {
                    writer.println(it.toString())
                }
        }
    }

    // endregion
}
