/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import android.util.Log
import com.datadog.android.log.Logger
import com.datadog.android.security.Encryption
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.Base64
import kotlin.experimental.inv
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
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
internal class EncryptedFileHandlerTest {

    @Mock
    lateinit var mockEncryption: Encryption

    @Mock
    lateinit var mockFileHandlerDelegate: FileHandler

    @Mock
    lateinit var mockFile: File

    @Mock
    lateinit var mockInternalLogger: Logger

    private lateinit var testedFileHandler: EncryptedFileHandler

    @BeforeEach
    fun setUp() {
        whenever(mockFileHandlerDelegate.writeData(any(), any(), any(), anyOrNull())) doReturn true

        whenever(mockEncryption.encrypt(any())) doAnswer {
            val bytes = it.getArgument<ByteArray>(0)
            encrypt(bytes)
        }
        whenever(mockEncryption.decrypt(any())) doAnswer {
            val bytes = it.getArgument<ByteArray>(0)
            decrypt(bytes)
        }

        testedFileHandler =
            EncryptedFileHandler(mockEncryption, mockFileHandlerDelegate, mockInternalLogger)
    }

    // region FileHandler#writeData tests

    @Test
    fun `𝕄 encrypt data and return true 𝕎 writeData() { separator not in Base64 set }`(
        @StringForgery data: String,
        @BoolForgery append: Boolean,
        forge: Forge
    ) {
        // Given
        val separator = forge.aNonBase64Separator()
        val expected = Base64.getEncoder().encode(encrypt(data.toByteArray()))

        // When
        val result = testedFileHandler.writeData(
            mockFile,
            data.toByteArray(),
            append = append,
            separator = separator
        )

        // Then
        assertThat(result).isTrue()
        verify(mockFileHandlerDelegate)
            .writeData(
                mockFile,
                expected,
                append,
                separator
            )

        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(logger.mockDevLogHandler)
    }

    @Test
    fun `𝕄 log internal error and return false 𝕎 writeData() { separator is in Base64 set }`(
        @StringForgery data: String,
        @BoolForgery append: Boolean,
        forge: Forge
    ) {
        // Given
        val separator = forge.anElementFrom(BASE_64_CHARS)

        // When
        val result = testedFileHandler.writeData(
            mockFile,
            data.toByteArray(),
            append = append,
            separator = ByteArray(1) { separator.code.toByte() }
        )

        // Then
        assertThat(result).isFalse()

        verify(mockInternalLogger).e(EncryptedFileHandler.INVALID_SEPARATOR_MESSAGE)
        verifyZeroInteractions(mockEncryption)
        verifyZeroInteractions(mockFileHandlerDelegate)
    }

    @Test
    fun `𝕄 log internal error and return false 𝕎 writeData() { append + missing separator }`(
        @StringForgery data: String
    ) {
        // When
        val result = testedFileHandler.writeData(
            mockFile,
            data.toByteArray(),
            append = true,
            separator = null
        )

        // Then
        assertThat(result).isFalse()

        verify(mockInternalLogger).e(EncryptedFileHandler.MISSING_SEPARATOR_MESSAGE)
        verifyZeroInteractions(mockEncryption)
        verifyZeroInteractions(mockFileHandlerDelegate)
    }

    @Test
    fun `𝕄 log internal error and return false 𝕎 writeData() { bad encryption result }`(
        @StringForgery data: String,
        @BoolForgery append: Boolean,
        forge: Forge
    ) {
        // Given
        val separator = forge.aNonBase64Separator()

        whenever(mockEncryption.encrypt(data.toByteArray())) doReturn ByteArray(0)

        // When
        val result = testedFileHandler.writeData(
            mockFile,
            data.toByteArray(),
            append = append,
            separator = separator
        )

        // Then
        assertThat(result).isFalse()

        verify(logger.mockDevLogHandler).handleLog(
            Log.ERROR,
            EncryptedFileHandler.BAD_ENCRYPTION_RESULT_MESSAGE
        )
        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(mockFileHandlerDelegate)
    }

