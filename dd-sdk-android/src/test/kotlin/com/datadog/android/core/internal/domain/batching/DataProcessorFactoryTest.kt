/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching

import com.datadog.android.core.internal.data.file.FileOrchestrator
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import com.datadog.android.core.internal.domain.FilePersistenceConfig
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.domain.batching.processors.DefaultDataProcessor
import com.datadog.android.core.internal.domain.batching.processors.NoOpDataProcessor
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.ExecutorService
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
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
internal class DataProcessorFactoryTest {

    lateinit var testedFactory: DataProcessorFactory<String>

    lateinit var fakeIntermediaryFolderPath: String

    lateinit var fakeTargetFolderPath: String

    @TempDir
    lateinit var rootDirectory: File

    @Mock
    lateinit var mockedExecutorService: ExecutorService

    @Mock
    lateinit var mockedSerializer: Serializer<String>

    @Mock
    lateinit var mockedIntermediaryFileOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockedTargetFileOrchestrator: FileOrchestrator

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeIntermediaryFolderPath =
            rootDirectory.absolutePath + forge.aStringMatching("[a-zA-z]+/[a-zA-z]")
        fakeTargetFolderPath =
            rootDirectory.absolutePath + forge.aStringMatching("[a-zA-z]+/[a-zA-z]")
        testedFactory = DataProcessorFactory(
            fakeIntermediaryFolderPath,
            fakeTargetFolderPath,
            FilePersistenceConfig(),
            mockedSerializer,
            mockedExecutorService
        )
    }

    @Test
    fun `M initialise correctly WHEN consent { PENDING, GRANTED }`() {
        // WHEN
        val processor = testedFactory.resolveProcessor(TrackingConsent.PENDING)

        // THEN
        assertThat(processor).isInstanceOfSatisfying(DefaultDataProcessor::class.java) {
            assertThat(it.executorService).isEqualTo(mockedExecutorService)
            assertThat(it.writer).isInstanceOf(ImmediateFileWriter::class.java)
        }
    }

    @Test
    fun `M use provide the appropriate processor W consent { PENDING }`() {
        // WHEN
        val spyFactory = spy(testedFactory)
        doReturn(mockedIntermediaryFileOrchestrator).whenever(spyFactory)
            .buildFileOrchestrator(fakeIntermediaryFolderPath)
        val processor = spyFactory.resolveProcessor(TrackingConsent.PENDING)

        // THEN
        assertThat(processor).isInstanceOfSatisfying(DefaultDataProcessor::class.java) {
            assertThat((it.writer as ImmediateFileWriter).fileOrchestrator)
                .isEqualTo(mockedIntermediaryFileOrchestrator)
        }
    }

    @Test
    fun `M use provide the appropriate processor W consent { GRANTED }`() {
        // WHEN
        val spyFactory = spy(testedFactory)
        doReturn(mockedTargetFileOrchestrator).whenever(spyFactory)
            .buildFileOrchestrator(fakeTargetFolderPath)
        val processor = spyFactory.resolveProcessor(TrackingConsent.GRANTED)

        // THEN
        assertThat(processor).isInstanceOfSatisfying(DefaultDataProcessor::class.java) {
            assertThat((it.writer as ImmediateFileWriter).fileOrchestrator)
                .isEqualTo(mockedTargetFileOrchestrator)
        }
    }

    @Test
    fun `M use provide the NoOp processor W consent { NOT_GRANTED }`() {
        // WHEN
        val processor = testedFactory.resolveProcessor(TrackingConsent.NOT_GRANTED)

        // THEN
        assertThat(processor).isInstanceOf(NoOpDataProcessor::class.java)
    }
}
