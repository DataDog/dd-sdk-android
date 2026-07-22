/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockFileReader.Companion.FAILED_TO_DESERIALIZE_ERROR
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TLVBlockFileReaderTest {
    private lateinit var testedReader: TLVBlockFileReader

    @Mock
    private lateinit var mockFile: File

    @Mock
    private lateinit var mockFileReaderWriter: FileReaderWriter

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    private lateinit var fakeDataString: String

    @Forgery
    private lateinit var fakeTelemetryContext: TelemetryContext

    private lateinit var fakeVersionBytes: ByteArray
    private lateinit var fakeDataBytes: ByteArray
    private lateinit var fakeBufferBytes: ByteArray

    @BeforeEach
    fun setup(@IntForgery(min = 0) fakeVersion: Int) {
        val versionBytes = createVersionBytes(fakeVersion)
        val dataBytes = createDataBytes()
        val dataToWrite = versionBytes + dataBytes

        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext)).thenReturn(dataToWrite)

        testedReader = TLVBlockFileReader(
            fileReaderWriter = mockFileReaderWriter,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M return empty collection W read() { input shorter than TLV type }`(
        @IntForgery(min = 0, max = 1) fakeSize: Int
    ) {
        // Given
        fakeBufferBytes = ByteArray(fakeSize)
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext))
            .thenReturn(fakeBufferBytes)

        // When
        val readBytes = testedReader.read(file = mockFile, telemetryContext = fakeTelemetryContext)

        // Then
        assertThat(readBytes).isEmpty()
    }

    @Test
    fun `M return empty collection W read() { invalid TLV type }`(
        @StringForgery(regex = "[a-zA-Z0-9]{2,32}") fakeInvalidTypeString: String
    ) {
        // Given
        fakeBufferBytes = fakeInvalidTypeString.toByteArray(Charsets.UTF_8)
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext))
            .thenReturn(fakeBufferBytes)

        // When
        val readBytes = testedReader.read(file = mockFile, telemetryContext = fakeTelemetryContext)

        // Then
        assertThat(readBytes).isEmpty()
    }

    @Test
    fun `M log error W read() { input shorter than TLV type }`() {
        // Given
        fakeBufferBytes = ByteArray(1)
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext))
            .thenReturn(fakeBufferBytes)

        // When
        testedReader.read(file = mockFile, telemetryContext = fakeTelemetryContext)

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = FAILED_TO_DESERIALIZE_ERROR,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = fakeBufferBytes.size)
        )
    }

    @Test
    fun `M log error W read() { invalid TLV type }`(
        @StringForgery(regex = "[a-zA-Z0-9]{2,32}") fakeInvalidTypeString: String
    ) {
        // Given
        fakeBufferBytes = fakeInvalidTypeString.toByteArray(Charsets.UTF_8)
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext))
            .thenReturn(fakeBufferBytes)

        // When
        testedReader.read(file = mockFile, telemetryContext = fakeTelemetryContext)

        // Then
        val captor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            level = eq(InternalLogger.Level.WARN),
            targets = eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
            captor.capture(),
            anyOrNull(),
            anyOrNull(),
            eq(fakeTelemetryContext.asAttributesMap(bytesLost = fakeBufferBytes.size))
        )
        assertThat(captor.firstValue.invoke())
            .startsWith("TLV header corrupt. Invalid type")
    }

    @Test
    fun `M return valid object W read() { valid TLV format }`() {
        // When
        val tlvArray = testedReader.read(file = mockFile, telemetryContext = fakeTelemetryContext)

        // Then
        assertThat(tlvArray).hasSize(2)
        val versionObject = tlvArray[0]
        val dataObject = tlvArray[1]

        assertThat(versionObject.type).isEqualTo(TLVBlockType.VERSION_CODE)
        assertThat(versionObject.data).isEqualTo(fakeVersionBytes)
        assertThat(dataObject.type).isEqualTo(TLVBlockType.DATA)
        assertThat(dataObject.data).isEqualTo(fakeDataBytes)
    }

    @Test
    fun `M return empty array W read() { invalid type length }`() {
        // Given
        val fakeByteArray = ByteBuffer.allocate(1).array()
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext)).thenReturn(fakeByteArray)

        // When
        val result = testedReader.read(mockFile, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = FAILED_TO_DESERIALIZE_ERROR,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = fakeByteArray.size)
        )
    }

    @Test
    fun `M return empty array W read() { invalid data length }`() {
        // Given
        val fakeBuffer = ByteBuffer.allocate(3)
        val fakeArray = fakeBuffer.putShort(TLVBlockType.DATA.rawValue.toShort()).array()
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext)).thenReturn(fakeArray)

        // When
        val result = testedReader.read(mockFile, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = FAILED_TO_DESERIALIZE_ERROR,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = fakeArray.size)
        )
    }

    @Test
    fun `M return empty array and log error W read() { declared block length exceeds limit }`(
        @IntForgery(min = 1, max = 8) fakeMaxEntrySize: Int,
        @IntForgery(min = 9) fakeDeclaredLength: Int
    ) {
        // Given
        val fakeBlock = ByteBuffer.allocate(Short.SIZE_BYTES + Int.SIZE_BYTES)
            .putShort(TLVBlockType.DATA.rawValue.toShort())
            .putInt(fakeDeclaredLength)
            .array()
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext)).thenReturn(fakeBlock)
        testedReader = TLVBlockFileReader(
            fileReaderWriter = mockFileReaderWriter,
            internalLogger = mockInternalLogger,
            maxEntrySize = fakeMaxEntrySize
        )

        // When
        val result = testedReader.read(mockFile, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = TLVBlockFileReader.CORRUPT_DATA_LENGTH_ERROR
                .format(Locale.US, fakeMaxEntrySize, fakeDeclaredLength),
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = fakeBlock.size)
        )
    }

    @Test
    fun `M return empty array and log error W read() { declared block length is negative }`(
        @IntForgery(min = 1) fakeMaxEntrySize: Int,
        @IntForgery(max = 0) fakeNegativeLength: Int
    ) {
        // Given
        val fakeBlock = ByteBuffer.allocate(Short.SIZE_BYTES + Int.SIZE_BYTES)
            .putShort(TLVBlockType.DATA.rawValue.toShort())
            .putInt(fakeNegativeLength)
            .array()
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext)).thenReturn(fakeBlock)
        testedReader = TLVBlockFileReader(
            fileReaderWriter = mockFileReaderWriter,
            internalLogger = mockInternalLogger,
            maxEntrySize = fakeMaxEntrySize
        )

        // When
        val result = testedReader.read(mockFile, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = TLVBlockFileReader.CORRUPT_DATA_LENGTH_ERROR
                .format(Locale.US, fakeMaxEntrySize, fakeNegativeLength),
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = fakeBlock.size)
        )
    }

    @Test
    fun `M return empty array and log error W read() { declared length exceeds available bytes }`(
        @IntForgery(min = 1, max = 10) fakeAvailableDataSize: Int,
        @IntForgery(min = 11, max = 1000) fakeDeclaredLength: Int
    ) {
        // Given
        val fakeBlock = ByteBuffer.allocate(Short.SIZE_BYTES + Int.SIZE_BYTES + fakeAvailableDataSize)
            .putShort(TLVBlockType.DATA.rawValue.toShort())
            .putInt(fakeDeclaredLength)
            .put(ByteArray(fakeAvailableDataSize))
            .array()
        whenever(mockFileReaderWriter.readData(mockFile, fakeTelemetryContext)).thenReturn(fakeBlock)

        // When
        val result = testedReader.read(mockFile, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = FAILED_TO_DESERIALIZE_ERROR,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = fakeBlock.size)
        )
    }

    private fun createVersionBytes(fakeVersion: Int): ByteArray {
        val versionType = TLVBlockType.VERSION_CODE.rawValue.toShort()
        fakeVersionBytes = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(fakeVersion).array()

        return ByteBuffer
            .allocate(fakeVersionBytes.size + Int.SIZE_BYTES + Short.SIZE_BYTES)
            .putShort(versionType)
            .putInt(fakeVersionBytes.size)
            .put(fakeVersionBytes)
            .array()
    }

    private fun createDataBytes(): ByteArray {
        fakeDataBytes = fakeDataString.toByteArray(Charsets.UTF_8)
        val dataType = TLVBlockType.DATA.rawValue.toShort()

        return ByteBuffer
            .allocate(fakeDataBytes.size + Int.SIZE_BYTES + Short.SIZE_BYTES)
            .putShort(dataType)
            .putInt(fakeDataBytes.size)
            .put(fakeDataBytes)
            .array()
    }
}