    // endregion

    // region FileHandler#readData tests

    @Test
    fun `𝕄 decrypt data 𝕎 readData() { single item in a file + no prefix and suffix }`(
        @StringForgery data: String
    ) {
        // Given
        val encrypted = generateEncryptedData(listOf(data.toByteArray()))

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                null,
                null,
                null
            )
        ) doReturn encrypted

        // When
        val result = testedFileHandler.readData(mockFile, null, null, null)

        // Then
        assertThat(result).isEqualTo(data.toByteArray())
    }

    @Test
    fun `𝕄 decrypt data 𝕎 readData() { single item in a file with prefix, no suffix }`(
        @StringForgery data: String,
        @StringForgery prefix: String
    ) {
        // Given
        val prefixBytes = prefix.toByteArray()

        val encrypted = generateEncryptedData(listOf(data.toByteArray()), prefix = prefixBytes)

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                null,
                null
            )
        ) doReturn encrypted

        // When
        val result = testedFileHandler.readData(mockFile, prefixBytes, null, null)

        // Then
        assertThat(result).isEqualTo(prefixBytes + data.toByteArray())
    }

    @Test
    fun `𝕄 decrypt data 𝕎 readData() { single item in a file with suffix, no prefix }`(
        @StringForgery data: String,
        @StringForgery suffix: String
    ) {
        // Given
        val suffixBytes = suffix.toByteArray()

        val encrypted = generateEncryptedData(listOf(data.toByteArray()), suffix = suffixBytes)

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                null,
                suffixBytes,
                null
            )
        ) doReturn encrypted

        // When
        val result = testedFileHandler.readData(mockFile, null, suffixBytes, null)

        // Then
        assertThat(result).isEqualTo(data.toByteArray() + suffixBytes)
    }

    @Test
    fun `𝕄 decrypt data 𝕎 readData() { single item in a file with suffix and prefix }`(
        @StringForgery data: String,
        @StringForgery prefix: String,
        @StringForgery suffix: String
    ) {
        // Given
        val prefixBytes = prefix.toByteArray()
        val suffixBytes = suffix.toByteArray()

        val encrypted = generateEncryptedData(
            listOf(data.toByteArray()),
            prefix = prefixBytes,
            suffix = suffixBytes
        )

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                suffixBytes,
                null
            )
        ) doReturn encrypted

        // When
        val result = testedFileHandler.readData(mockFile, prefixBytes, suffixBytes, null)

        // Then
        assertThat(result).isEqualTo(prefixBytes + data.toByteArray() + suffixBytes)
    }

    @Test
    fun `𝕄 decrypt data 𝕎 readData() { multiple items in a file + no prefix and suffix }`(
        forge: Forge
    ) {
        // Given
        val dataItems = forge.aList {
            aString()
        }.map { it.toByteArray() }

        val separator = forge.aNonBase64Separator()

        val encrypted = generateEncryptedData(dataItems, separator = separator)

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                null,
                null,
                separator
            )
        ) doReturn encrypted

        // When
        val result = testedFileHandler.readData(mockFile, null, null, separator)

        // Then
        assertThat(result).isEqualTo(
            dataItems.join(separator)
        )
    }

    @Test
    fun `𝕄 decrypt data 𝕎 readData() { multiple items in a file with prefix, no suffix }`(
        @StringForgery prefix: String,
        forge: Forge
    ) {
        // Given
        val dataItems = forge.aList {
            aString()
        }.map { it.toByteArray() }

        val prefixBytes = prefix.toByteArray()
        val separator = forge.aNonBase64Separator()

        val encrypted =
            generateEncryptedData(dataItems, prefix = prefixBytes, separator = separator)

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                null,
                separator
            )
        ) doReturn encrypted

        // When
        val result = testedFileHandler.readData(mockFile, prefixBytes, null, separator)

        // Then
        assertThat(result).isEqualTo(prefixBytes + dataItems.join(separator))
    }

    @Test
    fun `𝕄 decrypt data 𝕎 readData() { multiple items in a file with suffix, no prefix }`(
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val dataItems = forge.aList {
            aString()
        }.map { it.toByteArray() }

        val suffixBytes = suffix.toByteArray()
        val separator = forge.aNonBase64Separator()

        val encrypted =
            generateEncryptedData(dataItems, suffix = suffixBytes, separator = separator)

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                null,
                suffixBytes,
                separator
            )
        ) doReturn encrypted

        // When
        val result = testedFileHandler.readData(mockFile, null, suffixBytes, separator)

        // Then
        assertThat(result).isEqualTo(dataItems.join(separator) + suffixBytes)
    }

    @Test
    fun `𝕄 decrypt data 𝕎 readData() { multiple items in a file with suffix and prefix }`(
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val dataItems = forge.aList {
            aString()
        }.map { it.toByteArray() }

        val prefixBytes = prefix.toByteArray()
        val suffixBytes = suffix.toByteArray()
        val separator = forge.aNonBase64Separator()

        val encrypted =
            generateEncryptedData(
                dataItems,
                suffix = suffixBytes,
                prefix = prefixBytes,
                separator = separator
            )

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                suffixBytes,
                separator
            )
        ) doReturn encrypted

        // When
        val result = testedFileHandler.readData(mockFile, prefixBytes, suffixBytes, separator)

        // Then
        assertThat(result).isEqualTo(prefixBytes + dataItems.join(separator) + suffixBytes)
    }

    @Test
    fun `𝕄 log internal + dev error 𝕎 readData() { cannot decode Base64, file with single item }`(
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val decodingException = IllegalArgumentException()
        testedFileHandler = EncryptedFileHandler(
            mockEncryption,
            mockFileHandlerDelegate,
            mockInternalLogger,
            base64Decoder = { throw decodingException }
        )

        val prefixBytes = forge.aNullable { prefix.toByteArray() }
        val suffixBytes = forge.aNullable { suffix.toByteArray() }

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                suffixBytes,
                null
            )
        ) doReturn decorate(forge.aString().toByteArray(), prefixBytes, suffixBytes)

        // When
        val result = testedFileHandler.readData(mockFile, prefixBytes, suffixBytes, null)

        // Then
        assertThat(result).isEqualTo(
            (prefixBytes ?: EMPTY_BYTE_ARRAY) + (suffixBytes ?: EMPTY_BYTE_ARRAY)
        )

        verify(mockInternalLogger).e(
            EncryptedFileHandler.BASE64_DECODING_ERROR_MESSAGE,
            decodingException
        )
        verify(logger.mockDevLogHandler).handleLog(
            Log.ERROR,
            EncryptedFileHandler.BASE64_DECODING_ERROR_MESSAGE,
            decodingException
        )

        verifyZeroInteractions(mockEncryption)
    }

    @Test
    fun `𝕄 log internal + dev error 𝕎 readData() { cannot decode Base64, file with many items }`(
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val dataItems = forge.aList { aString() }.map { it.toByteArray() }.distinct()

        val prefixBytes = forge.aNullable { prefix.toByteArray() }
        val suffixBytes = forge.aNullable { suffix.toByteArray() }
        val separator = forge.aNonBase64Separator()

        val encryptedItems = dataItems.map { Base64.getEncoder().encode(encrypt(it)) }
        val badItemIndex = forge.anInt(min = 0, max = encryptedItems.size)
        val badItem = encryptedItems[badItemIndex]

        val encryptedData = decorate(encryptedItems.join(separator), prefixBytes, suffixBytes)

        val decodingException = IllegalArgumentException()

        var failCounter = 0
        testedFileHandler = EncryptedFileHandler(
            mockEncryption,
            mockFileHandlerDelegate,
            mockInternalLogger,
            base64Decoder = {
                @Suppress("ReplaceArrayEqualityOpWithArraysEquals")
                if (it.contentEquals(badItem)) {
                    failCounter++
                    throw decodingException
                } else {
                    Base64.getDecoder().decode(it)
                }
            }
        )

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                suffixBytes,
                separator
            )
        ) doReturn encryptedData

        // When
        val result = testedFileHandler.readData(mockFile, prefixBytes, suffixBytes, separator)

        // Then
        val expected = dataItems.toMutableList().apply {
            this[badItemIndex] = EMPTY_BYTE_ARRAY
        }.join(separator)

        assertThat(result).isEqualTo(decorate(expected, prefixBytes, suffixBytes))

        verify(mockInternalLogger, times(failCounter)).e(
            EncryptedFileHandler.BASE64_DECODING_ERROR_MESSAGE,
            decodingException
        )
        verify(logger.mockDevLogHandler, times(failCounter)).handleLog(
            Log.ERROR,
            EncryptedFileHandler.BASE64_DECODING_ERROR_MESSAGE,
            decodingException
        )
    }

    @Test
    fun `𝕄 log internal + dev error 𝕎 readData() { data is less than prefix or suffix size }`(
        forge: Forge
    ) {
        // Given
        var prefixBytes = forge.aNullable { forge.aString(forge.aSmallInt()).toByteArray() }
        var suffixBytes = forge.aNullable { forge.aString(forge.aSmallInt()).toByteArray() }

        if (prefixBytes == null) {
            suffixBytes = forge.aString(forge.aSmallInt()).toByteArray()
        } else if (suffixBytes == null) {
            prefixBytes = forge.aString(forge.aSmallInt()).toByteArray()
        }

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                suffixBytes,
                null
            )
        ) doReturn forge.aString().toByteArray().take(
            forge.anInt(
                0,
                (prefixBytes?.size ?: 0) + (suffixBytes?.size ?: 0)
            )
        ).toByteArray()

        // When
        val result = testedFileHandler.readData(mockFile, prefixBytes, suffixBytes, null)

        // Then
        assertThat(result).isEqualTo(decorate(EMPTY_BYTE_ARRAY, prefixBytes, suffixBytes))

        verify(mockInternalLogger).e(EncryptedFileHandler.BAD_DATA_READ_MESSAGE)
        verify(logger.mockDevLogHandler)
            .handleLog(Log.ERROR, EncryptedFileHandler.BAD_DATA_READ_MESSAGE)

        verifyZeroInteractions(mockEncryption)
    }

    @Test
    fun `𝕄 log internal error and return empty arr 𝕎 readData() { separator is in Base64 set }`(
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // When
        val result = testedFileHandler.readData(
            mockFile,
            forge.aNullable { prefix.toByteArray() },
            forge.aNullable { suffix.toByteArray() },
            separator = ByteArray(1) { forge.anElementFrom(BASE_64_CHARS).code.toByte() }
        )

        // Then
        assertThat(result).isEqualTo(EMPTY_BYTE_ARRAY)

        verify(mockInternalLogger).e(EncryptedFileHandler.INVALID_SEPARATOR_MESSAGE)
        verifyZeroInteractions(mockEncryption)
        verifyZeroInteractions(mockFileHandlerDelegate)
    }

    // endregion

    @RepeatedTest(4)
    fun `𝕄 return valid data 𝕎 writeData() + readData() { single item file }`(
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        @StringForgery data: String,
        forge: Forge
    ) {
        // Given
        val dataBytes = data.toByteArray()
        val prefixBytes = forge.aNullable { prefix.toByteArray() }
        val suffixBytes = forge.aNullable { suffix.toByteArray() }

        val storage = mutableListOf<Byte>()

        whenever(
            mockFileHandlerDelegate.writeData(
                eq(mockFile),
                any(),
                eq(false),
                isNull()
            )
        ) doAnswer {
            it.getArgument<ByteArray>(1).forEach { byte ->
                storage.add(byte)
            }
            true
        }

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                suffixBytes,
                null
            )
        ) doAnswer { decorate(storage.toByteArray(), prefixBytes, suffixBytes) }

        // When
        val writeResult = testedFileHandler.writeData(mockFile, dataBytes, false, null)
        val readResult = testedFileHandler.readData(mockFile, prefixBytes, suffixBytes, null)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).isEqualTo(decorate(data.toByteArray(), prefixBytes, suffixBytes))

        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(logger.mockDevLogHandler)
    }

    @RepeatedTest(4)
    fun `𝕄 return valid data 𝕎 writeData() + readData() { multiple items file }`(
        @StringForgery prefix: String,
        @StringForgery suffix: String,
        forge: Forge
    ) {
        // Given
        val dataItems = forge.aList { aString() }.map { it.toByteArray() }
        val prefixBytes = forge.aNullable { prefix.toByteArray() }
        val suffixBytes = forge.aNullable { suffix.toByteArray() }
        val separator = forge.aNonBase64Separator()

        val storage = mutableListOf<Byte>()

        whenever(
            mockFileHandlerDelegate.writeData(
                eq(mockFile),
                any(),
                eq(true),
                eq(separator)
            )
        ) doAnswer {
            it.getArgument<ByteArray>(1).forEach { byte ->
                storage.add(byte)
            }
            it.getArgument<ByteArray>(3).forEach { byte ->
                storage.add(byte)
            }
            true
        }

        whenever(
            mockFileHandlerDelegate.readData(
                mockFile,
                prefixBytes,
                suffixBytes,
                separator
            )
        ) doAnswer { decorate(storage.toByteArray(), prefixBytes, suffixBytes) }

        // When
        var writeResult = true
        for (item in dataItems) {
            writeResult =
                writeResult and testedFileHandler.writeData(mockFile, item, true, separator)
        }
        val readResult = testedFileHandler.readData(mockFile, prefixBytes, suffixBytes, separator)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).isEqualTo(
            decorate(
                dataItems.join(separator),
                prefixBytes,
                suffixBytes
            )
        )

        verifyZeroInteractions(mockInternalLogger)
        verifyZeroInteractions(logger.mockDevLogHandler)
    }

    // region private

    // this is valid encryption-decryption pair, after the round we will get the original data
    private fun encrypt(data: ByteArray): ByteArray {
        return data.map { it.inv() }.toByteArray()
    }

    private fun decrypt(data: ByteArray): ByteArray {
        return data.map { it.inv() }.toByteArray()
    }

    private fun generateEncryptedData(
        data: List<ByteArray>,
        prefix: ByteArray? = null,
        suffix: ByteArray? = null,
        separator: ByteArray? = null
    ): ByteArray {
        val encryptedItems = data
            .map {
                Base64.getEncoder().encode(encrypt(it))
            }
            .join(separator ?: EMPTY_BYTE_ARRAY)

        return decorate(encryptedItems, prefix, suffix)
    }

    private fun decorate(data: ByteArray, prefix: ByteArray?, suffix: ByteArray?): ByteArray {
        return (prefix ?: EMPTY_BYTE_ARRAY) + data + (suffix ?: EMPTY_BYTE_ARRAY)
    }

    private fun List<ByteArray>.join(separator: ByteArray): ByteArray {
        return filter { it.isNotEmpty() }
            .flatMap {
                listOf(it, separator)
            }
            .dropLast(1)
            .flatMap { it.toList() }
            .toByteArray()
    }

    private fun isBase64Char(separator: String): Boolean {
        if (separator.length != 1) {
            return false
        }

        val separatorChar = separator.elementAt(0)
        return separatorChar in BASE_64_CHARS
    }

    private fun Forge.aNonBase64Separator(): ByteArray {
        var separator = aString()
        while (isBase64Char(separator)) {
            separator = aString()
        }
        return separator.toByteArray()
    }

    // endregion

    companion object {
        private val EMPTY_BYTE_ARRAY = ByteArray(0)
        private val BASE_64_CHARS =
            (('A'..'Z') + ('a'..'z') + ('0'..'9') + arrayOf('+', '/', '=')).toSet()

        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
