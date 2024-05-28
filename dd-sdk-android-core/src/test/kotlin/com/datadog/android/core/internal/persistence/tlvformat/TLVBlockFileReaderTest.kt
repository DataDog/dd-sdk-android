/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.datastore.ext.toByteArray
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.tlvformat.TLVBlockFileReader.Companion.FAILED_TO_DESERIALIZE_ERROR
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
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

    @StringForgery(regex = "^(\\w{3})\$") // a minimal number of chars to avoid flakiness
    private lateinit var fakeDataString: String

    private lateinit var fakeUpdateBytes: ByteArray
    private lateinit var fakeVersionBytes: ByteArray
    private lateinit var fakeDataBytes: ByteArray
    private lateinit var fakeBufferBytes: ByteArray

    @BeforeEach
    fun setup(@IntForgery(min = 0, max = 10) fakeVersion: Int) {
        val lastUpdateBytes = createLastUpdateBytes()
        val versionBytes = createVersionBytes(fakeVersion)
        val dataBytes = createDataBytes()
        val dataToWrite = lastUpdateBytes + versionBytes + dataBytes

        whenever(mockFileReaderWriter.readData(mockFile)).thenReturn(dataToWrite)

        testedReader = TLVBlockFileReader(
            fileReaderWriter = mockFileReaderWriter,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M return empty collection W read() { invalid TLV type }`() {
        // Given
        fakeBufferBytes = fakeDataString.toByteArray(Charsets.UTF_8)
        whenever(mockFileReaderWriter.readData(mockFile))
            .thenReturn(fakeBufferBytes)

        // When
        val readBytes = testedReader.read(file = mockFile)

        // Then
        assertThat(readBytes).isEmpty()
    }

    @Test
    fun `M log error W read() { invalid TLV type }`() {
        // Given
        fakeBufferBytes = fakeDataString.toByteArray(Charsets.UTF_8)
        whenever(mockFileReaderWriter.readData(mockFile))
            .thenReturn(fakeBufferBytes)

        // When
        testedReader.read(file = mockFile)

        // Then
        val captor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            level = eq(InternalLogger.Level.WARN),
            target = eq(InternalLogger.Target.MAINTAINER),
            captor.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        assertThat(captor.firstValue.invoke())
            .startsWith("TLV header corrupt. Invalid type")
    }

    @Test
    fun `M return valid object W read() { valid TLV format }`() {
        // When
        val tlvArray = testedReader.read(file = mockFile)

        // Then
        assertThat(tlvArray.size).isEqualTo(3)
        val lastUpdateObject = tlvArray[0]
        val versionObject = tlvArray[1]
        val dataObject = tlvArray[2]

        assertThat(lastUpdateObject.type).isEqualTo(TLVBlockType.LAST_UPDATE_DATE)
        assertThat(lastUpdateObject.data).isEqualTo(fakeUpdateBytes)
        assertThat(versionObject.type).isEqualTo(TLVBlockType.VERSION_CODE)
        assertThat(versionObject.data).isEqualTo(fakeVersionBytes)
        assertThat(dataObject.type).isEqualTo(TLVBlockType.DATA)
        assertThat(dataObject.data).isEqualTo(fakeDataBytes)
    }

    @Test
    fun `M return empty array W read() { invalid type length }`() {
        // Given
        val fakeByteArray = ByteBuffer.allocate(1).array()
        whenever(mockFileReaderWriter.readData(mockFile)).thenReturn(fakeByteArray)

        // When
        val result = testedReader.read(mockFile)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = FAILED_TO_DESERIALIZE_ERROR
        )
    }

    @Test
    fun `M return empty array W read() { invalid data length }`() {
        // Given
        val fakeBuffer = ByteBuffer.allocate(3)
        val fakeArray = fakeBuffer.putShort(TLVBlockType.DATA.rawValue.toShort()).array()
        whenever(mockFileReaderWriter.readData(mockFile)).thenReturn(fakeArray)

        // When
        val result = testedReader.read(mockFile)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = FAILED_TO_DESERIALIZE_ERROR
        )
    }

    private fun createLastUpdateBytes(): ByteArray {
        val now = System.currentTimeMillis()
        fakeUpdateBytes = now.toByteArray()
        val lastUpdateType = TLVBlockType.LAST_UPDATE_DATE.rawValue.toShort()

        return ByteBuffer
            .allocate(fakeUpdateBytes.size + Int.SIZE_BYTES + Short.SIZE_BYTES)
            .putShort(lastUpdateType)
            .putInt(fakeUpdateBytes.size)
            .put(fakeUpdateBytes)
            .array()
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
