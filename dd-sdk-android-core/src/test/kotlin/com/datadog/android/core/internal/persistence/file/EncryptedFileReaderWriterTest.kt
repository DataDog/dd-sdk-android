/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.api.InternalLogger
import com.datadog.android.security.Encryption
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import kotlin.experimental.inv
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EncryptedFileReaderWriterTest {

    @Mock
    lateinit var mockEncryption: Encryption

    @Mock
    lateinit var mockFileReaderWriterDelegate: FileReaderWriter

    @Mock
    lateinit var mockFile: File

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedReaderWriter: EncryptedFileReaderWriter

    @BeforeEach
    fun setUp() {
        whenever(mockFileReaderWriterDelegate.writeData(any(), any(), any())) doReturn true

        whenever(mockEncryption.encrypt(any())) doAnswer {
            val bytes = it.getArgument<ByteArray>(0)
            encrypt(bytes)
        }
        whenever(mockEncryption.decrypt(any())) doAnswer {
            val bytes = it.getArgument<ByteArray>(0)
            decrypt(bytes)
        }

        testedReaderWriter = EncryptedFileReaderWriter(
            mockEncryption,
            mockFileReaderWriterDelegate,
            mockInternalLogger
        )
    }

    // region EncryptedFileReaderWriter#writeData tests

    @Test
    fun `𝕄 encrypt data and return true 𝕎 writeData()`(
        @StringForgery data: String
    ) {
        // When
        val result = testedReaderWriter.writeData(
            mockFile,
            data.toByteArray(),
            append = false
        )
        val encryptedData = encrypt(data.toByteArray())

        // Then
        assertThat(result).isTrue()
        verify(mockFileReaderWriterDelegate)
            .writeData(
                mockFile,
                encryptedData,
                false
            )

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log internal error and return false 𝕎 writeData() { bad encryption result }`(
        @StringForgery data: String
    ) {
        // Given
        whenever(mockEncryption.encrypt(data.toByteArray())) doReturn ByteArray(0)

        // When
        val result = testedReaderWriter.writeData(
            mockFile,
            data.toByteArray(),
            append = false
        )

        // Then
        assertThat(result).isFalse()

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            EncryptedFileReaderWriter.BAD_ENCRYPTION_RESULT_MESSAGE
        )
        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockFileReaderWriterDelegate)
    }

    @Test
    fun `𝕄 log internal error and return false 𝕎 writeData() { append = true }`(
        @StringForgery data: String
    ) {
        // When
        val result = testedReaderWriter.writeData(
            mockFile,
            data.toByteArray(),
            append = true
        )

        // Then
        assertThat(result).isFalse()

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            EncryptedFileReaderWriter.APPEND_MODE_NOT_SUPPORTED_MESSAGE
        )
        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockFileReaderWriterDelegate)
    }

    // endregion

    // region FileReader#readData tests

    @Test
    fun `𝕄 decrypt data 𝕎 readData()`(
        @StringForgery data: String
    ) {
        // Given
        whenever(
            mockFileReaderWriterDelegate.readData(mockFile)
        ) doReturn encrypt(data.toByteArray())

        // When
        val result = testedReaderWriter.readData(mockFile)

        // Then
        assertThat(result).isEqualTo(data.toByteArray())
    }

    // endregion

    // region writeData + readData

    @Test
    fun `𝕄 return valid data 𝕎 writeData() + readData()`(
        @StringForgery data: String
    ) {
        // Given
        var storage: ByteArray? = null

        whenever(
            mockFileReaderWriterDelegate.writeData(
                eq(mockFile),
                any(),
                eq(false)
            )
        ) doAnswer {
            storage = it.getArgument(1)
            true
        }

        whenever(
            mockFileReaderWriterDelegate.readData(mockFile)
        ) doAnswer { storage }

        // When
        val writeResult = testedReaderWriter.writeData(mockFile, data.toByteArray(), false)
        val readResult = testedReaderWriter.readData(mockFile)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).isEqualTo(data.toByteArray())

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
