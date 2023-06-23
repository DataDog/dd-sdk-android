/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.storage

import com.datadog.android.core.persistence.Serializer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.forge.aThrowable
import com.google.gson.JsonObject
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
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
internal class WebViewDataWriterTest {

    private lateinit var testedWriter: WebViewDataWriter

    @Mock
    lateinit var mockSerializer: Serializer<JsonObject>

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @BeforeEach
    fun `set up`() {
        testedWriter = WebViewDataWriter(mockSerializer, mockLogger)
    }

    @Test
    fun `𝕄 write data 𝕎 write()`(
        @Forgery fakeLogEvent: JsonObject
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
        verifyNoInteractions(mockLogger)
    }

    @Test
    fun `𝕄 return false 𝕎 write() { bytes were not written }`(
        @Forgery fakeLogEvent: JsonObject
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
        verifyNoInteractions(mockLogger)
    }

    @Test
    fun `𝕄 return false 𝕎 write() { serialization returns null }`(
        @Forgery fakeLogEvent: JsonObject
    ) {
        // Given
        whenever(mockSerializer.serialize(fakeLogEvent)) doReturn null

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent)

        // Then
        assertThat(result).isFalse

        verifyNoInteractions(mockEventBatchWriter, mockLogger)
    }

    @Test
    fun `𝕄 return false and log error 𝕎 write() { serialization failed with exception }`(
        @Forgery fakeLogEvent: JsonObject,
        forge: Forge
    ) {
        // Given
        val fakeThrowable = forge.aThrowable()
        whenever(mockSerializer.serialize(fakeLogEvent)) doThrow fakeThrowable

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeLogEvent)

        // Then
        assertThat(result).isFalse

        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            "Error serializing JsonObject model",
            fakeThrowable
        )

        verifyNoInteractions(mockEventBatchWriter)
    }
}
