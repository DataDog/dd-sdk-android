/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.datadog.gradle.plugin.internal.DdAppIdentifier
import com.datadog.gradle.plugin.internal.DdConfiguration
import com.datadog.gradle.plugin.internal.Uploader
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.lang.IllegalStateException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DdMappingFileUploadTaskTest {

    lateinit var testedTask: DdMappingFileUploadTask

    @TempDir
    lateinit var tempDir: File

    @Mock
    lateinit var mockUploader: Uploader

    @StringForgery
    lateinit var fakeVariant: String

    @StringForgery
    lateinit var fakeEnv: String

    @StringForgery
    lateinit var fakeVersion: String

    @StringForgery
    lateinit var fakeService: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeApiKey: String

    @Forgery
    lateinit var fakeSite: DdConfiguration.Site

    @BeforeEach
    fun `set up`() {

        val fakeProject = ProjectBuilder.builder()
            .withProjectDir(tempDir)
            .build()

        testedTask = fakeProject.tasks.create(
            "DdMappingFileUploadTask",
            DdMappingFileUploadTask::class.java
        )

        testedTask.uploader = mockUploader
        testedTask.apiKey = fakeApiKey
        testedTask.variantName = fakeVariant
        testedTask.envName = fakeEnv
        testedTask.versionName = fakeVersion
        testedTask.serviceName = fakeService
        testedTask.site = fakeSite.name
    }

    @Test
    fun `𝕄 upload file 𝕎 applyTask()`() {
        // Given
        val fakeFile = File(tempDir, "mapping.txt")
        fakeFile.writeText("")
        testedTask.mappingFilePath = fakeFile.path
        val expectedUrl = DdConfiguration(fakeSite, fakeApiKey).buildUrl()

        // When
        testedTask.applyTask()

        // Then
        verify(mockUploader).upload(
            expectedUrl,
            fakeFile,
            DdAppIdentifier(
                serviceName = fakeService,
                envName = fakeEnv,
                version = fakeVersion,
                variant = fakeVariant
            )
        )
    }

    @Test
    fun `𝕄 throw error 𝕎 applyTask() {no api key}`() {
        // Given
        val fakeFile = File(tempDir, "mapping.txt")
        fakeFile.writeText("")
        testedTask.mappingFilePath = fakeFile.path
        testedTask.apiKey = ""

        // When
        assertThrows<IllegalStateException> {
            testedTask.applyTask()
        }

        // Then
        verifyZeroInteractions(mockUploader)
    }

    @Test
    fun `𝕄 throw error 𝕎 applyTask() {invalid site}`(
        @StringForgery siteName: String
    ) {
        assumeTrue(siteName !in listOf("US", "EU", "GOV"))

        // Given
        val fakeFile = File(tempDir, "mapping.txt")
        fakeFile.writeText("")
        testedTask.mappingFilePath = fakeFile.path
        testedTask.site = siteName

        // When
        assertThrows<IllegalStateException> {
            testedTask.applyTask()
        }

        // Then
        verifyZeroInteractions(mockUploader)
    }

    @Test
    fun `𝕄 do nothing 𝕎 applyTask() {no mapping file}`() {
        // Given
        val fakeFile = File(tempDir, "mapping.txt")
        testedTask.mappingFilePath = fakeFile.path

        // When
        testedTask.applyTask()

        // Then
        verifyZeroInteractions(mockUploader)
    }

    @Test
    fun `𝕄 throw error 𝕎 applyTask() {mapping file is dir}`() {

        // Given
        val fakeFile = File(tempDir, "mapping.txt")
        fakeFile.mkdirs()
        testedTask.mappingFilePath = fakeFile.path

        // When
        assertThrows<IllegalStateException> {
            testedTask.applyTask()
        }

        // Then
        verifyZeroInteractions(mockUploader)
    }
}
