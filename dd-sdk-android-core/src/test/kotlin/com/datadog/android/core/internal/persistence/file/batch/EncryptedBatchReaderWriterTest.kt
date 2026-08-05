/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.security.Encryption
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import kotlin.experimental.inv

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EncryptedBatchReaderWriterTest {

    @Mock
    lateinit var mockEncryption: Encryption

    @Mock
    lateinit var mockBatchFileReaderWriter: BatchFileReaderWriter

    @Mock
    lateinit var mockFile: File

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeFeatureName: String

    @Forgery
    lateinit var fakeTelemetryContext: TelemetryContext

    private lateinit var testedReaderWriter: EncryptedBatchReaderWriter

    @BeforeEach
    fun setUp() {
        whenever(mockBatchFileReaderWriter.writeBinaryData(any(), any(), any(), any())) doReturn true

        whenever(mockEncryption.encrypt(any())) doAnswer {
            val bytes = it.getArgument<ByteArray>(0)
            encrypt(bytes)
        }
        whenever(mockEncryption.decrypt(any())) doAnswer {
            val bytes = it.getArgument<ByteArray>(0)
            decrypt(bytes)
        }

        testedReaderWriter = EncryptedBatchReaderWriter(
            mockEncryption,
            mockBatchFileReaderWriter,
            mockInternalLogger
        )
    }

    // region BatchFileReaderWriter#serializeToBytes tests

    @Test
    fun `M encrypt data and delegate W serializeToBytes()`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery fakeSerialized: String
    ) {
        // Given
        val serializedBytes = fakeSerialized.toByteArray()
        val encryptedData = encrypt(batchEvent.data)
        val encryptedMetadata = encrypt(batchEvent.metadata)
        whenever(mockBatchFileReaderWriter.serializeToBytes(any(), any())) doReturn serializedBytes

        // When
        val result = testedReaderWriter.serializeToBytes(batchEvent, fakeTelemetryContext)

        // Then
        assertThat(result).isEqualTo(serializedBytes)
        verify(mockBatchFileReaderWriter).serializeToBytes(
            RawBatchEvent(data = encryptedData, metadata = encryptedMetadata),
            fakeTelemetryContext
        )
    }

    @Test
    fun `M log error and return null W serializeToBytes() { bad encryption result }`(
        @Forgery batchEvent: RawBatchEvent
    ) {
        // Given
        // non-empty event data whose encryption yields an empty result
        val nonEmptyEvent = batchEvent.copy(data = ByteArray(4) { 1 })
        whenever(mockEncryption.encrypt(nonEmptyEvent.data)) doReturn ByteArray(0)

        // When
        val result = testedReaderWriter.serializeToBytes(nonEmptyEvent, fakeTelemetryContext)

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            EncryptedBatchReaderWriter.BAD_ENCRYPTION_RESULT_MESSAGE,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = 4)
        )
        verifyNoMoreInteractions(mockInternalLogger)
        verify(mockBatchFileReaderWriter, never()).serializeToBytes(any(), any())
    }

    // endregion

    // region BatchFileReaderWriter#writeBinaryData tests

    @Test
    fun `M delegate without re-encrypting W writeBinaryData()`(
        @StringForgery fakeContent: String,
        @BoolForgery append: Boolean
    ) {
        // Given
        val bytes = fakeContent.toByteArray()
        whenever(
            mockBatchFileReaderWriter.writeBinaryData(mockFile, bytes, append, fakeTelemetryContext)
        ) doReturn true

        // When
        val result = testedReaderWriter.writeBinaryData(mockFile, bytes, append, fakeTelemetryContext)

        // Then
        assertThat(result).isTrue()
        verify(mockBatchFileReaderWriter).writeBinaryData(mockFile, bytes, append, fakeTelemetryContext)
    }

    // endregion

    // region BatchFileReader#readData tests

    @Test
    fun `M decrypt data W readData()`(
        @Forgery events: List<RawBatchEvent>
    ) {
        // Given
        whenever(
            mockBatchFileReaderWriter.readData(eq(mockFile), any())
        ) doReturn events.map { RawBatchEvent(encrypt(it.data), encrypt(it.metadata)) }

        // When
        val result = testedReaderWriter.readData(mockFile, fakeTelemetryContext)

        // Then
        assertThat(result).containsExactlyElementsOf(events)
    }

    // endregion

    // region serializeToBytes + writeBinaryData + readData

    @Test
    fun `M return valid data W serializeToBytes() + writeBinaryData() + readData()`(
        @Forgery events: List<RawBatchEvent>
    ) {
        // Given
        // serializeToBytes returns encryptedEvent.data as placeholder bytes; we map that ByteArray
        // reference to the full encrypted event so writeBinaryData can store it, and readData
        // returns the stored encrypted events for EncryptedBatchReaderWriter to decrypt.
        val encryptedEventByBytes = mutableMapOf<ByteArray, RawBatchEvent>()
        val storage = mutableListOf<RawBatchEvent>()

        whenever(
            mockBatchFileReaderWriter.serializeToBytes(any(), any())
        ) doAnswer {
            val encryptedEvent = it.getArgument<RawBatchEvent>(0)
            val bytes = encryptedEvent.data
            encryptedEventByBytes[bytes] = encryptedEvent
            bytes
        }

        whenever(
            mockBatchFileReaderWriter.writeBinaryData(
                eq(mockFile),
                any(),
                eq(true),
                any()
            )
        ) doAnswer {
            val bytes = it.getArgument<ByteArray>(1)
            encryptedEventByBytes[bytes]?.also { event -> storage.add(event) }
            true
        }

        whenever(
            mockBatchFileReaderWriter.readData(eq(mockFile), any())
        ) doAnswer { storage.toList() }

        // When
        var writeResult = true
        events.forEach {
            val bytes = checkNotNull(testedReaderWriter.serializeToBytes(it, fakeTelemetryContext))
            writeResult = writeResult && testedReaderWriter.writeBinaryData(
                mockFile,
                bytes,
                append = true,
                fakeTelemetryContext
            )
        }
        val readResult = testedReaderWriter.readData(mockFile, fakeTelemetryContext)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).containsExactlyElementsOf(events)

        verifyNoInteractions(mockInternalLogger)
    }

    // endregion

    // region private

    // this is valid encryption-decryption pair, after the round we will get the original data
    private fun encrypt(data: ByteArray): ByteArray {
        return data.map { it.inv() }.toByteArray()
    }

    private fun decrypt(data: ByteArray): ByteArray {
        return data.map { it.inv() }.toByteArray()
    }

    // endregion
}
