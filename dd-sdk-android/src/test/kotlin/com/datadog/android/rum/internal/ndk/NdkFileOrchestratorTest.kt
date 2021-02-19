/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class NdkFileOrchestratorTest {

    lateinit var testedOrchestrator: NdkFileOrchestrator

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `M create the file and its parent dir W getWritableFile { file does not exist }`(
        forge: Forge,
        @StringForgery fakeData: String
    ) {
        // GIVEN
        val fakeDir = File(tempDir, forge.anAlphabeticalString())
        val fakeFile = File(fakeDir, forge.anAlphabeticalString())
        testedOrchestrator = NdkFileOrchestrator(fakeFile)

        // WHEN
        val file = testedOrchestrator.getWritableFile(forge.anInt())

        // THEN
        assertThat(file).exists()
    }

    @Test
    fun `M return the file W getWritableFile { file exists }`(
        forge: Forge,
        @StringForgery fakeData: String
    ) {
        // GIVEN
        val fakeFile = File(tempDir, forge.anAlphabeticalString())
        fakeFile.createNewFile()
        testedOrchestrator = NdkFileOrchestrator(fakeFile)

        // WHEN
        val file = testedOrchestrator.getWritableFile(forge.anInt())

        // THEN
        assertThat(file).exists()
    }
}
