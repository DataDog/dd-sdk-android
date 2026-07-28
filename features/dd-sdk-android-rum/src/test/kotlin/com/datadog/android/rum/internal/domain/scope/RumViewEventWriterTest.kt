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
internal class RumViewEventWriterTest {

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
            config = RumViewEventWriteConfig.FullViewOnlyAtStart,
            viewEventMapper = mockViewEventMapper,
            sdkCore = rumMonitor.mockSdkCore
        )
    }

    @Test
    fun `M keep full view baseline W previous write failed`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val secondEvent = firstEvent.copy(
            view = firstEvent.view.copy(
                action = ViewEvent.Action(firstEvent.view.action.count + forge.aPositiveLong(strict = true)),
                isActive = true
            ),
            dd = firstEvent.dd.copy(documentVersion = 2L)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(secondEvent)) doReturn secondEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            writtenEvents.size != 1
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
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
    fun `M write view update W two consecutive writes succeed`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val secondEvent = firstEvent.copy(
            view = firstEvent.view.copy(
                action = ViewEvent.Action(firstEvent.view.action.count + forge.aPositiveLong(strict = true)),
                isActive = true
            ),
            dd = firstEvent.dd.copy(documentVersion = 2L)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(secondEvent)) doReturn secondEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
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
        assertThat(writtenEvents[1]).isInstanceOf(RumViewUpdateData::class.java)
    }

    @Test
    fun `M write update from committed baseline W fail then two successful writes`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val secondEvent = firstEvent.copy(
            view = firstEvent.view.copy(
                action = ViewEvent.Action(firstEvent.view.action.count + forge.aPositiveLong(strict = true)),
                isActive = true
            ),
            dd = firstEvent.dd.copy(documentVersion = 2L)
        )
        val thirdEvent = secondEvent.copy(
            view = secondEvent.view.copy(
                action = ViewEvent.Action(secondEvent.view.action.count + forge.aPositiveLong(strict = true)),
                isActive = true
            ),
            dd = secondEvent.dd.copy(documentVersion = 3L)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(secondEvent)) doReturn secondEvent
        whenever(mockViewEventMapper.map(thirdEvent)) doReturn thirdEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            writtenEvents.size != 1
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
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
        testedWriter.writeViewEvent(
            viewEvent = thirdEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java)
        assertThat(writtenEvents[1]).isInstanceOf(MappedViewEvent::class.java)
        assertThat(writtenEvents[2]).isInstanceOf(RumViewUpdateData::class.java)
    }

    @Test
    fun `M write update W second event processed after first success`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val pendingWrites = mutableListOf<(EventBatchWriter) -> Unit>()
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            pendingWrites += it.getArgument<(EventBatchWriter) -> Unit>(0)
        }
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val secondEvent = firstEvent.copy(
            view = firstEvent.view.copy(
                action = ViewEvent.Action(firstEvent.view.action.count + forge.aPositiveLong(strict = true)),
                isActive = true
            ),
            dd = firstEvent.dd.copy(documentVersion = 2L)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(secondEvent)) doReturn secondEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
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
        pendingWrites.forEach { it(mockEventBatchWriter) }

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java)
        assertThat(writtenEvents[1]).isInstanceOf(RumViewUpdateData::class.java)
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

    @Test
    fun `M write full view W documentVersion is multiple of FULL_VIEW_EVERY_N_UPDATES`(
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val checkpointEvent = firstEvent.copy(
            dd = firstEvent.dd.copy(documentVersion = RumViewEventWriterImpl.FULL_VIEW_EVERY_N_UPDATES),
            view = firstEvent.view.copy(isActive = true)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(checkpointEvent)) doReturn checkpointEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )
        testedWriter.writeViewEvent(
            viewEvent = checkpointEvent,
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
    fun `M write view update W documentVersion is not multiple of FULL_VIEW_EVERY_N_UPDATES`(
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val beforeCheckpoint = firstEvent.copy(
            dd = firstEvent.dd.copy(documentVersion = RumViewEventWriterImpl.FULL_VIEW_EVERY_N_UPDATES - 1L),
            view = firstEvent.view.copy(isActive = true)
        )
        val afterCheckpoint = firstEvent.copy(
            dd = firstEvent.dd.copy(documentVersion = RumViewEventWriterImpl.FULL_VIEW_EVERY_N_UPDATES + 1L),
            view = firstEvent.view.copy(isActive = true)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(beforeCheckpoint)) doReturn beforeCheckpoint
        whenever(mockViewEventMapper.map(afterCheckpoint)) doReturn afterCheckpoint
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )
        testedWriter.writeViewEvent(
            viewEvent = beforeCheckpoint,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )
        testedWriter.writeViewEvent(
            viewEvent = afterCheckpoint,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java)
        assertThat(writtenEvents[1]).isInstanceOf(RumViewUpdateData::class.java)
        assertThat(writtenEvents[2]).isInstanceOf(RumViewUpdateData::class.java)
    }

    @Test
    fun `M write full view W documentVersion is multiple of FULL_VIEW_EVERY_N_UPDATES then resume diffs`(
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val checkpointEvent = firstEvent.copy(
            dd = firstEvent.dd.copy(documentVersion = RumViewEventWriterImpl.FULL_VIEW_EVERY_N_UPDATES),
            view = firstEvent.view.copy(isActive = true)
        )
        val afterCheckpoint = firstEvent.copy(
            dd = firstEvent.dd.copy(documentVersion = RumViewEventWriterImpl.FULL_VIEW_EVERY_N_UPDATES + 1L),
            view = firstEvent.view.copy(isActive = true)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(checkpointEvent)) doReturn checkpointEvent
        whenever(mockViewEventMapper.map(afterCheckpoint)) doReturn afterCheckpoint
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )
        testedWriter.writeViewEvent(
            viewEvent = checkpointEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )
        testedWriter.writeViewEvent(
            viewEvent = afterCheckpoint,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java) // baseline
        assertThat(writtenEvents[1]).isInstanceOf(MappedViewEvent::class.java) // checkpoint
        assertThat(writtenEvents[2]).isInstanceOf(RumViewUpdateData::class.java) // diff resumes
    }

    @Test
    fun `M write full view W view is closing {isActive is false}`(
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val closingEvent = firstEvent.copy(
            dd = firstEvent.dd.copy(documentVersion = 2L),
            view = firstEvent.view.copy(isActive = false)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(closingEvent)) doReturn closingEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )
        testedWriter.writeViewEvent(
            viewEvent = closingEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java) // baseline
        assertThat(writtenEvents[1]).isInstanceOf(MappedViewEvent::class.java) // closing → full view
    }

    @Test
    fun `M write view update W view is active {isActive is true}`(
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        val firstEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = 1L),
            view = fakeViewEvent.view.copy(isActive = true)
        )
        val secondEvent = firstEvent.copy(
            dd = firstEvent.dd.copy(documentVersion = 2L),
            view = firstEvent.view.copy(isActive = true)
        )
        whenever(mockViewEventMapper.map(firstEvent)) doReturn firstEvent
        whenever(mockViewEventMapper.map(secondEvent)) doReturn secondEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            true
        }

        // When
        testedWriter.writeViewEvent(
            viewEvent = firstEvent,
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
        assertThat(writtenEvents[0]).isInstanceOf(MappedViewEvent::class.java) // baseline
        assertThat(writtenEvents[1]).isInstanceOf(RumViewUpdateData::class.java) // active → diff
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
