/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.log.internal.storage

import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.forge.aThrowable
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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

    @BeforeEach
    fun `set up`() {
        testedWriter = LogsDataWriter(mockSerializer, mockInternalLogger)
    }

    @Test
    fun `𝕄 write data 𝕎 write()`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // Given
        val fakeSerializedLogEvent = fakeLogEvent.toString()
        whenever(mockSerializer.serialize(fakeLogEvent)) doReturn fakeSerializedLogEvent
        whenever(
            mockEventBatchWriter.write(
                fakeSerializedLogEvent.toByteArray(),
                null
            )
        ) doReturn true

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent)

        // Then
        assertThat(result).isTrue
        verify(mockEventBatchWriter).write(fakeSerializedLogEvent.toByteArray(), null)
        verifyZeroInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 return false 𝕎 write() { bytes were not written }`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // Given
        val fakeSerializedLogEvent = fakeLogEvent.toString()
        whenever(mockSerializer.serialize(fakeLogEvent)) doReturn fakeSerializedLogEvent
        whenever(
            mockEventBatchWriter.write(
                fakeSerializedLogEvent.toByteArray(),
                null
            )
        ) doReturn false

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent)

        // Then
        assertThat(result).isFalse
        verify(mockEventBatchWriter).write(fakeLogEvent.toString().toByteArray(), null)
        verifyZeroInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 return false 𝕎 write() { serialization returns null }`(
        @Forgery fakeLogEvent: LogEvent
    ) {
        // Given
        whenever(mockSerializer.serialize(fakeLogEvent)) doReturn null

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent)

        // Then
        assertThat(result).isFalse

        verifyZeroInteractions(mockEventBatchWriter, mockInternalLogger)
    }

    @Test
    fun `𝕄 return false and log error 𝕎 write() { serialization failed with exception }`(
        @Forgery fakeLogEvent: LogEvent,
        forge: Forge
    ) {
        // Given
        val fakeThrowable = forge.aThrowable()
        whenever(mockSerializer.serialize(fakeLogEvent)) doThrow fakeThrowable

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent)

        // Then
        assertThat(result).isFalse

        verify(mockInternalLogger)
            .log(
                eq(InternalLogger.Level.ERROR),
                targets = eq(
                    listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    )
                ),
                any(),
                eq(fakeThrowable)
            )

        verifyZeroInteractions(mockEventBatchWriter)
    }
}
