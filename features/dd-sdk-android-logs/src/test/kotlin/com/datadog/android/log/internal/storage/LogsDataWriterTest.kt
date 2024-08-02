/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.storage

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.aThrowable
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsDataWriterTest {

    private lateinit var testedWriter: LogsDataWriter

    @Mock
    lateinit var mockSerializer: Serializer<LogEvent>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Forgery
    lateinit var fakeEventType: EventType

    @BeforeEach
    fun `set up`() {
        testedWriter = LogsDataWriter(mockSerializer, mockInternalLogger)
    }

    @Test
    fun `M write data W write()`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // Given
        val fakeSerializedLogEvent = fakeLogEvent.toString()
        whenever(mockSerializer.serialize(fakeLogEvent)) doReturn fakeSerializedLogEvent
        whenever(
            mockEventBatchWriter.write(
                RawBatchEvent(data = fakeSerializedLogEvent.toByteArray()),
                null,
                fakeEventType
            )
        ) doReturn true

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent, fakeEventType)

        // Then
        assertThat(result).isTrue
        verify(mockEventBatchWriter).write(
            RawBatchEvent(data = fakeSerializedLogEvent.toByteArray()),
            null,
            fakeEventType
        )
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return false W write() { bytes were not written }`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // Given
        val fakeSerializedLogEvent = fakeLogEvent.toString()
        whenever(mockSerializer.serialize(fakeLogEvent)) doReturn fakeSerializedLogEvent
        whenever(
            mockEventBatchWriter.write(
                RawBatchEvent(data = fakeSerializedLogEvent.toByteArray()),
                null,
                fakeEventType
            )
        ) doReturn false

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent, fakeEventType)

        // Then
        assertThat(result).isFalse
        verify(mockEventBatchWriter).write(
            RawBatchEvent(data = fakeLogEvent.toString().toByteArray()),
            null,
            fakeEventType
        )
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return false W write() { serialization returns null }`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // Given
        whenever(mockSerializer.serialize(fakeLogEvent)) doReturn null

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent, fakeEventType)

        // Then
        assertThat(result).isFalse

        verifyNoInteractions(mockEventBatchWriter, mockInternalLogger)
    }

    @Test
    fun `M return false and log error W write() { serialization failed with exception }`(
        @Forgery fakeLogEvent: LogEvent,
        forge: Forge
    ) {
        // Given
        val fakeThrowable = forge.aThrowable()
        whenever(mockSerializer.serialize(fakeLogEvent)) doThrow fakeThrowable

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent, fakeEventType)

        // Then
        assertThat(result).isFalse
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)),
                capture(),
                eq(fakeThrowable),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo("Error serializing LogEvent model")
        }

        verifyNoInteractions(mockEventBatchWriter)
    }
}
