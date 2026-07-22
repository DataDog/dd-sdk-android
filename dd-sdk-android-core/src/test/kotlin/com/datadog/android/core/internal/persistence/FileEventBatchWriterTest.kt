/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.persistence.FileEventBatchWriter.Companion.ERROR_LARGE_DATA
import com.datadog.android.core.internal.persistence.FileEventBatchWriter.Companion.NO_BATCH_FILE_AVAILABLE
import com.datadog.android.core.internal.persistence.FileEventBatchWriter.Companion.WARNING_METADATA_WRITE_FAILED
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.internal.telemetry.TelemetryContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FileEventBatchWriterTest {

    private lateinit var testedWriter: EventBatchWriter

    @Mock
    lateinit var mockBatchWriter: BatchFileReaderWriter

    @Mock
    lateinit var mockMetaReaderWriter: FileReaderWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFilePersistenceConfig: FilePersistenceConfig

    @Mock
    lateinit var mockBatchWriteEventListener: BatchWriteEventListener

    @Mock
    lateinit var mockFileOrchestrator: FileOrchestrator

    @Forgery
    lateinit var fakeBatchFile: File

    @Forgery
    lateinit var fakeBatchMetadataFile: File

    @Forgery
    lateinit var fakeEventType: EventType

    @StringForgery
    lateinit var fakeFeatureName: String

    private lateinit var fakeTelemetryContext: TelemetryContext

    @BeforeEach
    fun `set up`() {
        fakeTelemetryContext = TelemetryContext(featureName = fakeFeatureName)
        testedWriter = FileEventBatchWriter(
            featureName = fakeFeatureName,
            fileOrchestrator = mockFileOrchestrator,
            eventsWriter = mockBatchWriter,
            metadataReaderWriter = mockMetaReaderWriter,
            filePersistenceConfig = mockFilePersistenceConfig,
            internalLogger = mockInternalLogger,
            batchWriteEventListener = mockBatchWriteEventListener
        )
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn Long.MAX_VALUE
        whenever(mockFileOrchestrator.getWritableFile(any())) doReturn fakeBatchFile
        whenever(mockFileOrchestrator.getMetadataFile(fakeBatchFile)) doReturn fakeBatchMetadataFile
        whenever(mockBatchWriter.serializeToBytes(any(), any())) doAnswer {
            it.getArgument<RawBatchEvent>(0).data
        }
    }

    // region write

    @Test
    fun `M write event W write()`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(mockMetaReaderWriter.readData(fakeBatchMetadataFile, fakeTelemetryContext)) doReturn serializedMetadata
        whenever(
            mockBatchWriter.writeBinaryData(fakeBatchFile, batchEvent.data, true, fakeTelemetryContext)
        ) doReturn true

        // When
        val result = testedWriter.write(batchEvent, serializedMetadata, fakeEventType)

        // Then
        assertThat(result).isTrue()

        verify(mockBatchWriter).serializeToBytes(batchEvent, fakeTelemetryContext)
        verify(mockBatchWriter).writeBinaryData(
            fakeBatchFile,
            batchEvent.data,
            append = true,
            fakeTelemetryContext
        )
        verify(mockMetaReaderWriter).writeData(
            fakeBatchMetadataFile,
            serializedMetadata,
            append = false,
            fakeTelemetryContext
        )

        verifyNoMoreInteractions(
            mockBatchWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `M do nothing W write() {empty array}`(
        @StringForgery batchMetadata: String
    ) {
        // Given
        val rawBatchEvent = RawBatchEvent(data = ByteArray(0))
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)

        // When
        val result = testedWriter.write(rawBatchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isTrue

        verifyNoInteractions(
            mockBatchWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `M return false W write() {batch file cannot be allocated}`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(mockFileOrchestrator.getWritableFile(any())) doReturn null

        // When
        val result = testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isFalse

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            NO_BATCH_FILE_AVAILABLE,
            additionalProperties = fakeTelemetryContext.asAttributesMap(bytesLost = batchEvent.data.size)
        )
    }

    @Test
    fun `M return false W write() {serialization returns null}`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(mockBatchWriter.serializeToBytes(batchEvent, fakeTelemetryContext)) doReturn null

        // When
        val result = testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isFalse
        verify(mockFileOrchestrator, never()).getWritableFile(any())
        verifyNoInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `M return false W write() {item is too big}`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val maxItemSize = batchEvent.data.size - 1
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn maxItemSize.toLong()

        // When
        val result = testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isFalse

        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            ERROR_LARGE_DATA.format(Locale.US, batchEvent.data.size, maxItemSize),
            additionalProperties = fakeTelemetryContext.asAttributesMap(batchEvent.data.size)
        )
    }

    @Test
    fun `M return false W write() {write operation failed}`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(
            mockBatchWriter.writeBinaryData(fakeBatchFile, batchEvent.data, true, fakeTelemetryContext)
        ) doReturn false

        // When
        val result = testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isFalse
    }

    @Test
    fun `M not write metadata W write() {no available file}`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        whenever(mockFileOrchestrator.getMetadataFile(fakeBatchFile)) doReturn null

        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)

        whenever(
            mockBatchWriter.writeBinaryData(fakeBatchFile, batchEvent.data, true, fakeTelemetryContext)
        ) doReturn true

        // When
        val result = testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isTrue

        verifyNoInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `M not write metadata W write() {null or empty metadata}`(
        @Forgery batchEvent: RawBatchEvent,
        forge: Forge
    ) {
        // Given
        whenever(
            mockBatchWriter.writeBinaryData(fakeBatchFile, batchEvent.data, true, fakeTelemetryContext)
        ) doReturn true

        // When
        val result = testedWriter.write(batchEvent, forge.aNullable { ByteArray(0) }, fakeEventType)

        // Then
        assertThat(result).isTrue

        verify(mockBatchWriter).serializeToBytes(batchEvent, fakeTelemetryContext)
        verify(mockBatchWriter).writeBinaryData(
            fakeBatchFile,
            batchEvent.data,
            append = true,
            fakeTelemetryContext
        )
        verifyNoMoreInteractions(mockBatchWriter)
        verifyNoInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `M not write metadata W write() {item is too big}`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val maxItemSize = batchEvent.data.size - 1
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn maxItemSize.toLong()

        // When
        val result = testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isFalse

        verifyNoInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `M not write metadata W write() {write operation failed}`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(
            mockBatchWriter.writeBinaryData(fakeBatchFile, batchEvent.data, true, fakeTelemetryContext)
        ) doReturn false

        // When
        val result = testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isFalse

        verifyNoInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `M log warning W write() {write metadata failed}`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(
            mockBatchWriter.writeBinaryData(fakeBatchFile, batchEvent.data, true, fakeTelemetryContext)
        ) doReturn true
        whenever(
            mockMetaReaderWriter.writeData(
                fakeBatchMetadataFile,
                serializedBatchMetadata,
                false,
                fakeTelemetryContext
            )
        ) doReturn false

        // When
        val result = testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        assertThat(result).isTrue()

        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            WARNING_METADATA_WRITE_FAILED.format(
                Locale.US,
                fakeBatchMetadataFile
            ),
            additionalProperties = fakeTelemetryContext.asAttributesMap(serializedBatchMetadata.size)
        )
    }

    // endregion

    // region benchmark

    @Test
    fun `M send onWriteEvent W write`(
        @Forgery batchEvent: RawBatchEvent,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(
            mockBatchWriter.writeBinaryData(fakeBatchFile, batchEvent.data, true, fakeTelemetryContext)
        ) doReturn true
        whenever(
            mockMetaReaderWriter.writeData(
                fakeBatchMetadataFile,
                serializedBatchMetadata,
                false,
                fakeTelemetryContext
            )
        ) doReturn false

        // When
        testedWriter.write(batchEvent, serializedBatchMetadata, fakeEventType)

        // Then
        verify(mockBatchWriteEventListener).onWriteEvent(batchEvent.data.size.toLong())
    }

    // endregion
}
