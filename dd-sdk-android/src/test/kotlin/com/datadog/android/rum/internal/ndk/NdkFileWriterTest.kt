/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.file.FileHandler
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
@ForgeConfiguration(Configurator::class)
internal class NdkFileWriterTest {

    lateinit var testedWriter: NdkFileWriter<String>

    @Mock
    lateinit var mockFileOrchestrator: Orchestrator

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`() {
        whenever(mockSerializer.serialize(any())).thenAnswer {
            it.getArgument(0) as String
        }
        testedWriter = NdkFileWriter(mockFileOrchestrator, mockSerializer)
    }

    @Test
    fun `M write the data to a file W write`(
        forge: Forge,
        @StringForgery fakeData: String
    ) {
        // GIVEN
        val fakeFile = File(tempDir, forge.anAlphabeticalString())
        whenever(mockFileOrchestrator.getWritableFile(any())).thenReturn(fakeFile)

        // WHEN
        testedWriter.write(fakeData)

        // THEN
        assertThat(fakeFile.readText()).isEqualTo(fakeData)
    }

    @Test
    fun `M do nothing W write { file orchestrator returns null }`(
        @StringForgery fakeData: String
    ) {
        // GIVEN
        val mockFileHandler: FileHandler = mock()
        testedWriter = NdkFileWriter(mockFileOrchestrator, mockSerializer, mockFileHandler)

        // WHEN
        testedWriter.write(fakeData)

        // THEN
        verifyZeroInteractions(mockFileHandler)
    }
}
