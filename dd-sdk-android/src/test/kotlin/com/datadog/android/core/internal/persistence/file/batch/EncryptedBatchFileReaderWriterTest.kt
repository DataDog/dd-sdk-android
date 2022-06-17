/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import android.util.Log
import com.datadog.android.log.Logger
import com.datadog.android.security.Encryption
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
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
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EncryptedBatchFileReaderWriterTest {

    @Mock
    lateinit var mockEncryption: Encryption

    @Mock
    lateinit var mockBatchFileReaderWriter: BatchFileReaderWriter

    @Mock
    lateinit var mockFile: File

    @Mock
    lateinit var mockInternalLogger: Logger

    private lateinit var testedReaderWriter: EncryptedBatchReaderWriter

    @BeforeEach
    fun setUp() {
        whenever(mockBatchFileReaderWriter.writeData(any(), any(), any())) doReturn true

        whenever(mockEncryption.encrypt(any())) doAnswer {
            val bytes = it.getArgument<ByteArray>(0)
            encrypt(bytes)
        }
        whenever(mockEncryption.decrypt(any())) doAnswer {
            val bytes = it.getArgument<ByteArray>(0)
            decrypt(bytes)
        }

        testedReaderWriter =
            EncryptedBatchReaderWriter(mockEncryption, mockBatchFileReaderWriter)
    }

    // region BatchFileReaderWriter#writeData tests

    @Test
    fun `𝕄 encrypt data and return true 𝕎 writeData()`(
        @StringForgery data: String,
        @BoolForgery append: Boolean
    ) {
        // When
        val result = testedReaderWriter.writeData(
            mockFile,
            data.toByteArray(),
            append = append
        )
        val encryptedData = encrypt(data.toByteArray())

        // Then
        assertThat(result).isTrue()
        verify(mockBatchFileReaderWriter)
            .writeData(
                mockFile,
                encryptedData,
                append
            )

        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(logger.mockDevLogHandler)
    }

    @Test
    fun `𝕄 log internal error and return false 𝕎 writeData() { bad encryption result }`(
        @StringForgery data: String,
        @BoolForgery append: Boolean
    ) {
        // Given
        whenever(mockEncryption.encrypt(data.toByteArray())) doReturn ByteArray(0)

        // When
        val result = testedReaderWriter.writeData(
            mockFile,
            data.toByteArray(),
            append = append
        )

        // Then
        assertThat(result).isFalse()

        verify(logger.mockDevLogHandler).handleLog(
            Log.ERROR,
            EncryptedBatchReaderWriter.BAD_ENCRYPTION_RESULT_MESSAGE
        )
        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(mockBatchFileReaderWriter)
    }

    // endregion

    // region BatchFileReader#readData tests

    @Test
    fun `𝕄 decrypt data 𝕎 readData()`(
        forge: Forge
    ) {
        // Given
        val events = forge.aList {
            forge.aString().toByteArray()
        }

        whenever(
            mockBatchFileReaderWriter.readData(mockFile)
        ) doReturn events.map { encrypt(it) }

        // When
        val result = testedReaderWriter.readData(mockFile)

        // Then
        assertThat(result).containsExactlyElementsOf(events)
    }

    // endregion

    // region writeData + readData

    @Test
    fun `𝕄 return valid data 𝕎 writeData() + readData()`(
        forge: Forge
    ) {
        // Given
        val events = forge.aList { forge.aString().toByteArray() }

        val storage = mutableListOf<ByteArray>()

        whenever(
            mockBatchFileReaderWriter.writeData(
                eq(mockFile),
                any(),
                eq(true)
            )
        ) doAnswer {
            storage.add(it.getArgument(1))
            true
        }

        whenever(
            mockBatchFileReaderWriter.readData(mockFile)
        ) doAnswer { storage }

        // When
        var writeResult = true
        events.forEach {
            writeResult = writeResult && testedReaderWriter.writeData(mockFile, it, true)
        }
        val readResult = testedReaderWriter.readData(mockFile)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).containsExactlyElementsOf(events)

        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(logger.mockDevLogHandler)
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

        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
