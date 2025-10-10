/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
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

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var fakeSrcDir: File
    private lateinit var fakeDstDir: File

    @BeforeEach
    fun `set up`() {
        fakeSrcDir = File(fakeRootDirectory, fakeSrcDirName)
        fakeDstDir = File(fakeRootDirectory, fakeDstDirName)
        testedReaderWriter = PlainBatchFileReaderWriter(mockInternalLogger)
    }

    // region writeData

    @Test
    fun `M write data in empty file W writeData() {append=false}`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedReaderWriter.writeData(
            file,
            event,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(encode(event))
    }

    @Test
    fun `M write data in empty file  W writeData() {append=true}`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedReaderWriter.writeData(
            file,
            event,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(encode(event))
    }

    @Test
    fun `M overwrite data in non empty file W writeData() {append=false}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(previousContent)

        // When
        val result = testedReaderWriter.writeData(
            file,
            event,
            append = false
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(encode(event))
    }

    @Test
    fun `M append data in non empty file W writeData() {append=true}`(
        @StringForgery fileName: String,
        @Forgery previousEvent: RawBatchEvent,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeBytes(encode(previousEvent))

        // When
        val result = testedReaderWriter.writeData(
            file,
            event,
            append = true
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists()
            .hasBinaryContent(
                encode(previousEvent) + encode(event)
            )
    }

    @Test
    fun `M return false and warn W writeData() {parent dir does not exist}`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent,
        @BoolForgery append: Boolean
    ) {
        // Given
        assumeFalse(fakeSrcDir.exists())
        val file = File(fakeSrcDir, fileName)

        // When
        val result = testedReaderWriter.writeData(
            file,
            event,
            append = append
        )

        // Then
        assertThat(result).isFalse()
        assertThat(file).doesNotExist()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER),
            PlainBatchFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path),
            FileNotFoundException::class.java
        )
    }

    @Test
    fun `M return false and warn W writeData() {file is not file}`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent,
        @BoolForgery append: Boolean
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.mkdirs()

        // When
        val result = testedReaderWriter.writeData(
            file,
            event,
            append = append
        )

        // Then
        assertThat(result).isFalse()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER),
            PlainBatchFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path),
            FileNotFoundException::class.java
        )
    }

    // endregion

    // region readData

    @Test
    fun `M return empty list and warn W readData() {file does not exist}`(
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
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.ERROR_READ.format(Locale.US, file.path),
            FileNotFoundException::class.java
        )
    }

    @Test
    fun `M return empty list and warn W readData() {file is not file}`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.ERROR_READ.format(Locale.US, file.path),
            FileNotFoundException::class.java
        )
    }

    @Test
    fun `M return empty list and warn user W readData() { corrupted data }`(
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
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path)
        )
    }

    @Test
    fun `M return valid events read so far and warn W readData() { stream cutoff }`(
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val events = forge.aList {
            RawBatchEvent(aString().toByteArray())
        }

        file.writeBytes(
            events.mapIndexed { index, event ->
                if (index == events.lastIndex) {
                    encode(event)
                        .let { it.take(forge.anInt(min = 1, max = it.size - 1)) }
                        .toByteArray()
                } else {
                    encode(event)
                }
            }.reduce { acc, bytes -> acc + bytes }
        )

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).containsExactlyElementsOf(events.take(events.size - 1))
    }

    @Test
    fun `M return valid events read so far and warn W readData() { unexpected block type }`(
        @StringForgery fileName: String,
        @Forgery events: List<RawBatchEvent>,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        val badEventIndex = forge.anInt(min = 0, max = events.size)
        file.writeBytes(
            events.mapIndexed { index, item ->
                val metaBytes = metaBytesAsTlv(item.metadata)
                val eventBytes = dataBytesAsTlv(item.data)
                if (index == badEventIndex) {
                    val isBadBlockTypeInMeta = forge.aBool()
                    if (isBadBlockTypeInMeta) {
                        metaBytes.apply {
                            set(
                                1,
                                // first 2 bytes of meta should be 1, so to generate
                                // wrong block we need any value != 1
                                forge.anElementFrom(
                                    0,
                                    forge.anInt(min = 2, max = Byte.MAX_VALUE + 1)
                                ).toByte()
                            )
                        } + eventBytes
                    } else {
                        // first 2 bytes of event should be 0, so to generate
                        // wrong block we need any value != 0
                        metaBytes + eventBytes.apply {
                            set(1, forge.anInt(min = 1, max = Byte.MAX_VALUE + 1).toByte())
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

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path)
        )
    }

    @Test
    fun `M return file content W readData() { single event }`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeBytes(encode(event))

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).containsExactlyElementsOf(listOf(event))
    }

    @Test
    fun `M return file content W readData() { multiple events }`(
        @StringForgery fileName: String,
        @Forgery events: List<RawBatchEvent>
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeBytes(events.map { encode(it) }.reduce { acc, bytes -> acc + bytes })

        // When
        val result = testedReaderWriter.readData(file)

        // Then
        assertThat(result).containsExactlyElementsOf(events)
    }

    // endregion

    // region writeData + readData

    @Test
    fun `M return file content W writeData + readData() { append = false }`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        // When
        val writeResult = testedReaderWriter.writeData(file, event, false)
        val readResult = testedReaderWriter.readData(file)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).containsExactlyElementsOf(listOf(event))
    }

    @Test
    fun `M return file content W writeData + readData() { append = true }`(
        @StringForgery fileName: String,
        @Forgery events: List<RawBatchEvent>
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        // When
        var writeResult = true
        events.forEach {
            writeResult = writeResult && testedReaderWriter.writeData(
                file,
                it,
                true
            )
        }
        val readResult = testedReaderWriter.readData(file)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).containsExactlyElementsOf(events)
    }

    // endregion

    // region Reading older formats

    @Test
    fun `M return file content W readData() { 2-2-0 and earlier }`() {
        // 2.3.0 release is changing the way we are handling metadata, so we need to make sure
        // that we are backward compatible with data written earlier

        // Given
        val file = File(
            checkNotNull(javaClass.classLoader)
                .getResource("logs-batch-2.2.0-and-earlier")
                .file
        )

        // When
        val readResult = testedReaderWriter.readData(file)

        // Then
        assertThat(readResult).hasSize(2)
        assertThat(readResult).satisfies { it.all { it.data.isNotEmpty() } }
        assertThat(readResult).satisfies { it.all { it.metadata.isEmpty() } }
    }

    // endregion

    // region private

    // Encoding specification is as following:
    // +-  2 bytes -+-   4 bytes   -+- n bytes -|
    // | block type | data size (n) |    data   |
    // +------------+---------------+-----------+
    // where block type is 0x00 for event, 0x01 for data
    private fun encode(event: RawBatchEvent): ByteArray {
        return metaBytesAsTlv(event.metadata) + dataBytesAsTlv(event.data)
    }

    private fun metaBytesAsTlv(meta: ByteArray): ByteArray {
        return ByteBuffer.allocate(6 + meta.size)
            .putShort(0x01)
            .putInt(meta.size)
            .put(meta)
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
}
