/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import android.util.Log
import com.datadog.android.core.internal.persistence.file.EventMeta
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.utils.ERROR_WITH_TELEMETRY_LEVEL
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonParseException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class PlainBatchFileReaderWriterTest {

    private lateinit var testedReaderWriter: PlainBatchFileReaderWriter

    @StringForgery(regex = "([a-z]+)-([a-z]+)")
    lateinit var fakeSrcDirName: String

    @StringForgery(regex = "([a-z]+)-([a-z]+)")
    lateinit var fakeDstDirName: String

    @TempDir
    lateinit var fakeRootDirectory: File

    private lateinit var fakeSrcDir: File
    private lateinit var fakeDstDir: File

    @BeforeEach
    fun `set up`() {
        fakeSrcDir = File(fakeRootDirectory, fakeSrcDirName)
        fakeDstDir = File(fakeRootDirectory, fakeDstDirName)
        testedReaderWriter = PlainBatchFileReaderWriter(Logger(logger.mockSdkLogHandler))
    }

    // region writeData

    @Test
    fun `𝕄 write data in empty file 𝕎 writeData() {append=false}`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()
        val contentBytes = content.toByteArray()

        // When
        val result = testedReaderWriter.writeData(
            file,
            contentBytes,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(encode(contentBytes))
    }

    @Test
    fun `𝕄 write data in empty file  𝕎 writeData() {append=true}`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()
        val contentBytes = content.toByteArray()

        // When
        val result = testedReaderWriter.writeData(
            file,
            contentBytes,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(encode(contentBytes))
    }

    @Test
    fun `𝕄 overwrite data in non empty file 𝕎 writeData() {append=false}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(previousContent)
        val contentBytes = content.toByteArray()

        // When
        val result = testedReaderWriter.writeData(
            file,
            contentBytes,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(encode(contentBytes))
    }

    @Test
    fun `𝕄 append data in non empty file 𝕎 writeData() {append=true}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val previousData = previousContent.toByteArray()
        file.writeBytes(encode(previousData))
        val contentBytes = content.toByteArray()

        // When
        val result = testedReaderWriter.writeData(
            file,
            contentBytes,
            append = true
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists()
            .hasBinaryContent(
                encode(previousData) + encode(contentBytes)
            )
    }

    @Test
    fun `𝕄 return false and warn 𝕎 writeData() {parent dir does not exist}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @BoolForgery append: Boolean
    ) {
        // Given
        assumeFalse(fakeSrcDir.exists())
        val file = File(fakeSrcDir, fileName)

        // When
        val result = testedReaderWriter.writeData(
            file,
            content.toByteArray(),
            append = append
        )

        // Then
        assertThat(result).isFalse()
        assertThat(file).doesNotExist()
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(PlainBatchFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return false and warn 𝕎 writeData() {file is not file}`(
        @StringForgery fileName: String,
        @StringForgery content: String,
        @BoolForgery append: Boolean
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.mkdirs()

        // When
        val result = testedReaderWriter.writeData(
            file,
            content.toByteArray(),
            append = append
        )

        // Then
        assertThat(result).isFalse()
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(PlainBatchFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    // endregion

    // region readData

    @Test
    fun `𝕄 return empty list and warn 𝕎 readData() {file does not exist}`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).isEmpty()
        assertThat(file).doesNotExist()
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(PlainBatchFileReaderWriter.ERROR_READ.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return empty list and warn 𝕎 readData() {file is not file}`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).isEmpty()
        verify(logger.mockSdkLogHandler).handleLog(
            eq(ERROR_WITH_TELEMETRY_LEVEL),
            eq(PlainBatchFileReaderWriter.ERROR_READ.format(Locale.US, file.path)),
            any(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return empty list and warn user 𝕎 readData() { corrupted data }`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeBytes(content.toByteArray())

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).isEmpty()
        verify(logger.mockDevLogHandler).handleLog(
            Log.ERROR,
            PlainBatchFileReaderWriter.WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path)
        )
        verify(logger.mockSdkLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            PlainBatchFileReaderWriter.WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path)
        )
    }

    @Test
    fun `𝕄 return valid events read so far and warn 𝕎 readData() { stream cutoff }`(
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val events = forge.aList {
            aString().toByteArray()
        }

        file.writeBytes(
            events.mapIndexed { index, bytes ->
                if (index == events.lastIndex) {
                    encode(bytes)
                        .let { it.take(forge.anInt(min = 1, max = it.size - 1)) }
                        .toByteArray()
                } else {
                    encode(bytes)
                }
            }.reduce { acc, bytes -> acc + bytes }
        )

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).containsExactlyElementsOf(events.take(events.size - 1))
    }

    @Test
    fun `𝕄 return valid events read and warn 𝕎 readData() { malformed meta }`(
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val events = forge.aList {
            aString().toByteArray()
        }

        file.writeBytes(
            events.map {
                encode(it)
            }.reduce { acc, bytes -> acc + bytes }
        )

        val malformedMetaIndex = forge.anInt(min = 0, max = events.size)
        testedReaderWriter = PlainBatchFileReaderWriter(
            Logger(logger.mockSdkLogHandler),
            metaParser = object : (ByteArray) -> EventMeta {
                // in case of malformed meta we should drop corresponding event and continue reading
                var invocations = 0

                override fun invoke(metaBytes: ByteArray): EventMeta {
                    return if (invocations == malformedMetaIndex) {
                        invocations++
                        throw JsonParseException(forge.aString())
                    } else {
                        invocations++
                        EventMeta.fromBytes(metaBytes)
                    }
                }
            }
        )

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).containsExactlyElementsOf(events.minus(events[malformedMetaIndex]))

        verify(logger.mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(PlainBatchFileReaderWriter.ERROR_FAILED_META_PARSE),
            isA<JsonParseException>(),
            eq(emptyMap()),
            eq(emptySet()),
            isNull()
        )
    }

    @Test
    fun `𝕄 return valid events read so far and warn 𝕎 readData() { unexpected block type }`(
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val events = forge.aList {
            aString().toByteArray()
        }

        val badEventIndex = forge.anInt(min = 0, max = events.size)
        file.writeBytes(
            events.mapIndexed { index, item ->
                val metaBytes = metaBytesAsTlv()
                val eventBytes = dataBytesAsTlv(item)
                if (index == badEventIndex) {
                    val isBadBlockTypeInMeta = forge.aBool()
                    if (isBadBlockTypeInMeta) {
                        metaBytes.apply {
                            set(
                                1,
                                forge.anElementFrom(
                                    0,
                                    forge.anInt(min = 2, max = Byte.MAX_VALUE + 1)
                                ).toByte()
                            )
                        } + eventBytes
                    } else {
                        metaBytes + eventBytes.apply {
                            set(1, forge.anInt(min = 0, max = Byte.MAX_VALUE + 1).toByte())
                        }
                    }
                } else {
                    metaBytes + eventBytes
                }
            }.reduce { acc, bytes -> acc + bytes }
        )

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).containsExactlyElementsOf(events.take(badEventIndex))

        verify(logger.mockDevLogHandler).handleLog(
            Log.ERROR,
            PlainBatchFileReaderWriter.WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path)
        )
        verify(logger.mockSdkLogHandler).handleLog(
            ERROR_WITH_TELEMETRY_LEVEL,
            PlainBatchFileReaderWriter.WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path)
        )
    }

    @Test
    fun `𝕄 return file content 𝕎 readData() { single event }`(
        @StringForgery fileName: String,
        @StringForgery event: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val eventBytes = event.toByteArray()
        file.writeBytes(encode(eventBytes))

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).containsExactlyElementsOf(listOf(eventBytes))
    }

    @Test
    fun `𝕄 return file content 𝕎 readData() { multiple events }`(
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val events = forge.aList {
            aString().toByteArray()
        }
        file.writeBytes(events.map { encode(it) }.reduce { acc, bytes -> acc + bytes })

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).containsExactlyElementsOf(events)
    }

    // endregion

    // region writeData + readData

    @Test
    fun `𝕄 return file content 𝕎 writeData + readData() { append = false }`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        // When
        val writeResult = testedReaderWriter.writeData(file, content.toByteArray(), false)
        val readResult = testedReaderWriter.readData(file)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).containsExactlyElementsOf(listOf(content.toByteArray()))
    }

    @Test
    fun `𝕄 return file content 𝕎 writeData + readData() { append = true }`(
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        val data = forge.aList {
            aString().toByteArray()
        }

        // When
        var writeResult = true
        data.forEach {
            writeResult = writeResult && testedReaderWriter.writeData(
                file,
                it,
                true
            )
        }
        val readResult = testedReaderWriter.readData(file)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).containsExactlyElementsOf(data)
    }

    // endregion

    // region private

    // Encoding specification is as following:
    // +-  2 bytes -+-   4 bytes   -+- n bytes -|
    // | block type | data size (n) |    data   |
    // +------------+---------------+-----------+
    // where block type is 0x00 for event, 0x01 for data
    private fun encode(data: ByteArray): ByteArray {
        return metaBytesAsTlv() + dataBytesAsTlv(data)
    }

    private fun metaBytesAsTlv(): ByteArray {
        val metaBytes = EventMeta().asBytes

        return ByteBuffer.allocate(6 + metaBytes.size)
            .putShort(0x01)
            .putInt(metaBytes.size)
            .put(metaBytes)
            .array()
    }

    private fun dataBytesAsTlv(data: ByteArray): ByteArray {
        return ByteBuffer.allocate(6 + data.size)
            .putShort(0x00)
            .putInt(data.size)
            .put(data)
            .array()
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
