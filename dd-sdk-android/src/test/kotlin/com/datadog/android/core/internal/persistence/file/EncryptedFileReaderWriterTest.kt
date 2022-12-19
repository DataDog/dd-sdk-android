/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.log.Logger
import com.datadog.android.security.Encryption
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
import org.mockito.quality.Strictness
import java.io.File
import kotlin.experimental.inv

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
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
    lateinit var mockInternalLogger: Logger

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

        testedReaderWriter =
            EncryptedFileReaderWriter(mockEncryption, mockFileReaderWriterDelegate)
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

        verifyZeroInteractions(mockInternalLogger)
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

        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            EncryptedFileReaderWriter.BAD_ENCRYPTION_RESULT_MESSAGE
        )
        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(mockFileReaderWriterDelegate)
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

        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            EncryptedFileReaderWriter.APPEND_MODE_NOT_SUPPORTED_MESSAGE
        )
        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(mockFileReaderWriterDelegate)
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

        verifyZeroInteractions(mockInternalLogger)
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

    companion object {

        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
