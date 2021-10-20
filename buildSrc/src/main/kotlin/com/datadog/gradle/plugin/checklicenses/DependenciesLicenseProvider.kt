/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.checklicenses

import com.datadog.gradle.utils.asSequence
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Document

class DependenciesLicenseProvider {

    fun getThirdPartyDependencies(
        project: Project,
        transitive: Boolean,
        listDependencyOnce: Boolean
    ): List<ThirdPartyDependency> {
        val dependencies = getConfigurationDependenciesMap(project, transitive)

        val dependencyIds = dependencies.values.flatten()
        val pomFilesList = resolvePomFiles(project, dependencyIds)

        return listThirdPartyLicenses(dependencies, pomFilesList, listDependencyOnce)
    }

    // region Internal

    private fun getConfigurationDependenciesMap(
        project: Project,
        transitive: Boolean
    ): Map<String, List<ComponentIdentifier>> {
        return project.configurations.filter { it.isCanBeResolved }
            .map { configuration ->
                configuration.name to configuration.incoming.resolutionResult.allDependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .filter { transitive || it.isRoot() }
                    .map { it.selected.id }
            }
            .filter { it.second.isNotEmpty() }
            .toMap()
    }

    private fun resolvePomFiles(
        project: Project,
        dependencyIds: List<ComponentIdentifier>
    ): Map<ComponentIdentifier, String> {
        return project.dependencies
            .createArtifactResolutionQuery()
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .forComponents(dependencyIds)
            .execute()
            .resolvedComponents
            .flatMap { result ->
                result.getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>()
                    .map { result.id to it.file.absolutePath }
            }.toMap()
    }

    private fun listThirdPartyLicenses(
        dependencies: Map<String, List<ComponentIdentifier>>,
        pomFilesList: Map<ComponentIdentifier, String>,
        listDependencyOnce: Boolean
    ): List<ThirdPartyDependency> {
        val sorted = dependencies.map {
            listThridPartyLicensesInConfiguration(
                it.key,
                it.value,
                pomFilesList
            )
        }.flatten()
            .toSet()
            .sortedBy { it.origin }
            .sortedBy { it.component.ordinal }

        return if (listDependencyOnce) {
            val knownOrigins = mutableSetOf<String>()
            val result = mutableListOf<ThirdPartyDependency>()
            sorted.forEach {
                if (it.origin !in knownOrigins) {
                    result.add(it)
                    knownOrigins.add(it.origin)
                } else {
                    println("Ignoring ${it.component.csvName}/${it.origin}, already added.")
                }
            }
            result
        } else {
            sorted
        }
    }

    private fun listThridPartyLicensesInConfiguration(
        configuration: String,
        dependencies: List<ComponentIdentifier>,
        pomFilesList: Map<ComponentIdentifier, String>
    ): List<ThirdPartyDependency> {
        return dependencies.mapNotNull {
            val pomFilePath = pomFilesList[it]
            if (pomFilePath.isNullOrBlank()) {
                System.err.println("Missing pom.xml file for dependency $it")
                null
            } else {
                readLicenseFromPomFile(configuration, pomFilePath)
            }
        }
    }

    private fun readLicenseFromPomFile(
        configuration: String,
        path: String
    ): ThirdPartyDependency? {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path)
        val groupId = readGroupIdFromPomDocument(document)
        val licenseString = readLicenseFromPomDocument(document)

        return if (groupId != null) {
            return ThirdPartyDependency(
                component = configurationToComponent(configuration),
                origin = groupId,
                license = License.from(
                    licenseString
                ),
                copyright = "__"
            )
        } else {
            System.err.println("Missing groupId in $path")
            null
        }
    }

    private fun configurationToComponent(configuration: String): ThirdPartyDependency.Component {
        if (configuration in knownImportConfiguration) {
            return ThirdPartyDependency.Component.IMPORT
        } else if (configuration in knownImportTestConfiguration) {
            return ThirdPartyDependency.Component.IMPORT_TEST
        } else if (configuration in knownBuildConfiguration) {
            return ThirdPartyDependency.Component.BUILD
        } else {
            System.err.println("Unknown configuration $configuration")
            return ThirdPartyDependency.Component.UNKNOWN
        }
    }

    private fun readGroupIdFromPomDocument(document: Document): String? {
        val groupIdNode = document.getElementsByTagName(TAG_GROUP_ID)
            .asSequence().firstOrNull()
        val groupId = groupIdNode?.textContent
        return groupId
    }

    private fun readLicenseFromPomDocument(document: Document): String? {
        val licencesNode = document.getElementsByTagName(TAG_LICENSES)
            .asSequence()
            .firstOrNull()
        val licenceNodes = licencesNode?.childNodes
            ?.asSequence()
            ?.filter { it.nodeName == TAG_LICENSE }
        val licenses = licenceNodes?.asSequence()
            ?.mapNotNull {
                it.childNodes
                    .asSequence()
                    .firstOrNull { child -> child.nodeName == TAG_NAME }
            }
            ?.joinToString("/") { it.textContent }
        return licenses
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

        private val knownImportConfiguration = setOf(
            "archives",
            "debugCompileClasspath",
            "debugImplementationDependenciesMetadata",
            "debugRuntimeClasspath",
            "implementationDependenciesMetadata",
            "releaseCompileClasspath",
            "releaseImplementationDependenciesMetadata",
            "releaseRuntimeClasspath"
        )
        private val knownImportTestConfiguration = setOf(
            "androidTestImplementationDependenciesMetadata",
            "debugAndroidTestCompileClasspath",
            "debugAndroidTestImplementationDependenciesMetadata",
            "debugAndroidTestRuntimeClasspath",
            "debugUnitTestCompileClasspath",
            "debugUnitTestImplementationDependenciesMetadata",
            "debugUnitTestRuntimeClasspath",
            "jacocoAgent",
            "jacocoAnt",
            "releaseUnitTestCompileClasspath",
            "releaseUnitTestImplementationDependenciesMetadata",
            "releaseUnitTestRuntimeClasspath",
            "testImplementationDependenciesMetadata"
        )
        private val knownBuildConfiguration = setOf(
            "_internal_aapt2_binary",
            "detekt",
            "ktlint",
            "ktlintBaselineReporter",
            "kotlinCompilerClasspath",
            "kotlinCompilerPluginClasspath",
            "lintClassPath"
        )
    }
}
