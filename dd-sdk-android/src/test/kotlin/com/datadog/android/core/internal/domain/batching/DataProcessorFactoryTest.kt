/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching

import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.file.ImmediateFileWriter
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.domain.batching.processors.DefaultDataProcessor
import com.datadog.android.core.internal.domain.batching.processors.NoOpDataProcessor
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.ExecutorService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
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

    @Mock
    lateinit var mockedExecutorService: ExecutorService

    @Mock
    lateinit var mockedSerializer: Serializer<String>

    @Mock
    lateinit var mockedIntermediateFileOrchestrator: Orchestrator

    @Mock
    lateinit var mockedTargetFileOrchestrator: Orchestrator

    lateinit var fakeEventsSeparator: CharSequence

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeEventsSeparator = forge.aString(size = 2)
        testedFactory = DataProcessorFactory(
            mockedIntermediateFileOrchestrator,
            mockedTargetFileOrchestrator,
            mockedSerializer,
            fakeEventsSeparator,
            mockedExecutorService

        )
    }

    @Test
    fun `M use provide the appropriate processor W consent { PENDING }`() {
        // WHEN
        val processor = testedFactory.resolveProcessor(TrackingConsent.PENDING)

        // THEN
        assertThat(processor).isInstanceOfSatisfying(DefaultDataProcessor::class.java) {
            val immediateFileWriter = it.getWriter() as ImmediateFileWriter
            assertThat(immediateFileWriter.fileOrchestrator)
                .isEqualTo(mockedIntermediateFileOrchestrator)
        }
    }

    @Test
    fun `M reset the intermediateOrchestrator W consent { PENDING }`() {
        // WHEN
        testedFactory.resolveProcessor(TrackingConsent.PENDING)

        // THEN
        verify(mockedIntermediateFileOrchestrator).reset()
        verifyNoMoreInteractions(mockedIntermediateFileOrchestrator)
    }

    @Test
    fun `M use provide the appropriate processor W consent { GRANTED }`() {
        // WHEN
        val processor = testedFactory.resolveProcessor(TrackingConsent.GRANTED)

        // THEN
        assertThat(processor).isInstanceOfSatisfying(DefaultDataProcessor::class.java) {
            val immediateFileWriter = it.getWriter() as ImmediateFileWriter
            assertThat(immediateFileWriter.fileOrchestrator)
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
