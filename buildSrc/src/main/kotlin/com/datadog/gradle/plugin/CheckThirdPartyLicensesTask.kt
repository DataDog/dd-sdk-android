/*
 * Unless explicitly stated otherwise all pomFilesList in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.datadog.gradle.utils.asSequence
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

open class CheckThirdPartyLicensesTask : DefaultTask() {

    var extension: ThirdPartyLicensesExtension = ThirdPartyLicensesExtension()

    init {
        group = "datadog"
        description = "Check all Third Party Licences appear in the csv file"
    }

    // region Task

    @TaskAction
    fun applyTask() {

        val dependencies = getDependencyIds()
        val dependencyIds = dependencies.map { it.selected.id }

        val pomFilesList = resolvePomFiles(dependencyIds)
        val dependenciesMap = resolveDependencies(pomFilesList)

        val knownDependencies = parseCsvFile()

        var error = false

        // Report missing dependencies
        dependenciesMap.forEach { (dep, v) ->
            if (!knownDependencies.containsKey(dep)) {
                error = true
                System.err.println("✗ Missing dependency in ${extension.csvFile.name} : $dep $v")
            }
        }

        // Report obsolete dependencies
        knownDependencies.forEach { (dep, v) ->
            if (!dependenciesMap.containsKey(dep)) {
                if (extension.checkObsoleteDependencies) {
                    error = true
                    System.err.println("✗ Obsolete dependency in ${extension.csvFile.name} : $dep [$v]")
                } else {
                    println("• Obsolete dependency in ${extension.csvFile.name} : $dep [$v]")
                }
            }
        }

        check(!error) { "Some dependencies are missing or obsolete in ${extension.csvFile.name}" }
    }

    @InputFile
    fun getCsvInputFile(): File {
        return extension.csvFile
    }

    // endregion

    // region Internal/Process

    private fun getDependencyIds(): List<ResolvedDependencyResult> {
        return project.configurations.filter { useConfiguration(it) }
            .flatMap { getConfigurationDependencies(it) }
    }

    private fun getConfigurationDependencies(configuration: Configuration): List<ResolvedDependencyResult> {
        return configuration.incoming.resolutionResult.allDependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .filter { useDependency(it) }
    }

    private fun resolvePomFiles(dependencyIds: List<ComponentIdentifier>): List<String> {
        return project.dependencies
            .createArtifactResolutionQuery()
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .forComponents(dependencyIds)
            .execute()
            .resolvedComponents
            .flatMap {
                it.getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>()
                    .map { it.file.absolutePath }
            }
            .sorted()
    }

    private fun resolveDependencies(pomFilesList: List<String>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        pomFilesList.forEach { path ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path)
            val groupIdNode = document.getElementsByTagName(TAG_GROUP_ID).asSequence().firstOrNull()
            val groupId = groupIdNode?.textContent.orEmpty()

            val licencesNode = document.getElementsByTagName(TAG_LICENSES).asSequence().firstOrNull()
            val licenceNodes = licencesNode?.childNodes?.asSequence()?.filter { it.nodeName == TAG_LICENSE }
            val licenses = licenceNodes?.asSequence()
                ?.mapNotNull {
                    it.childNodes
                        .asSequence()
                        .firstOrNull { child -> child.nodeName == TAG_NAME }
                        ?.textContent
                }
                ?.toList().orEmpty()

            if (result.containsKey(groupId)) {
                result[groupId]?.addAll(licenses)
            } else {
                result[groupId] = licenses.toMutableSet()
            }
        }

        return result
    }

    private fun parseCsvFile(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        extension.csvFile.forEachLine {
            val (_, origin, license) = it.split(",")

            if (origin != "Origin") {
                result[origin] = license
            }
        }

        return result
    }

    // endregion

    // region Internal/Filters

    private fun useConfiguration(configuration: Configuration): Boolean {
        return configuration.isCanBeResolved &&
            (configuration.name.contains("implementation", true) ||
                configuration.name.contains("api", true))
    }

    private fun useDependency(dependency: ResolvedDependencyResult): Boolean {
        return extension.transitiveDependencies || dependency.isRoot()
    }

    @Suppress("UnstableApiUsage")
    private fun ResolvedDependencyResult.isRoot(): Boolean {
        return from.selectionReason.descriptions.any {
            it.cause == ComponentSelectionCause.ROOT
        }
    }

    // endregion

    companion object {
        private const val TAG_GROUP_ID = "groupId"
        private const val TAG_LICENSES = "licenses"
        private const val TAG_LICENSE = "license"
        private const val TAG_NAME = "name"
        private const val TAG_URL = "url"
    }
}
