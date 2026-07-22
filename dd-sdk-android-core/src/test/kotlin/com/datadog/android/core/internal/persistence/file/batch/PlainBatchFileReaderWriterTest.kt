/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
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

    @StringForgery
    lateinit var fakeFeatureName: String

    @Forgery
    lateinit var fakeTelemetryContext: TelemetryContext

    private lateinit var fakeSrcDir: File
    private lateinit var fakeDstDir: File

    @BeforeEach
    fun `set up`() {
        fakeSrcDir = File(fakeRootDirectory, fakeSrcDirName)
        fakeDstDir = File(fakeRootDirectory, fakeDstDirName)
        testedReaderWriter = PlainBatchFileReaderWriter(mockInternalLogger)
    }

    // region writeBinaryData

    @Test
    fun `M write bytes in empty file W writeBinaryData() {append=false}`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedReaderWriter.writeBinaryData(
            file,
            encode(event),
            append = false,
            telemetryContext = fakeTelemetryContext
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(encode(event))
    }

    @Test
    fun `M overwrite bytes in non empty file W writeBinaryData() {append=false}`(
        @StringForgery fileName: String,
        @StringForgery previousContent: String,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeText(previousContent)

        // When
        val result = testedReaderWriter.writeBinaryData(
            file,
            encode(event),
            append = false,
            telemetryContext = fakeTelemetryContext
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists().hasBinaryContent(encode(event))
    }

    @Test
    fun `M append bytes in non empty file W writeBinaryData() {append=true}`(
        @StringForgery fileName: String,
        @Forgery previousEvent: RawBatchEvent,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.writeBytes(encode(previousEvent))

        // When
        val result = testedReaderWriter.writeBinaryData(
            file,
            encode(event),
            append = true,
            telemetryContext = fakeTelemetryContext
        )

        // Then
        assertThat(result).isTrue()
        assertThat(file).exists()
            .hasBinaryContent(
                encode(previousEvent) + encode(event)
            )
    }

    @Test
    fun `M return false and warn W writeBinaryData() {parent dir does not exist}`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent,
        @BoolForgery append: Boolean
    ) {
        // Given
        assumeFalse(fakeSrcDir.exists())
        val file = File(fakeSrcDir, fileName)

        // When
        val result = testedReaderWriter.writeBinaryData(
            file,
            encode(event),
            append = append,
            telemetryContext = fakeTelemetryContext
        )

        // Then
        assertThat(result).isFalse()
        assertThat(file).doesNotExist()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path),
            FileNotFoundException::class.java,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = encode(event).size)
        )
    }

    @Test
    fun `M return false and warn W writeBinaryData() {file is not file}`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent,
        @BoolForgery append: Boolean
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.mkdirs()

        // When
        val result = testedReaderWriter.writeBinaryData(
            file,
            encode(event),
            append = append,
            telemetryContext = fakeTelemetryContext
        )

        // Then
        assertThat(result).isFalse()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path),
            FileNotFoundException::class.java,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = encode(event).size)
        )
    }

    @Test
    fun `M roll back partial write W writeBinaryData() {channel write throws IOException}`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent,
        @BoolForgery append: Boolean,
        @LongForgery(min = 0L, max = 1024L) fakeFileLength: Long,
        @StringForgery errorMessage: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val expectedRollbackLength = if (append) fakeFileLength else 0L
        val mockChannel = mock<FileChannel>()
        val mockLock = mock<FileLock>()
        whenever(mockChannel.size()) doReturn fakeFileLength
        whenever(mockChannel.lock()) doReturn mockLock
        whenever(mockChannel.write(any<ByteBuffer>())) doThrow IOException(errorMessage)

        Mockito.mockConstruction(RandomAccessFile::class.java) { mock, _ ->
            whenever(mock.channel) doReturn mockChannel
        }.use {
            // When
            val result = testedReaderWriter.writeBinaryData(
                file,
                encode(event),
                append = append,
                telemetryContext = fakeTelemetryContext
            )

            // Then
            assertThat(result).isFalse()
            // initial alignment truncate + rollback truncate after IOException
            verify(mockChannel, times(2)).truncate(expectedRollbackLength)
            mockInternalLogger.verifyLog(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                PlainBatchFileReaderWriter.ERROR_WRITE.format(Locale.US, file.path),
                IOException::class.java,
                additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = encode(event).size)
            )
        }
    }

    // endregion

    // region serializeToBytes

    @Test
    fun `M return TLV encoded bytes W serializeToBytes()`(
        @Forgery event: RawBatchEvent
    ) {
        // When
        val result = testedReaderWriter.serializeToBytes(event, fakeTelemetryContext)

        // Then
        assertThat(result).isEqualTo(encode(event))
    }

    // endregion

    // region readData

    @Test
    fun `M return empty list W readData() { empty file }`(
        @StringForgery(regex = "[a-z]+") fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        file.createNewFile()

        // When
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M return empty list and warn W readData() {file does not exist}`(
        @StringForgery fileName: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        assumeFalse(file.exists())

        // When
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        assertThat(file).doesNotExist()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.ERROR_READ.format(Locale.US, file.path),
            FileNotFoundException::class.java,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = 0)
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
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.ERROR_READ.format(Locale.US, file.path),
            FileNotFoundException::class.java,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = 0)
        )
    }

    @Test
    fun `M return empty list and warn user W readData() { corrupted data }`(
        @StringForgery fileName: String,
        @StringForgery content: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val contentBytes = content.toByteArray()
        file.writeBytes(contentBytes)

        // When
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(result).isEmpty()
        // whole file is unreadable -> file-level warning to USER+TELEMETRY with the full size dropped
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path),
            additionalProperties = droppedBytesTelemetry(contentBytes.size)
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
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(result).containsExactlyElementsOf(events.take(events.size - 1))
    }

    @Test
    fun `M return valid events and report telemetry W readData() { data block shorter than declared }`(
        @StringForgery fileName: String,
        @Forgery validEvent: RawBatchEvent,
        @StringForgery fakeCorruptedMetadata: String,
        @StringForgery fakeCorruptedEventData: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val corruptedMetadataBytes = metaBytesAsTlv(fakeCorruptedMetadata.toByteArray())
        val corruptedEventData = fakeCorruptedEventData.toByteArray()
        // header declares more data than is actually present -> partial data read (actual != -1)
        val declaredEventDataSize = corruptedEventData.size + forge.anInt(min = 1, max = 128)
        val corruptedEventBytes = ByteBuffer.allocate(
            PlainBatchFileReaderWriter.HEADER_SIZE_BYTES + corruptedEventData.size
        )
            .putShort(0x00)
            .putInt(declaredEventDataSize)
            .put(corruptedEventData)
            .array()
        file.writeBytes(encode(validEvent) + corruptedMetadataBytes + corruptedEventBytes)

        // When
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(result).containsExactly(validEvent)
        // the corrupted data block is read up to the declared (but absent) tail, consuming the
        // rest of the file, so `remaining` lands exactly on 0 and the file-level warning stays silent
        val droppedBytes = corruptedMetadataBytes.size + corruptedEventBytes.size
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Number of bytes read for operation='Block(EVENT):Data read' doesn't match with expected: " +
                "expected=$declaredEventDataSize, actual=${corruptedEventData.size}",
            additionalProperties = droppedBytesTelemetry(droppedBytes)
        )
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
        val isBadBlockTypeInMeta = forge.aBool()
        // block type is the 2nd header byte (big-endian short, high byte 0x00); META identifier
        // is 1 and EVENT identifier is 0, so pick any value that differs from the expected one
        val badBlockType = if (isBadBlockTypeInMeta) {
            forge.anElementFrom(0, forge.anInt(min = 2, max = Byte.MAX_VALUE + 1))
        } else {
            forge.anInt(min = 1, max = Byte.MAX_VALUE + 1)
        }
        file.writeBytes(
            events.mapIndexed { index, item ->
                val metaBytes = metaBytesAsTlv(item.metadata)
                val eventBytes = dataBytesAsTlv(item.data)
                when {
                    index == badEventIndex -> if (isBadBlockTypeInMeta) {
                        metaBytes.apply { set(1, badBlockType.toByte()) } + eventBytes
                    } else {
                        metaBytes + eventBytes.apply { set(1, badBlockType.toByte()) }
                    }
                    else -> metaBytes + eventBytes
                }
            }.reduce { acc, bytes -> acc + bytes }
        )

        // When
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(result).containsExactlyElementsOf(events.take(badEventIndex))

        // an unexpected block type is a block-level (MAINTAINER) diagnostic, and the file-level
        // (USER) summary warning must report the same dropped byte count, not a partially
        // decremented one, since everything from the bad block to EOF is discarded
        val expectedBlockName = if (isBadBlockTypeInMeta) "META" else "EVENT"
        val expectedIdentifier = if (isBadBlockTypeInMeta) 1 else 0
        val droppedBytes =
            events.drop(badEventIndex).sumOf { metaBytesAsTlv(it.metadata).size + dataBytesAsTlv(it.data).size }
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unexpected block type identifier=$badBlockType met," +
                " was expecting $expectedBlockName($expectedIdentifier)",
            additionalProperties = droppedBytesTelemetry(droppedBytes)
        )
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            PlainBatchFileReaderWriter.WARNING_NOT_ALL_DATA_READ.format(Locale.US, file.path),
            additionalProperties = droppedBytesTelemetry(droppedBytes)
        )
    }

    @Test
    fun `M report telemetry W readData() { unexpected EOF on data block }`(
        @StringForgery fileName: String,
        @Forgery validEvent: RawBatchEvent,
        @StringForgery fakeMetadata: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val metaBytes = metaBytesAsTlv(fakeMetadata.toByteArray())
        // valid event header declaring data, but no data bytes follow -> EOF (actual == -1)
        val eventHeaderOnly = ByteBuffer.allocate(PlainBatchFileReaderWriter.HEADER_SIZE_BYTES)
            .putShort(0x00)
            .putInt(forge.anInt(min = 1, max = 128))
            .array()
        file.writeBytes(encode(validEvent) + metaBytes + eventHeaderOnly)

        // When
        testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unexpected EOF at the operation=Block(EVENT):Data read",
            additionalProperties = droppedBytesTelemetry(
                metaBytes.size + eventHeaderOnly.size
            )
        )
    }

    @Test
    fun `M report telemetry W readData() { IOException while reading }`(
        @StringForgery(regex = "[a-z]+") fileName: String,
        @StringForgery(regex = "[a-z]+") content: String,
        @StringForgery errorMessage: String
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val contentBytes = content.toByteArray()
        // real, non-empty file so inputLength > 0 and the read actually starts
        file.writeBytes(contentBytes)

        Mockito.mockConstruction(FileInputStream::class.java) { mock, _ ->
            whenever(mock.read(any(), any(), any())) doThrow IOException(errorMessage)
        }.use {
            // When
            val result = testedReaderWriter.readData(file, fakeTelemetryContext)

            // Then
            assertThat(result).isEmpty()
            mockInternalLogger.verifyLog(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                PlainBatchFileReaderWriter.ERROR_READ.format(Locale.US, file.path),
                IOException::class.java,
                additionalProperties = droppedBytesTelemetry(contentBytes.size)
            )
        }
    }

    @Test
    fun `M report telemetry W readData() { data block larger than declared }`(
        @StringForgery fileName: String,
        @StringForgery fakeMetadata: String,
        @StringForgery fakeEventData: String,
        forge: Forge
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)
        val metaBytes = metaBytesAsTlv(fakeMetadata.toByteArray())
        val eventData = fakeEventData.toByteArray()
        // header under-declares the payload: the reader consumes `eventData.size` bytes as the
        // event and the surplus (< header size) is then misread as the next block's header
        val surplus = forge.anInt(min = 1, max = PlainBatchFileReaderWriter.HEADER_SIZE_BYTES)
        val oversizedEventBytes = ByteBuffer.allocate(
            PlainBatchFileReaderWriter.HEADER_SIZE_BYTES + eventData.size + surplus
        )
            .putShort(0x00)
            .putInt(eventData.size)
            .put(eventData + ByteArray(surplus))
            .array()
        file.writeBytes(metaBytes + oversizedEventBytes)

        // When
        testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then: surplus bytes are misread as the next META header -> block-level (MAINTAINER) mismatch
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Number of bytes read for operation='Block(META): Header read' doesn't match with expected: " +
                "expected=${PlainBatchFileReaderWriter.HEADER_SIZE_BYTES}, actual=$surplus",
            additionalProperties = droppedBytesTelemetry(surplus)
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
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

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
        val result = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(result).containsExactlyElementsOf(events)
    }

    // endregion

    // region writeBinaryData + readData

    @Test
    fun `M return file content W writeBinaryData + readData() { append = false }`(
        @StringForgery fileName: String,
        @Forgery event: RawBatchEvent
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        // When
        val writeResult = testedReaderWriter.writeBinaryData(
            file,
            testedReaderWriter.serializeToBytes(event, fakeTelemetryContext),
            append = false,
            telemetryContext = fakeTelemetryContext
        )
        val readResult = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(writeResult).isTrue()
        assertThat(readResult).containsExactlyElementsOf(listOf(event))
    }

    @Test
    fun `M return file content W writeBinaryData + readData() { append = true }`(
        @StringForgery fileName: String,
        @Forgery events: List<RawBatchEvent>
    ) {
        // Given
        val file = File(fakeRootDirectory, fileName)

        // When
        var writeResult = true
        events.forEach {
            writeResult = writeResult && testedReaderWriter.writeBinaryData(
                file,
                testedReaderWriter.serializeToBytes(it, fakeTelemetryContext),
                append = true,
                fakeTelemetryContext
            )
        }
        val readResult = testedReaderWriter.readData(file, fakeTelemetryContext)

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
        val readResult = testedReaderWriter.readData(file, fakeTelemetryContext)

        // Then
        assertThat(readResult).hasSize(2)
        assertThat(readResult).allMatch { it.data.isNotEmpty() }
        assertThat(readResult).allMatch { it.metadata.isNotEmpty() }
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

    private fun droppedBytesTelemetry(droppedBytes: Int): Map<String, Any> =
        fakeTelemetryContext.asAttributesMap(bytesLost = droppedBytes)

    // endregion
}
