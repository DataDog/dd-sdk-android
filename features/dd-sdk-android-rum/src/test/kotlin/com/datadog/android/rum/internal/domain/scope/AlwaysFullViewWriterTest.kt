/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.rum.configuration.RumViewEventWriteConfig
import com.datadog.android.rum.event.ViewEventMapper
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AlwaysFullViewWriterTest {

    lateinit var testedWriter: RumViewEventWriter

    @Mock
    lateinit var mockViewEventMapper: ViewEventMapper

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockDataWriter: DataWriter<Any>

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeEventType: EventType

    @BeforeEach
    fun `set up`() {
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        testedWriter = RumViewEventWriterImpl(
            config = RumViewEventWriteConfig.AlwaysFullView,
            viewEventMapper = mockViewEventMapper,
            sdkCore = rumMonitor.mockSdkCore
        )
    }

    @Test
    fun `M write full view event W single write`(
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        whenever(mockViewEventMapper.map(fakeViewEvent)) doReturn fakeViewEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = fakeViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents).hasSize(1)
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java)
    }

    @Test
    fun `M always write full view W two consecutive writes succeed`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val secondEvent = fakeViewEvent.copy(
            view = fakeViewEvent.view.copy(
                action = ViewEvent.Action(fakeViewEvent.view.action.count + forge.aPositiveLong(strict = true))
            )
        )
        whenever(mockViewEventMapper.map(fakeViewEvent)) doReturn fakeViewEvent
        whenever(mockViewEventMapper.map(secondEvent)) doReturn secondEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = fakeViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )
        testedWriter.writeViewEvent(
            viewEvent = secondEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java)
        assertThat(writtenEvents[1]).isInstanceOf(MappedViewEvent::class.java)
    }

    @Test
    fun `M always write full view W previous write failed`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val secondEvent = fakeViewEvent.copy(
            view = fakeViewEvent.view.copy(
                action = ViewEvent.Action(fakeViewEvent.view.action.count + forge.aPositiveLong(strict = true))
            )
        )
        whenever(mockViewEventMapper.map(fakeViewEvent)) doReturn fakeViewEvent
        whenever(mockViewEventMapper.map(secondEvent)) doReturn secondEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            if (writtenEvents.size == 1) {
                false
            } else {
                true
            }
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = fakeViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )
        testedWriter.writeViewEvent(
            viewEvent = secondEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java)
        assertThat(writtenEvents[1]).isInstanceOf(MappedViewEvent::class.java)
    }

    @Test
    fun `M always write full view W many consecutive writes`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeCount = forge.anInt(min = 3, max = 10)
        val events = (0 until fakeCount).scan(fakeViewEvent) { prev, _ ->
            prev.copy(
                view = prev.view.copy(
                    action = ViewEvent.Action(prev.view.action.count + forge.aPositiveLong(strict = true))
                )
            )
        }
        events.forEach { event ->
            whenever(mockViewEventMapper.map(event)) doReturn event
        }
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        events.forEach { event ->
            testedWriter.writeViewEvent(
                viewEvent = event,
                datadogContext = fakeDatadogContext,
                writeScope = mockEventWriteScope,
                writer = mockDataWriter,
                eventType = fakeEventType
            )
        }

        // Then
        assertThat(writtenEvents).hasSize(events.size)
        writtenEvents.forEach { written ->
            assertThat(written).isInstanceOf(MappedViewEvent::class.java)
        }
    }

    @Test
    fun `M fallback to original view event W mapper throws exception`(
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        whenever(mockViewEventMapper.map(fakeViewEvent)) doThrow IllegalStateException("mapper failure")
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = fakeViewEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents).hasSize(1)
        assertThat(writtenEvents[0]).isEqualTo(MappedViewEvent(fakeViewEvent))
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
