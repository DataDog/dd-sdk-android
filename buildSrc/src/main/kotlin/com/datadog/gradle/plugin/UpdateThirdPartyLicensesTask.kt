package com.datadog.gradle.plugin

import com.datadog.gradle.utils.asSequence
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.TaskAction
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.PrintWriter
import javax.xml.parsers.DocumentBuilderFactory

open class UpdateThirdPartyLicensesTask : DefaultTask() {

    var extension: ThirdPartyLicensesExtension = ThirdPartyLicensesExtension()

    init {
        group = "datadog"
        description = "Lists Third Party Licences in a csv file"
    }

    // region Task

    @TaskAction
    fun applyTask() {

        val dependencies = getDependencyIds()
        val dependencyIds = dependencies.map { it.selected.id }

        val pomFilesList = resolvePomFiles(dependencyIds)

        extension.output.printWriter().use {
            it.println("Component,Origin,License,Copyright")
            printPomDependencies(it, pomFilesList)
        }

    }

    private fun printPomDependencies(
        writer : PrintWriter,
        pomFilesList: List<String>
    ) {
        val knownGroups = mutableListOf<String>()
        pomFilesList.forEach { path ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path)
            val groupIdNode = document.getElementsByTagName(TAG_GROUP_ID).asSequence().firstOrNull()
            val groupId = groupIdNode?.textContent

            if (groupId != null && groupId !in knownGroups) {
                knownGroups.add(groupId)
                val licencesNode = document.getElementsByTagName(TAG_LICENSES).asSequence().firstOrNull()
                val licenceNodes = licencesNode?.childNodes?.asSequence()?.filter { it.nodeName == TAG_LICENSE }
                val licenses = licenceNodes?.asSequence()
                    ?.mapNotNull {
                        it.childNodes
                            .asSequence()
                            .firstOrNull { child -> child.nodeName == TAG_NAME }
                    }
                    ?.joinToString("/") { it.textContent }

                writer.println("__,$groupId,$licenses,__")
            }
        }
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

    // endregion

    // region Internal/Filters

    private fun useConfiguration(configuration: Configuration): Boolean {
        return configuration.isCanBeResolved &&
            (configuration.name.contains("implementation", true)
                || configuration.name.contains("api", true))
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
        val knownConfigurations = arrayOf("implementation", "api", "testImplementation")
    }
}
