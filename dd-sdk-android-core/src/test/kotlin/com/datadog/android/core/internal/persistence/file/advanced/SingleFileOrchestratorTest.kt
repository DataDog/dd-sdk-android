/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.single.SingleFileOrchestrator
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.io.File

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SingleFileOrchestratorTest {

    private lateinit var testedOrchestrator: SingleFileOrchestrator

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @TempDir
    lateinit var tempDir: File

    @StringForgery
    lateinit var fakeParentDirName: String

    @StringForgery
    lateinit var fakeFileName: String

    lateinit var fakeFile: File

    @BeforeEach
    fun `set up`() {
        fakeFile = File(File(tempDir, fakeParentDirName), fakeFileName)
        testedOrchestrator = SingleFileOrchestrator(fakeFile, mockInternalLogger)
    }

    // region getWritableFile

    @Test
    fun `M create parent dir W getWritableFile()`(@BoolForgery forceNewBatch: Boolean) {
        // When
        testedOrchestrator.getWritableFile(forceNewBatch)

        // Then
        assertThat(fakeFile.parentFile).exists()
    }

    @Test
    fun `M return file W getWritableFile()`(@BoolForgery forceNewBatch: Boolean) {
        // When
        val result = testedOrchestrator.getWritableFile(forceNewBatch)

        // Then
        assertThat(result).isSameAs(fakeFile)
    }

    // endregion

    // region getReadableFile

    @Test
    fun `M create parent dir W getReadableFile()`() {
        // When
        testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(fakeFile.parentFile).exists()
    }

    @Test
    fun `M return file W getReadableFile()`() {
        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isSameAs(fakeFile)
    }

    @Test
    fun `M return null W getReadableFile() {file is excluded}`() {
        // When
        val result = testedOrchestrator.getReadableFile(setOf(fakeFile))

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region getAllFiles

    @Test
    fun `M create parent dir W getAllFiles()`() {
        // When
        testedOrchestrator.getAllFiles()

        // Then
        assertThat(fakeFile.parentFile).exists()
    }

    @Test
    fun `M return file W getAllFiles()`() {
        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result)
            .contains(fakeFile)
            .hasSize(1)
    }

    @Test
    fun `M return file W getAllFlushableFiles()`() {
        // When
        val result = testedOrchestrator.getFlushableFiles()

        // Then
        assertThat(result)
            .contains(fakeFile)
            .hasSize(1)
    }

    // endregion

    // region getRootDir

    @Test
    fun `M return null W getRootDir()`() {
        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region getRootDirName

    @Test
    fun `M return file parent dirname W getRootDirName()`() {
        // When
        val result = testedOrchestrator.getRootDirName()

        // Then
        assertThat(result).isEqualTo(fakeParentDirName)
    }

    @Test
    fun `M return null W getRootDirName() { parent dir is null }`() {
        // Given
        val fakeInvalidFile: File = mock()
        testedOrchestrator = SingleFileOrchestrator(fakeInvalidFile, mockInternalLogger)

        // When
        val result = testedOrchestrator.getRootDirName()

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region getMetadataFile

    @Test
    fun `M return null W getMetadataFile()`() {
        // When
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isNull()
    }

    // endregion
}
