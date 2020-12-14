/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching.processors

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.event.EventMapper
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.ExecutorService
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
internal class DefaultDataProcessorTest {

    lateinit var testedDataProcessor: DataProcessor<String>

    @Mock
    lateinit var mockedExecutorService: ExecutorService

    @Mock
    lateinit var mockedWriter: Writer<String>

    @Mock
    lateinit var mockedEventMapper: EventMapper<String>

    @BeforeEach
    fun `set up`() {
        whenever(mockedEventMapper.map(any())).thenAnswer {
            it.arguments[0]
        }
        testedDataProcessor =
            DefaultDataProcessor(mockedExecutorService, mockedWriter, mockedEventMapper)
    }

    @Test
    fun `M delegate to writer W a new event is consumed`(@StringForgery fakeEvent: String) {
        // WHEN
        testedDataProcessor.consume(fakeEvent)

        // THEN
        val argumentCaptor = argumentCaptor<Runnable>()
        verify(mockedExecutorService).submit(argumentCaptor.capture())
        argumentCaptor.firstValue.run()
        verify(mockedWriter).write(fakeEvent)
    }

    @Test
    fun `M do nothing if the eventMapper returns null W a new event is consumed`(
        @StringForgery fakeEvent: String
    ) {
        // GIVEN
        whenever(mockedEventMapper.map(fakeEvent)).thenReturn(null)

        // WHEN
        testedDataProcessor.consume(fakeEvent)

        // THEN
        val argumentCaptor = argumentCaptor<Runnable>()
        verify(mockedExecutorService).submit(argumentCaptor.capture())
        argumentCaptor.firstValue.run()
        verifyZeroInteractions(mockedWriter)
    }

    @Test
    fun `M delegate to writer W a list of events is consumed`(forge: Forge) {
        // GIVEN
        val fakeEvents = forge.aList { forge.aString() }

        // WHEN
        testedDataProcessor.consume(fakeEvents)

        // THEN
        val argumentCaptor = argumentCaptor<Runnable>()
        verify(mockedExecutorService).submit(argumentCaptor.capture())
        argumentCaptor.firstValue.run()
        fakeEvents.forEach {
            verify(mockedWriter).write(it)
        }
    }

    @Test
    fun `M do nothing when event mapper returns null for an event W a list of events is consumed`(
        forge: Forge
    ) {
        // GIVEN
        val fakeEvents = forge.aList { forge.aString() }
        val fakeRandIndex = forge.anInt(0, fakeEvents.size)
        whenever(mockedEventMapper.map(fakeEvents[fakeRandIndex])).thenReturn(null)

        // WHEN
        testedDataProcessor.consume(fakeEvents)

        // THEN
        val argumentCaptor = argumentCaptor<Runnable>()
        verify(mockedExecutorService).submit(argumentCaptor.capture())
        argumentCaptor.firstValue.run()
        fakeEvents.forEachIndexed() { index, event ->
            if (index != fakeRandIndex) {
                verify(mockedWriter).write(event)
            } else {
                verify(mockedWriter, never()).write(event)
            }
        }
    }
}
