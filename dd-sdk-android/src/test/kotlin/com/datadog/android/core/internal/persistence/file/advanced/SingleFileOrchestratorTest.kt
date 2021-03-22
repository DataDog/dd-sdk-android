/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.core.internal.persistence.file.single.SingleFileOrchestrator
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SingleFileOrchestratorTest {

    lateinit var testedOrchestrator: SingleFileOrchestrator

    @Forgery
    lateinit var fakeFile: File

    @BeforeEach
    fun `set up`() {
        testedOrchestrator = SingleFileOrchestrator(fakeFile)
    }

    // region getWritableFile

    @Test
    fun `𝕄 return file 𝕎 getWritableFile()`(
        @IntForgery(min = 1) dataSize: Int
    ) {
        // When
        val result = testedOrchestrator.getWritableFile(dataSize)

        // Then
        assertThat(result).isSameAs(fakeFile)
    }

    // endregion

    // region getReadableFile

    @Test
    fun `𝕄 return file 𝕎 getReadableFile()`() {
        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isSameAs(fakeFile)
    }

    @Test
    fun `𝕄 return null 𝕎 getReadableFile() {file is excluded}`() {
        // When
        val result = testedOrchestrator.getReadableFile(setOf(fakeFile))

        // Then
        assertThat(result).isNull()
    }

    // endregion

    // region getAllFiles

    @Test
    fun `𝕄 return file 𝕎 getAllFiles()`() {
        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result)
            .contains(fakeFile)
            .hasSize(1)
    }

    // endregion

    // region getRootDir

    @Test
    fun `𝕄 return null 𝕎 getRootDir()`() {
        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
    }

    // endregion
}
