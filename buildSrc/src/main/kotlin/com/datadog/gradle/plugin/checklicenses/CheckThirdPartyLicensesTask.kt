/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.checklicenses

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

open class CheckThirdPartyLicensesTask : DefaultTask() {

    @get:Input
    internal var extension: ThirdPartyLicensesExtension =
        ThirdPartyLicensesExtension()
    private val provider: DependenciesLicenseProvider =
        DependenciesLicenseProvider()

    init {
        group = "datadog"
        description = "Check all Third Party Licences appear in the csv file"
    }

    // region Task

    @TaskAction
    fun applyTask() {

        val projectDependencies = provider.getThirdPartyDependencies(
            project,
            extension.transitiveDependencies,
            extension.listDependencyOnce
        )
        val listedDependencies = parseCsvFile()

        checkMatchingDependencies(projectDependencies, listedDependencies, "missing")

        if (extension.checkObsoleteDependencies) {
            checkMatchingDependencies(listedDependencies, projectDependencies, "obsolete")
        }

        listedDependencies.filter { it.license is License.Empty }
            .forEach {
                System.err.println("License for ${it.origin} is empty")
            }

        listedDependencies.filter { it.license is License.Raw }
            .forEach {
                System.err.println("License for ${it.origin} is not valid : ${it.license}")
            }

        listedDependencies.filter { it.copyright == "__" }
            .forEach {
                System.err.println("Copyright for ${it.origin} is missing")
            }
    }

    private fun checkMatchingDependencies(
        trueDependencies: List<ThirdPartyDependency>,
        testedDependencies: List<ThirdPartyDependency>,
        check: String
    ) {
        var error = false

        trueDependencies.forEach { dep ->
            val known = testedDependencies.firstOrNull {
                it.component == dep.component && it.origin == dep.origin
            }
            val knownInOtherComponent = testedDependencies.firstOrNull {
                it.component != dep.component && it.origin == dep.origin
            }

            if (known == null && knownInOtherComponent == null) {
                error = true
                System.err.println("✗ $check dependency in ${extension.csvFile.name} : $dep")
            } else if (knownInOtherComponent != null) {
                System.err.println("✗ $dep $check but exist in component ${knownInOtherComponent.component}")
            }
        }

        check(!error) { "Some dependencies are missing in ${extension.csvFile.name}" }
    }

    @InputFile
    fun getCsvInputFile(): File {
        return extension.csvFile
    }

    // endregion

    // region Internal

    private fun parseCsvFile(): List<ThirdPartyDependency> {
        val result = mutableListOf<ThirdPartyDependency>()
        var firstLineRead = false
        extension.csvFile.forEachLine {
            if (firstLineRead) {
                val (component, origin, license, copyright) = it.split(",")
                result.add(
                    ThirdPartyDependency(
                        component = componentMap[component]
                            ?: ThirdPartyDependency.Component.UNKNOWN,
                        origin = origin,
                        license = License.from(
                            license
                        ),
                        copyright = copyright
                    )
                )
            } else {
                firstLineRead = true
            }
        }

        return result
    }

    // endregion

    companion object {
        private val componentMap = ThirdPartyDependency.Component.values()
            .map { it.csvName to it }
            .toMap()
    }
}
