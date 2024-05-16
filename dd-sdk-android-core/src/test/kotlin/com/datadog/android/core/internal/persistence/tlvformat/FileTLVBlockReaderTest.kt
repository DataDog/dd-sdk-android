/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.tlvformat

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.existsSafe
import com.datadog.android.core.internal.persistence.file.lengthSafe
import com.datadog.android.core.internal.persistence.tlvformat.FileTLVBlockReader.Companion.CORRUPT_TLV_HEADER_TYPE_ERROR
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
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
internal class FileTLVBlockReaderTest {
    private lateinit var testedReader: FileTLVBlockReader

    @Mock
    private lateinit var mockFile: File

    @Mock
    private lateinit var mockFileReaderWriter: FileReaderWriter

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    private lateinit var fakeDataString: String

    private lateinit var fakeDataBytes: ByteArray
    private lateinit var fakeBufferBytes: ByteArray

    @BeforeEach
    fun setup() {
        fakeDataBytes = fakeDataString.toByteArray(Charsets.UTF_8)
        val length = fakeDataBytes.size
        val type = TLVBlockType.DATA

        val buffer = ByteBuffer.allocate(fakeDataBytes.size + 6)
        buffer.putShort(type.rawValue.toShort())
        buffer.putInt(length)
        buffer.put(fakeDataBytes)

        fakeBufferBytes = buffer.array()

        whenever(mockFile.existsSafe(mockInternalLogger)).thenReturn(true)
        whenever(mockFile.lengthSafe(mockInternalLogger)).thenReturn(fakeBufferBytes.size.toLong())
        whenever(mockFileReaderWriter.readData(mockFile)).thenReturn(fakeBufferBytes)

        testedReader = FileTLVBlockReader(
            fileReaderWriter = mockFileReaderWriter,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M return empty collection W all() { file does not exist }`() {
        // Given
        whenever(mockFile.existsSafe(mockInternalLogger)).thenReturn(false)

        // When
        val readBytes = testedReader.all(file = mockFile)

        // Then
        assertThat(readBytes).isEmpty()
    }

    @Test
    fun `M return empty collection W all() { empty file }`() {
        // Given
        whenever(mockFile.lengthSafe(mockInternalLogger)).thenReturn(0L)

        // When
        val readBytes = testedReader.all(file = mockFile)

        // Then
        assertThat(readBytes).isEmpty()
    }

    @Test
    fun `M return empty collection W all() { invalid TLV type }`() {
        // Given
        fakeBufferBytes = fakeDataString.toByteArray(Charsets.UTF_8)
        whenever(mockFileReaderWriter.readData(mockFile))
            .thenReturn(fakeBufferBytes)

        // When
        val readBytes = testedReader.all(file = mockFile)

        // Then
        assertThat(readBytes).isEmpty()
    }

    @Test
    fun `M log error W all() { invalid TLV type }`() {
        // Given
        fakeBufferBytes = fakeDataString.toByteArray(Charsets.UTF_8)
        whenever(mockFileReaderWriter.readData(mockFile))
            .thenReturn(fakeBufferBytes)

        // When
        testedReader.all(file = mockFile)

        // Then
        mockInternalLogger.verifyLog(
            target = InternalLogger.Target.MAINTAINER,
            level = InternalLogger.Level.ERROR,
            message = CORRUPT_TLV_HEADER_TYPE_ERROR
        )
    }

    @Test
    fun `M return valid object W all() { valid TLV format }`() {
        // When
        val tlvArray = testedReader.all(file = mockFile)
        val dataObject = tlvArray[0]

        // Then
        assertThat(dataObject.type).isEqualTo(TLVBlockType.DATA)
        assertThat(dataObject.data).isEqualTo(fakeDataBytes)
    }
}
