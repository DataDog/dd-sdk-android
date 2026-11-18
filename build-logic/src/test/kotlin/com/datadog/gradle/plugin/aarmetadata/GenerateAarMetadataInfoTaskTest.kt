/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.aarmetadata

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class GenerateAarMetadataInfoTaskTest {

    @Test
    fun `M produce stable sorted properties W applyTask()`(@TempDir fakeTempDir: File) {
        // Given
        val project = ProjectBuilder.builder().withProjectDir(fakeTempDir).build()
        val testedTask = project.tasks.register("generateMetadata", GenerateAarMetadataInfoTask::class.java).get()
        val fakeAar = File(fakeTempDir, "library.aar")
        val fakeReport = File(fakeTempDir, "aar-metadata.txt")
        fakeAar.writeAarMetadata("# timestamp\nminCompileSdk=34\naarFormatVersion=1.0\n\n")
        testedTask.aarFile.set(fakeAar)
        testedTask.aarMetadataFile.set(fakeReport)

        // When
        testedTask.applyTask()

        // Then
        assertThat(fakeReport).hasContent("aarFormatVersion=1.0\nminCompileSdk=34\n")
    }

    @Test
    fun `M fail when own AAR metadata is missing W applyTask()`(@TempDir fakeTempDir: File) {
        // Given
        val project = ProjectBuilder.builder().withProjectDir(fakeTempDir).build()
        val testedTask = project.tasks.register("generateMetadata", GenerateAarMetadataInfoTask::class.java).get()
        val fakeAar = File(fakeTempDir, "library.aar")
        fakeAar.writeAarMetadata(null)
        testedTask.aarFile.set(fakeAar)
        testedTask.aarMetadataFile.set(File(fakeTempDir, "aar-metadata.txt"))

        // When / Then
        assertThrows<IllegalStateException> { testedTask.applyTask() }
    }
}
