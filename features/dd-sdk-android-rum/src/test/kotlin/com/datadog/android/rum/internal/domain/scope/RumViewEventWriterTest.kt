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
        assertThat(writtenEvents[0]).isInstanceOf(ViewEvent::class.java)
        assertThat(writtenEvents[1]).isInstanceOf(ViewEvent::class.java)
    }

    @Test
    fun `M write view update W two consecutive writes succeed`(
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
        assertThat(writtenEvents[0]).isInstanceOf(ViewEvent::class.java)
        assertThat(writtenEvents[1]).isInstanceOf(RumViewUpdateData::class.java)
    }

    @Test
    fun `M write update from committed baseline W fail then two successful writes`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val secondEvent = fakeViewEvent.copy(
            view = fakeViewEvent.view.copy(
                action = ViewEvent.Action(fakeViewEvent.view.action.count + forge.aPositiveLong(strict = true))
            )
        )
        val thirdEvent = secondEvent.copy(
            view = secondEvent.view.copy(
                action = ViewEvent.Action(secondEvent.view.action.count + forge.aPositiveLong(strict = true))
            )
        )
        whenever(mockViewEventMapper.map(fakeViewEvent)) doReturn fakeViewEvent
        whenever(mockViewEventMapper.map(secondEvent)) doReturn secondEvent
        whenever(mockViewEventMapper.map(thirdEvent)) doReturn thirdEvent
        val writtenEvents = mutableListOf<Any>()
        whenever(mockDataWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doAnswer {
            writtenEvents += it.getArgument<Any>(1)
            writtenEvents.size != 1
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
        testedWriter.writeViewEvent(
            viewEvent = thirdEvent,
            datadogContext = fakeDatadogContext,
            writeScope = mockEventWriteScope,
            writer = mockDataWriter,
            eventType = fakeEventType
        )

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(ViewEvent::class.java)
        assertThat(writtenEvents[1]).isInstanceOf(ViewEvent::class.java)
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
        pendingWrites.forEach { it(mockEventBatchWriter) }

        // Then
        assertThat(writtenEvents[0]).isInstanceOf(ViewEvent::class.java)
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
        assertThat(writtenEvents[0]).isSameAs(fakeViewEvent)
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
