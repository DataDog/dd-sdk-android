/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.security.Encryption
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.android.v2.api.InternalLogger
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
internal class EncryptedBatchFileReaderWriterTest {

    @Mock
    lateinit var mockEncryption: Encryption

    @Mock
    lateinit var mockBatchFileReaderWriter: BatchFileReaderWriter

    @Mock
    lateinit var mockFile: File

    @Mock
    lateinit var mockInternalLogger: InternalLogger

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
            EncryptedBatchReaderWriter(
                mockEncryption,
                mockBatchFileReaderWriter,
                mockInternalLogger
            )
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

        verifyNoInteractions(mockInternalLogger)
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
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            EncryptedBatchReaderWriter.BAD_ENCRYPTION_RESULT_MESSAGE
        )
        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockBatchFileReaderWriter)
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
