/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.storage.TelemetryAwareEventBatchWriter
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.datadog.android.rum.internal.domain.event.RumEventMeta
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.domain.scope.DiffThenFullView
import com.datadog.android.rum.internal.domain.scope.MappedViewEvent
import com.datadog.android.rum.internal.domain.scope.RumViewUpdateData
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumDataWriterTest {

    private lateinit var testedWriter: RumDataWriter

    @Mock
    lateinit var mockEventMapper: RumEventMapper

    @Mock
    lateinit var mockEventSerializer: RumEventSerializer

    @Mock
    lateinit var mockEventMetaSerializer: Serializer<RumEventMeta>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockEventBatchWriter: TelemetryAwareEventBatchWriter

    @StringForgery
    lateinit var fakeSerializedEvent: String

    lateinit var fakeSerializedData: ByteArray

    @Forgery
    lateinit var fakeEventType: EventType

    @BeforeEach
    fun `set up`() {
        fakeSerializedData = fakeSerializedEvent.toByteArray(Charsets.UTF_8)

        whenever(
            mockEventBatchWriter.write(
                any<RawBatchEvent>(),
                anyOrNull<ByteArray>(),
                any<EventType>(),
                any<TelemetryContext>()
            )
        ) doReturn true
        whenever(
            mockEventBatchWriter.write(
                any<RawBatchEvent>(),
                anyOrNull<ByteArray>(),
                any<EventType>()
            )
        ) doReturn true
        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockEventMapper.map(any<ViewEvent>())) doAnswer { it.getArgument<ViewEvent>(0) }

        testedWriter = RumDataWriter(
            mockEventMapper,
            mockEventSerializer,
            mockEventMetaSerializer,
            rumMonitor.mockSdkCore
        )
    }

    @Test
    fun `M write data W write()`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.anElementFrom(
            forge.getForgery(ViewEvent::class.java),
            forge.getForgery(ActionEvent::class.java),
            forge.getForgery(ResourceEvent::class.java),
            forge.getForgery(LongTaskEvent::class.java),
            forge.getForgery(ErrorEvent::class.java)
        )

        whenever(mockEventMapper.map(fakeEvent)) doReturn fakeEvent
        whenever(mockEventSerializer.serialize(fakeEvent)) doReturn fakeSerializedEvent

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeEvent, fakeEventType)

        // Then
        assertThat(result).isTrue

        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            captor.capture(),
            anyOrNull<ByteArray>(),
            any<EventType>(),
            any<TelemetryContext>()
        )
        assertThat(captor.firstValue.data).isEqualTo(fakeSerializedData)
    }

    @Test
    fun `M resolve event type W write() { telemetry models }`(forge: Forge) {
        // Given
        val fakeConfigurationEvent = forge.getForgery(TelemetryConfigurationEvent::class.java)
        val fakeDebugEvent = forge.getForgery(TelemetryDebugEvent::class.java)
        val fakeErrorEvent = forge.getForgery(TelemetryErrorEvent::class.java)
        val fakeUsageEvent = forge.getForgery(TelemetryUsageEvent::class.java)
        val fakeEvents = listOf<Any>(
            fakeConfigurationEvent,
            fakeDebugEvent,
            fakeErrorEvent,
            fakeUsageEvent
        )
        val expectedEventTypes = listOf(
            fakeConfigurationEvent.type,
            fakeDebugEvent.type,
            fakeErrorEvent.type,
            fakeUsageEvent.type
        )
        fakeEvents.forEach {
            whenever(mockEventMapper.map(it)) doReturn it
            whenever(mockEventSerializer.serialize(it)) doReturn fakeSerializedEvent
        }

        // When
        fakeEvents.forEach {
            testedWriter.write(mockEventBatchWriter, it, fakeEventType)
        }

        // Then
        val captor = argumentCaptor<TelemetryContext>()
        verify(mockEventBatchWriter, times(fakeEvents.size)).write(
            any(),
            anyOrNull<ByteArray>(),
            any<EventType>(),
            captor.capture()
        )
        assertThat(captor.allValues.map { it.eventType })
            .containsExactlyElementsOf(expectedEventTypes)
    }

    @Test
    fun `M resolve unknown event type W write() { event class has no type property }`(
        @StringForgery fakeValue: String
    ) {
        // Given
        val fakeEvent = FakeEventWithoutType(fakeValue)
        whenever(mockEventMapper.map(fakeEvent)) doReturn fakeEvent
        whenever(mockEventSerializer.serialize(fakeEvent)) doReturn fakeSerializedEvent

        // When
        testedWriter.write(mockEventBatchWriter, fakeEvent, fakeEventType)

        // Then
        val captor = argumentCaptor<TelemetryContext>()
        verify(mockEventBatchWriter).write(
            any(),
            anyOrNull<ByteArray>(),
            any<EventType>(),
            captor.capture()
        )
        assertThat(captor.firstValue.eventType).isEqualTo("unknown")
    }

    @Test
    fun `M write data with event meta W write() {View Event}`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        whenever(mockEventMapper.map(fakeViewEvent)) doReturn fakeViewEvent
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeSerializedEvent
        val eventMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        val fakeSerializedViewEventMeta = forge.aString()
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedViewEventMeta

        // When
        testedWriter.write(mockEventBatchWriter, fakeViewEvent, fakeEventType)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            captor.capture(),
            anyOrNull<ByteArray>(),
            any<EventType>(),
            any<TelemetryContext>()
        )
        assertThat(captor.firstValue.data).isEqualTo(fakeSerializedData)
        assertThat(captor.firstValue.metadata).isEqualTo(fakeSerializedViewEventMeta.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `M map raw view event W write() { ViewEvent }`(
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeMappedSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val mappedViewEvent = fakeViewEvent.copy(
            dd = fakeViewEvent.dd.copy(documentVersion = fakeViewEvent.dd.documentVersion + 1)
        )
        val fakeMappedSerializedData = fakeMappedSerializedEvent.toByteArray(Charsets.UTF_8)
        whenever(mockEventMapper.map(fakeViewEvent)) doReturn mappedViewEvent
        whenever(mockEventSerializer.serialize(mappedViewEvent)) doReturn fakeMappedSerializedEvent
        val eventMeta = RumEventMeta.View(
            viewId = mappedViewEvent.view.id,
            documentVersion = mappedViewEvent.dd.documentVersion,
            hasAccessibility = mappedViewEvent.view.accessibility != null
        )
        val fakeSerializedViewEventMeta = forge.aString()
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedViewEventMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeMappedSerializedData,
                        metadata = fakeSerializedViewEventMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeViewEvent, fakeEventType)

        // Then
        assertThat(result).isTrue
        verify(mockEventMapper).map(fakeViewEvent)
    }

    @Test
    fun `M bypass mapper W write() { MappedViewEvent }`(
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeMappedSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeMappedSerializedData = fakeMappedSerializedEvent.toByteArray(Charsets.UTF_8)
        val mappedViewEvent = MappedViewEvent(fakeViewEvent)
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeMappedSerializedEvent
        val eventMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        val fakeSerializedViewEventMeta = forge.aString()
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedViewEventMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeMappedSerializedData,
                        metadata = fakeSerializedViewEventMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        val result = testedWriter.write(mockEventBatchWriter, mappedViewEvent, fakeEventType)

        // Then
        assertThat(result).isTrue
        verifyNoInteractions(mockEventMapper)
    }

    @Test
    fun `M write data with empty event meta W write() {View Event, meta serialization fails}`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        whenever(mockEventMapper.map(fakeViewEvent)) doReturn fakeViewEvent
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeSerializedEvent
        val hasAccessibility = fakeViewEvent.view.accessibility != null
        val eventMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = hasAccessibility
        )
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doThrow forge.aThrowable()

        // When
        testedWriter.write(mockEventBatchWriter, fakeViewEvent, fakeEventType)

        // Then
        val captor = argumentCaptor<RawBatchEvent>()
        verify(mockEventBatchWriter).write(
            captor.capture(),
            anyOrNull<ByteArray>(),
            any<EventType>(),
            any<TelemetryContext>()
        )
        assertThat(captor.firstValue.data).isEqualTo(fakeSerializedData)
        assertThat(captor.firstValue.metadata).isEmpty()
    }

    @Test
    fun `M not write data W write() { exception during serialization }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.anElementFrom(
            forge.getForgery(ViewEvent::class.java),
            forge.getForgery(ActionEvent::class.java),
            forge.getForgery(ResourceEvent::class.java),
            forge.getForgery(LongTaskEvent::class.java),
            forge.getForgery(ErrorEvent::class.java)
        )

        whenever(mockEventMapper.map(fakeEvent)) doReturn fakeEvent
        whenever(mockEventSerializer.serialize(fakeEvent)) doThrow RuntimeException("serialization error")

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeEvent, fakeEventType)

        // Then
        assertThat(result).isFalse

        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M return false W write() { data was not written }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.anElementFrom(
            forge.getForgery(ViewEvent::class.java),
            forge.getForgery(ActionEvent::class.java),
            forge.getForgery(ResourceEvent::class.java),
            forge.getForgery(LongTaskEvent::class.java),
            forge.getForgery(ErrorEvent::class.java)
        )

        whenever(mockEventMapper.map(fakeEvent)) doReturn fakeEvent
        whenever(mockEventSerializer.serialize(fakeEvent)) doReturn fakeSerializedEvent
        whenever(
            mockEventBatchWriter.write(
                any<RawBatchEvent>(),
                anyOrNull<ByteArray>(),
                any<EventType>(),
                any<TelemetryContext>()
            )
        ) doReturn false

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeEvent, fakeEventType)

        // Then
        assertThat(result).isFalse
    }

    // region writeLastViewEvent

    @Test
    fun `M call writeLastViewEvent W write() { ViewEvent, write succeeds }`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeSerializedEvent
        val fakeSerializedMeta = forge.aString()
        val eventMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeSerializedData,
                        metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        testedWriter.write(mockEventBatchWriter, fakeViewEvent, fakeEventType)

        // Then
        verify(rumMonitor.mockSdkCore).writeLastViewEvent(fakeSerializedData)
    }

    @Test
    fun `M NOT call writeLastViewEvent W write() { ViewEvent, write fails }`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeSerializedEvent
        val fakeSerializedMeta = forge.aString()
        val eventMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeSerializedData,
                        metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn false

        // When
        testedWriter.write(mockEventBatchWriter, fakeViewEvent, fakeEventType)

        // Then
        verify(rumMonitor.mockSdkCore, never()).writeLastViewEvent(any<ByteArray>())
    }

    @Test
    fun `M call writeLastViewEvent with full ViewEvent W write() { RumViewUpdateData, write succeeds }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        @StringForgery fakeFullSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeViewUpdateData = RumViewUpdateData(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffSerializedData = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeFullSerializedData = fakeFullSerializedEvent.toByteArray(Charsets.UTF_8)
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeFullSerializedEvent
        val fakeSerializedMeta = forge.aString()
        val eventMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeDiffSerializedData,
                        metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        testedWriter.write(mockEventBatchWriter, fakeViewUpdateData, fakeEventType)

        // Then
        verify(rumMonitor.mockSdkCore).writeLastViewEvent(fakeFullSerializedData)
    }

    @Test
    fun `M NOT call writeLastViewEvent W write() { RumViewUpdateData, write fails }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeViewUpdateData = RumViewUpdateData(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffSerializedData = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        val fakeSerializedMeta = forge.aString()
        val eventMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeDiffSerializedData,
                        metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn false

        // When
        testedWriter.write(mockEventBatchWriter, fakeViewUpdateData, fakeEventType)

        // Then
        verify(rumMonitor.mockSdkCore, never()).writeLastViewEvent(any<ByteArray>())
    }

    // endregion

    // region writeViewEvent edge cases

    @Test
    fun `M return false W write() { ViewEvent, serialization fails }`(
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doThrow RuntimeException("serialization error")

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeViewEvent, fakeEventType)

        // Then
        assertThat(result).isFalse
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M return false W write() { ViewEvent, batch write returns false }`(
        @Forgery fakeViewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeSerializedEvent
        val fakeSerializedMeta = forge.aString()
        val eventMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeSerializedData,
                        metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn false

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeViewEvent, fakeEventType)

        // Then
        assertThat(result).isFalse
    }

    // endregion

    // region writeViewUpdateEvent edge cases

    @Test
    fun `M return false W write() { RumViewUpdateData, serialization fails }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        val fakeViewUpdateData = RumViewUpdateData(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doThrow RuntimeException("serialization error")

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeViewUpdateData, fakeEventType)

        // Then
        assertThat(result).isFalse
        verifyNoInteractions(mockEventBatchWriter)
    }

    @Test
    fun `M write data with empty event meta W write() { RumViewUpdateData, meta serialization fails }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeViewUpdateData = RumViewUpdateData(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffSerializedData = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        val eventMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doThrow forge.aThrowable()
        whenever(
            mockEventBatchWriter.write(
                eq(RawBatchEvent(data = fakeDiffSerializedData)),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        testedWriter.write(mockEventBatchWriter, fakeViewUpdateData, fakeEventType)

        // Then
        verify(mockEventBatchWriter).write(
            eq(RawBatchEvent(data = fakeDiffSerializedData)),
            anyOrNull<ByteArray>(),
            eq(fakeEventType),
            any<TelemetryContext>()
        )
    }

    @Test
    fun `M NOT call writeLastViewEvent W write() { RumViewUpdateData, full ViewEvent serialization fails }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeViewUpdateData = RumViewUpdateData(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffSerializedData = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doThrow RuntimeException("serialization error")
        val fakeSerializedMeta = forge.aString()
        val eventMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        whenever(mockEventMetaSerializer.serialize(eventMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeDiffSerializedData,
                        metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        testedWriter.write(mockEventBatchWriter, fakeViewUpdateData, fakeEventType)

        // Then
        verify(rumMonitor.mockSdkCore, never()).writeLastViewEvent(any<ByteArray>())
    }

    // endregion

    // region writeDiffThenFullView

    @Test
    fun `M write diff then full view W write() { DiffThenFullView, diff succeeds }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        @StringForgery fakeFullSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeData = DiffThenFullView(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffBytes = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeFullBytes = fakeFullSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeSerializedMeta = forge.aString()
        val fakeFullMeta = forge.aString()
        val diffMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        val fullMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeFullSerializedEvent
        whenever(mockEventMetaSerializer.serialize(diffMeta)) doReturn fakeSerializedMeta
        whenever(mockEventMetaSerializer.serialize(fullMeta)) doReturn fakeFullMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeDiffBytes,
                        metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeFullBytes,
                        metadata = fakeFullMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeData, fakeEventType)

        // Then
        assertThat(result).isTrue()
        val captor = argumentCaptor<RawBatchEvent>()
        verify(
            mockEventBatchWriter,
            times(2)
        ).write(captor.capture(), isNull(), eq(fakeEventType), any<TelemetryContext>())
        // diff is written first, then the full view
        assertThat(captor.firstValue.data).isEqualTo(fakeDiffBytes)
        assertThat(captor.secondValue.data).isEqualTo(fakeFullBytes)
    }

    @Test
    fun `M NOT call writeLastViewEvent for diff W write() { DiffThenFullView, diff succeeds }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        @StringForgery fakeFullSerializedEvent: String,
        forge: Forge
    ) {
        // Given — the diff write succeeds but crash-recovery must NOT fire for the diff step
        val fakeData = DiffThenFullView(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffBytes = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeFullBytes = fakeFullSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeSerializedMeta = forge.aString()
        val fakeFullMeta = forge.aString()
        val diffMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        val fullMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeFullSerializedEvent
        whenever(mockEventMetaSerializer.serialize(diffMeta)) doReturn fakeSerializedMeta
        whenever(mockEventMetaSerializer.serialize(fullMeta)) doReturn fakeFullMeta
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeDiffBytes,
                        metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true
        whenever(
            mockEventBatchWriter.write(
                eq(
                    RawBatchEvent(
                        data = fakeFullBytes,
                        metadata = fakeFullMeta.toByteArray(Charsets.UTF_8)
                    )
                ),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        testedWriter.write(mockEventBatchWriter, fakeData, fakeEventType)

        // Then — writeLastViewEvent is called exactly once: for the full view via writeMappedViewEvent
        verify(rumMonitor.mockSdkCore).writeLastViewEvent(fakeFullBytes)
    }

    @Test
    fun `M call writeLastViewEvent with full ViewEvent W write() { DiffThenFullView, diff and full succeed }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        @StringForgery fakeFullSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeData = DiffThenFullView(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffBytes = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeFullBytes = fakeFullSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeSerializedMeta = forge.aString()
        val fakeFullMeta = forge.aString()
        val diffMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        val fullMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeFullSerializedEvent
        whenever(mockEventMetaSerializer.serialize(diffMeta)) doReturn fakeSerializedMeta
        whenever(mockEventMetaSerializer.serialize(fullMeta)) doReturn fakeFullMeta
        whenever(
            mockEventBatchWriter.write(
                eq(RawBatchEvent(data = fakeDiffBytes, metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8))),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true
        whenever(
            mockEventBatchWriter.write(
                eq(RawBatchEvent(data = fakeFullBytes, metadata = fakeFullMeta.toByteArray(Charsets.UTF_8))),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true

        // When
        testedWriter.write(mockEventBatchWriter, fakeData, fakeEventType)

        // Then
        verify(rumMonitor.mockSdkCore).writeLastViewEvent(fakeFullBytes)
    }

    @Test
    fun `M return true W write() { DiffThenFullView, diff succeeds, full view write fails }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        @StringForgery fakeFullSerializedEvent: String,
        forge: Forge
    ) {
        // Given — diff persisted, full view batch write fails; return value must still be true
        val fakeData = DiffThenFullView(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffBytes = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeFullBytes = fakeFullSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeSerializedMeta = forge.aString()
        val fakeFullMeta = forge.aString()
        val diffMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        val fullMeta = RumEventMeta.View(
            viewId = fakeViewEvent.view.id,
            documentVersion = fakeViewEvent.dd.documentVersion,
            hasAccessibility = fakeViewEvent.view.accessibility != null
        )
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventSerializer.serialize(fakeViewEvent)) doReturn fakeFullSerializedEvent
        whenever(mockEventMetaSerializer.serialize(diffMeta)) doReturn fakeSerializedMeta
        whenever(mockEventMetaSerializer.serialize(fullMeta)) doReturn fakeFullMeta
        whenever(
            mockEventBatchWriter.write(
                eq(RawBatchEvent(data = fakeDiffBytes, metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8))),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn true
        whenever(
            mockEventBatchWriter.write(
                eq(RawBatchEvent(data = fakeFullBytes, metadata = fakeFullMeta.toByteArray(Charsets.UTF_8))),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn false

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeData, fakeEventType)

        // Then — diffWritten=true, so result is true even though full view write failed
        assertThat(result).isTrue()
        verify(rumMonitor.mockSdkCore, never()).writeLastViewEvent(any<ByteArray>())
    }

    @Test
    fun `M NOT write full view W write() { DiffThenFullView, diff write fails }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        forge: Forge
    ) {
        // Given — diff write fails; writeMappedViewEvent must not be called at all
        val fakeData = DiffThenFullView(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffBytes = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeSerializedMeta = forge.aString()
        val diffMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventMetaSerializer.serialize(diffMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(RawBatchEvent(data = fakeDiffBytes, metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8))),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn false

        // When
        testedWriter.write(mockEventBatchWriter, fakeData, fakeEventType)

        // Then — only one batch write (the diff); full view batch write never attempted
        verify(mockEventBatchWriter, times(1)).write(any(), isNull(), any(), any<TelemetryContext>())
    }

    @Test
    fun `M NOT call writeLastViewEvent W write() { DiffThenFullView, diff write fails }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeData = DiffThenFullView(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffBytes = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeSerializedMeta = forge.aString()
        val diffMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventMetaSerializer.serialize(diffMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(RawBatchEvent(data = fakeDiffBytes, metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8))),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn false

        // When
        testedWriter.write(mockEventBatchWriter, fakeData, fakeEventType)

        // Then
        verify(rumMonitor.mockSdkCore, never()).writeLastViewEvent(any<ByteArray>())
    }

    @Test
    fun `M return false W write() { DiffThenFullView, diff write fails }`(
        @Forgery fakeViewUpdateEvent: ViewUpdateEvent,
        @Forgery fakeViewEvent: ViewEvent,
        @StringForgery fakeDiffSerializedEvent: String,
        forge: Forge
    ) {
        // Given
        val fakeData = DiffThenFullView(
            viewUpdate = fakeViewUpdateEvent,
            viewEvent = fakeViewEvent
        )
        val fakeDiffBytes = fakeDiffSerializedEvent.toByteArray(Charsets.UTF_8)
        val fakeSerializedMeta = forge.aString()
        val diffMeta = RumEventMeta.ViewUpdate(
            viewId = fakeViewUpdateEvent.view.id,
            documentVersion = fakeViewUpdateEvent.dd.documentVersion
        )
        whenever(mockEventSerializer.serialize(fakeViewUpdateEvent)) doReturn fakeDiffSerializedEvent
        whenever(mockEventMetaSerializer.serialize(diffMeta)) doReturn fakeSerializedMeta
        whenever(
            mockEventBatchWriter.write(
                eq(RawBatchEvent(data = fakeDiffBytes, metadata = fakeSerializedMeta.toByteArray(Charsets.UTF_8))),
                anyOrNull<ByteArray>(),
                eq(fakeEventType),
                any<TelemetryContext>()
            )
        ) doReturn false

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeData, fakeEventType)

        // Then
        assertThat(result).isFalse()
    }

    // endregion

    // region writeOtherEvent edge cases

    @Test
    fun `M return false W write() { other event, mapper returns null }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.anElementFrom(
            forge.getForgery(ActionEvent::class.java),
            forge.getForgery(ResourceEvent::class.java),
            forge.getForgery(LongTaskEvent::class.java),
            forge.getForgery(ErrorEvent::class.java)
        )
        whenever(mockEventMapper.map(fakeEvent)) doReturn null

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeEvent, fakeEventType)

        // Then
        assertThat(result).isFalse
        verifyNoInteractions(mockEventBatchWriter)
    }

    // endregion

    // region accessibility

    @Test
    fun `M hasAccessibility false W write() { null accessibility }`(
        forge: Forge
    ) {
        // Given
        val viewEvent = forge.getForgery<ViewEvent>()
        val newView = viewEvent.view.copy(
            accessibility = null
        )
        val newViewEvent = viewEvent.copy(
            view = newView
        )

        whenever(mockEventMapper.map(newViewEvent)) doReturn newViewEvent
        whenever(mockEventSerializer.serialize(newViewEvent)) doReturn fakeSerializedEvent

        // When
        testedWriter.write(mockEventBatchWriter, newViewEvent, fakeEventType)

        // Then
        val captor = argumentCaptor<RumEventMeta.View>()
        verify(mockEventMetaSerializer).serialize(captor.capture())
        val metaData = captor.firstValue
        assertThat(metaData.hasAccessibility).isFalse
    }

    @Test
    fun `M hasAccessibility true W write() { non-null accessibility }`(
        forge: Forge
    ) {
        // Given
        val viewEvent = forge.getForgery<ViewEvent>()
        val newView = viewEvent.view.copy(
            accessibility = forge.getForgery()
        )
        val newViewEvent = viewEvent.copy(
            view = newView
        )

        whenever(mockEventMapper.map(newViewEvent)) doReturn newViewEvent
        whenever(mockEventSerializer.serialize(newViewEvent)) doReturn fakeSerializedEvent

        // When
        testedWriter.write(mockEventBatchWriter, newViewEvent, fakeEventType)

        // Then
        val captor = argumentCaptor<RumEventMeta.View>()
        verify(mockEventMetaSerializer).serialize(captor.capture())
        val metaData = captor.firstValue
        assertThat(metaData.hasAccessibility).isTrue
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}

private data class FakeEventWithoutType(val value: String)
