/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class TransitiveDependenciesPluginTest {

    @TempDir
    lateinit var fakeTempDir: File

    private lateinit var fakeProject: Project
    private lateinit var fakeCore: Project
    private lateinit var fakeRepository: File

    @BeforeEach
    fun `set up`() {
        fakeProject = ProjectBuilder.builder().withProjectDir(fakeTempDir).build()
        fakeCore = ProjectBuilder.builder().withName("core").withParent(fakeProject).build()
        fakeRepository = File(fakeTempDir, "repository")
        listOf(fakeProject, fakeCore).forEach { project ->
            project.plugins.apply("java-library")
            project.repositories.maven { url = fakeRepository.toURI() }
        }
        fakeCore.configurations.create("sdkDependencies") {
            isCanBeConsumed = true
            isCanBeResolved = false
        }
        val fakeCoreJar = File(fakeCore.projectDir, "core.jar").apply {
            parentFile.mkdirs()
            writeText("SDK project artifact")
        }
        fakeCore.artifacts.add("sdkDependencies", fakeCoreJar)
    }

    @Test
    fun `M exclude project dependency trees W externalDependencyArtifacts()`() {
        // Given
        publishModule("hidden")
        publishModule("child")
        publishModule("own", dependencies = dependency("child"))
        fakeCore.dependencies.add("sdkDependencies", "com.example:hidden:1.0")
        val fakeConfiguration = configuration("releaseRuntimeClasspath", Usage.JAVA_RUNTIME)
        fakeProject.dependencies.add(fakeConfiguration.name, "com.example:own:1.0")

        // When
        val artifacts = fakeConfiguration.externalDependencyArtifacts().files

        // Then
        assertThat(artifacts.map { it.name }).containsExactlyInAnyOrder("own-1.0.jar", "child-1.0.jar")
    }

    @Test
    fun `M retain shared dependency at resolved version W externalDependencyArtifacts()`() {
        // Given
        publishModule("hidden")
        publishModule("child")
        publishModule("shared")
        publishModule("shared", version = "2.0", dependencies = dependency("child"))
        fakeCore.dependencies.add("sdkDependencies", "com.example:hidden:1.0")
        fakeCore.dependencies.add("sdkDependencies", "com.example:shared:2.0")
        val fakeConfiguration = configuration("releaseRuntimeClasspath", Usage.JAVA_RUNTIME)
        fakeProject.dependencies.add(fakeConfiguration.name, "com.example:shared:1.0")

        // When
        val artifacts = fakeConfiguration.externalDependencyArtifacts().files

        // Then
        assertThat(artifacts.map { it.name }).containsExactlyInAnyOrder("shared-2.0.jar", "child-1.0.jar")
    }

    @Test
    fun `M retain external runtime transitives W externalDependencyArtifacts()`() {
        // Given
        publishModule("hidden")
        publishModule("compile-child")
        publishModule("runtime-child")
        publishModule(
            "own",
            dependencies = dependency("compile-child") + dependency("runtime-child", scope = "runtime")
        )
        fakeCore.dependencies.add("sdkDependencies", "com.example:hidden:1.0")
        val fakeCompile = configuration("releaseCompileClasspath", Usage.JAVA_API)
        val fakeRuntime = configuration("releaseRuntimeClasspath", Usage.JAVA_RUNTIME)
        fakeProject.dependencies.add(fakeCompile.name, "com.example:own:1.0")
        fakeProject.dependencies.add(fakeRuntime.name, "com.example:own:1.0")

        // When
        val compileArtifacts = fakeCompile.externalDependencyArtifacts().files
        val runtimeArtifacts = fakeRuntime.externalDependencyArtifacts().files

        // Then
        assertThat(compileArtifacts.map { it.name }).containsExactlyInAnyOrder("own-1.0.jar", "compile-child-1.0.jar")
        assertThat(runtimeArtifacts.map { it.name })
            .containsExactlyInAnyOrder("own-1.0.jar", "compile-child-1.0.jar", "runtime-child-1.0.jar")
    }

    @Test
    fun `M exclude project-only dependencies referenced by constraints W externalDependencyArtifacts()`() {
        // Given
        publishModule("hidden")
        publishModule("own")
        fakeCore.dependencies.add("sdkDependencies", "com.example:hidden:1.0")
        val fakeConfiguration = configuration("releaseRuntimeClasspath", Usage.JAVA_RUNTIME)
        fakeProject.dependencies.add(fakeConfiguration.name, "com.example:own:1.0")
        fakeProject.dependencies.constraints.add(fakeConfiguration.name, "com.example:hidden:1.0")

        // When
        val artifacts = fakeConfiguration.externalDependencyArtifacts().files

        // Then
        assertThat(artifacts.map { it.name }).containsExactly("own-1.0.jar")
    }

    private fun configuration(name: String, usage: String): Configuration {
        val configuration = fakeProject.configurations.create(name) {
            isCanBeConsumed = false
            attributes.attribute(Usage.USAGE_ATTRIBUTE, fakeProject.objects.named(Usage::class.java, usage))
        }
        fakeProject.dependencies.add(
            name,
            fakeProject.dependencies.project(mapOf("path" to ":core", "configuration" to "sdkDependencies"))
        )
        return configuration
    }

    private fun publishModule(name: String, version: String = "1.0", dependencies: String = "") {
        val directory = File(fakeRepository, "com/example/$name/$version").apply { mkdirs() }
        File(directory, "$name-$version.jar").writeText("external artifact")
        File(directory, "$name-$version.pom").writeText(
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>$name</artifactId>
                <version>$version</version>
                <dependencies>$dependencies</dependencies>
            </project>
            """.trimIndent()
        )
    }

    private fun dependency(name: String, scope: String = "compile"): String {
        return """
            <dependency>
                <groupId>com.example</groupId>
                <artifactId>$name</artifactId>
                <version>1.0</version>
                <scope>$scope</scope>
            </dependency>
        """.trimIndent()
    }
}
