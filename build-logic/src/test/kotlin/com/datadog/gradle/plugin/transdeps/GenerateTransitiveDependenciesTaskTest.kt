/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import com.datadog.gradle.plugin.aarmetadata.writeAarMetadata
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipException

@ExtendWith(ForgeExtension::class)
internal class GenerateTransitiveDependenciesTaskTest {

    @TempDir
    lateinit var fakeTempDir: File

    private lateinit var testedTask: GenerateTransitiveDependenciesTask
    private lateinit var fakeArtifact: File
    private lateinit var fakeReport: File
    private lateinit var fakeCoordinates: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        val project = ProjectBuilder.builder().withProjectDir(fakeTempDir).build()
        val fakeGroup = "com.example.${forge.anAlphabeticalString()}"
        val fakeName = forge.anAlphabeticalString()
        val fakeVersion = "${forge.anInt(1, 10)}.0.0"
        fakeCoordinates = "$fakeGroup:$fakeName:$fakeVersion"
        fakeArtifact = File(fakeTempDir, "$fakeGroup/$fakeName/$fakeVersion/hash/$fakeName.aar")
        fakeArtifact.parentFile.mkdirs()
        fakeReport = File(fakeTempDir, "transitiveDependencies")
        testedTask = project.tasks.register("generateReport", GenerateTransitiveDependenciesTask::class.java).get()
        testedTask.dependenciesFile.set(fakeReport)
        testedTask.compileArtifacts.from(fakeArtifact)
    }

    @Test
    fun `M report each declared minimum next to its dependency W applyTask()`(forge: Forge) {
        // Given
        val fakeSdk = forge.anInt(21, 100)
        fakeArtifact.writeAarMetadata("minCompileSdk=$fakeSdk")
        testedTask.compileArtifacts.from(createAar("lower", "minCompileSdk=9"))

        // When
        testedTask.applyTask()

        // Then
        assertThat(dependencyRows().map { it.first() }).containsExactlyInAnyOrder(
            "$fakeCoordinates (minCompileSdk=$fakeSdk)",
            "com.example:lower:1.0.0 (minCompileSdk=9)"
        )
    }

    @Test
    fun `M omit annotation W applyTask() {AAR without metadata}`() {
        // Given
        fakeArtifact.writeAarMetadata(null)

        // When
        testedTask.applyTask()

        // Then
        assertThat(dependencyRows().map { it.first() }).containsExactly(fakeCoordinates)
        assertThat(fakeReport.readText()).doesNotContain("minCompileSdk")
    }

    @Test
    fun `M omit annotations W applyTask() {missing blank and nonnumeric declarations}`() {
        // Given
        fakeArtifact.writeAarMetadata("minCompileSdk=   ")
        testedTask.compileArtifacts.from(
            createAar("missing", "minCompileSdkExtension=0"),
            createAar("nonnumeric", "minCompileSdk=invalid")
        )

        // When
        testedTask.applyTask()

        // Then
        assertThat(dependencyRows()).hasSize(3)
        assertThat(fakeReport.readText()).doesNotContain("minCompileSdk")
    }

    @Test
    fun `M omit annotation W applyTask() {non-AAR artifact}`() {
        // Given
        val fakeJar = File(fakeArtifact.parentFile, "library.jar").apply { writeText("not an AAR archive") }
        testedTask.compileArtifacts.setFrom(fakeJar)

        // When
        testedTask.applyTask()

        // Then
        assertThat(dependencyRows().map { it.first() }).containsExactly(fakeCoordinates)
        assertThat(fakeReport.readText()).doesNotContain("minCompileSdk")
    }

    @Test
    fun `M report changed requirements W applyTask() {same dependency coordinates}`(forge: Forge) {
        // Given
        val fakeSdk = forge.anInt(21, 99)
        fakeArtifact.writeAarMetadata("minCompileSdk=$fakeSdk")
        testedTask.applyTask()
        fakeArtifact.writeAarMetadata("minCompileSdk=${fakeSdk + 1}")

        // When
        testedTask.applyTask()

        // Then
        assertThat(fakeReport.readText())
            .contains("$fakeCoordinates (minCompileSdk=${fakeSdk + 1})")
            .doesNotContain("(minCompileSdk=$fakeSdk)")
    }

    @Test
    fun `M fail reading corrupt AAR W applyTask()`() {
        // Given
        fakeArtifact.writeText("not a zip archive")

        // When / Then
        assertThrows<ZipException> { testedTask.applyTask() }
    }

    @Test
    fun `M preserve raw runtime artifact size W applyTask()`() {
        // Given
        fakeArtifact.writeAarMetadata("minCompileSdk=34")
        testedTask.humanReadableSize = false
        testedTask.runtimeArtifacts.from(fakeArtifact)

        // When
        testedTask.applyTask()

        // Then
        assertThat(fakeReport.readText())
            .contains("${fakeArtifact.length()} b\n")
            .contains("Total runtime dependency artifact size: ${fakeArtifact.length()} b")
    }

    @Test
    fun `M report independently resolved compile and runtime versions W applyTask()`() {
        // Given
        val fakeCompileAar = createAar("shared", "minCompileSdk=33")
        val fakeRuntimeAar = createAar("shared", "minCompileSdk=35\n# runtime metadata", version = "2.0.0")
        testedTask.compileArtifacts.setFrom(fakeCompileAar)
        testedTask.runtimeArtifacts.from(fakeRuntimeAar)
        testedTask.humanReadableSize = false

        // When
        testedTask.applyTask()

        // Then
        assertThat(dependencyRows()).containsExactly(
            listOf("com.example:shared:1.0.0 (minCompileSdk=33)", "${fakeCompileAar.length()} b", "compile"),
            listOf("com.example:shared:2.0.0 (minCompileSdk=35)", "${fakeRuntimeAar.length()} b", "runtime")
        )
        assertThat(fakeReport.readText())
            .contains("Total compile dependency artifact size: ${fakeCompileAar.length()} b")
            .contains("Total runtime dependency artifact size: ${fakeRuntimeAar.length()} b")
    }

    @Test
    fun `M report runtime-only dependencies W applyTask() {empty compile classpath}`() {
        // Given
        testedTask.compileArtifacts.setFrom(emptyList<File>())
        fakeArtifact.writeAarMetadata("minCompileSdk=35")
        testedTask.runtimeArtifacts.from(fakeArtifact)

        // When
        testedTask.applyTask()

        // Then
        assertThat(dependencyRows()).containsExactly(
            listOf("$fakeCoordinates (minCompileSdk=35)", "${fakeArtifact.length()} b", "runtime")
        )
    }

    @Test
    fun `M total only unique runtime artifacts W applyTask()`() {
        // Given
        fakeArtifact.writeAarMetadata("minCompileSdk=35")
        val fakeRuntimeOnlyAar = createAar("runtime-only", "minCompileSdk=33")
        testedTask.compileArtifacts.from(createAar("compile-only", "minCompileSdk=31"))
        testedTask.runtimeArtifacts.from(fakeArtifact, fakeArtifact, fakeRuntimeOnlyAar)
        testedTask.humanReadableSize = false

        // When
        testedTask.applyTask()

        // Then
        val expectedSize = fakeArtifact.length() + fakeRuntimeOnlyAar.length()
        assertThat(fakeReport.readLines().filter { it.startsWith("Total runtime dependency artifact size:") })
            .containsExactly("Total runtime dependency artifact size: $expectedSize b")
        assertThat(fakeReport.readText()).doesNotContain("Total transitive dependencies size")
    }

    @Test
    fun `M put size totals after dependency table W applyTask()`() {
        // Given
        fakeArtifact.writeAarMetadata("minCompileSdk=35")

        // When
        testedTask.applyTask()

        // Then
        val report = fakeReport.readText()
        assertThat(report).startsWith("Dependencies List\n\nDependency ")
        assertThat(report).endsWith(
            "\nTotal compile dependency artifact size: ${fakeArtifact.length()} b\n" +
                "Total runtime dependency artifact size: 0 b\n"
        )
        assertThat(report).doesNotContain("Highest declared", "AARs without")
        assertThat(dependencyRows()).hasSize(1)
        assertThat(report).doesNotContain("Release compile classpath", "Release runtime classpath")
    }

    @Test
    fun `M list identical classpaths once W applyTask()`() {
        // Given
        fakeArtifact.writeAarMetadata("minCompileSdk=35")
        testedTask.runtimeArtifacts.from(fakeArtifact)
        testedTask.humanReadableSize = false

        // When
        testedTask.applyTask()

        // Then
        assertThat(dependencyRows()).containsExactly(
            listOf("$fakeCoordinates (minCompileSdk=35)", "${fakeArtifact.length()} b", "both")
        )
        assertThat(fakeReport.readText())
            .contains("Total compile dependency artifact size: ${fakeArtifact.length()} b")
            .contains("Total runtime dependency artifact size: ${fakeArtifact.length()} b")
    }

    @Test
    fun `M label overlapping classpaths and sort rows by dependency W applyTask()`() {
        // Given
        val fakeCompileOnly = createAar("aaa", "minCompileSdk=31")
        val fakeShared = createAar("bbb", "minCompileSdk=33")
        val fakeRuntimeOnly = createAar("ccc", "minCompileSdk=35")
        testedTask.compileArtifacts.setFrom(fakeShared, fakeCompileOnly)
        testedTask.runtimeArtifacts.setFrom(fakeRuntimeOnly, fakeShared)
        testedTask.humanReadableSize = false

        // When
        testedTask.applyTask()

        // Then
        assertThat(dependencyRows()).containsExactly(
            listOf("com.example:aaa:1.0.0 (minCompileSdk=31)", "${fakeCompileOnly.length()} b", "compile"),
            listOf("com.example:bbb:1.0.0 (minCompileSdk=33)", "${fakeShared.length()} b", "both"),
            listOf("com.example:ccc:1.0.0 (minCompileSdk=35)", "${fakeRuntimeOnly.length()} b", "runtime")
        )
    }

    private fun dependencyRows(): List<List<String>> {
        return fakeReport.readLines().filter { it.contains(" | ") }.drop(1).map { row ->
            row.split(" | ").map { it.trim() }
        }
    }

    private fun createAar(name: String, metadata: String?, version: String = "1.0.0"): File {
        return File(fakeTempDir, "com.example/$name/$version/hash/$name.aar").apply {
            parentFile.mkdirs()
            writeAarMetadata(metadata)
        }
    }
}
