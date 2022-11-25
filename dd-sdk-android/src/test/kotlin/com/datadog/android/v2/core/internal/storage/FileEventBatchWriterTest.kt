/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.internal.storage.FileEventBatchWriter.Companion.ERROR_LARGE_DATA
import com.datadog.android.v2.core.internal.storage.FileEventBatchWriter.Companion.WARNING_METADATA_WRITE_FAILED
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
    lateinit var mockBatchWriter: FileWriter

    @Mock
    lateinit var mockMetaReaderWriter: FileReaderWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFilePersistenceConfig: FilePersistenceConfig

    @Forgery
    lateinit var fakeBatchFile: File

    @Forgery
    lateinit var fakeBatchMetadataFile: File

    @BeforeEach
    fun `set up`() {
        testedWriter = FileEventBatchWriter(
            batchFile = fakeBatchFile,
            metadataFile = fakeBatchMetadataFile,
            eventsWriter = mockBatchWriter,
            metadataReaderWriter = mockMetaReaderWriter,
            filePersistenceConfig = mockFilePersistenceConfig,
            internalLogger = mockInternalLogger
        )
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn Long.MAX_VALUE
    }

    // region write

    @Test
    fun `𝕄 write event 𝕎 write()`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(mockMetaReaderWriter.readData(fakeBatchMetadataFile)) doReturn serializedMetadata
        whenever(
            mockBatchWriter.writeData(fakeBatchFile, serializedData, true)
        ) doReturn true

        // When
        val result = testedWriter.write(serializedData, serializedMetadata)

        // Then
        assertThat(result).isTrue()

        verify(mockBatchWriter).writeData(
            fakeBatchFile,
            serializedData,
            append = true
        )
        verify(mockMetaReaderWriter).writeData(
            fakeBatchMetadataFile,
            serializedMetadata,
            append = false
        )

        verifyNoMoreInteractions(
            mockBatchWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() {empty array}`(
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedData = ByteArray(0)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)

        // When
        val result = testedWriter.write(serializedData, serializedBatchMetadata)

        // Then
        assertThat(result).isTrue

        verifyZeroInteractions(
            mockBatchWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `𝕄 return false 𝕎 write() {item is too big}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val maxItemSize = serializedData.size - 1
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn maxItemSize.toLong()

        // When
        val result = testedWriter.write(serializedData, serializedBatchMetadata)

        // Then
        assertThat(result).isFalse

        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            ERROR_LARGE_DATA.format(
                Locale.US,
                serializedData.size,
                maxItemSize
            ),
            null,
            emptyMap()
        )
    }

    @Test
    fun `𝕄 return false 𝕎 write() {write operation failed}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @Forgery batchFile: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(mockBatchWriter.writeData(batchFile, serializedData, true)) doReturn false

        // When
        val result = testedWriter.write(serializedData, serializedBatchMetadata)

        // Then
        assertThat(result).isFalse
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {no available file}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String
    ) {
        // Given
        testedWriter = FileEventBatchWriter(
            batchFile = fakeBatchFile,
            metadataFile = null,
            eventsWriter = mockBatchWriter,
            metadataReaderWriter = mockMetaReaderWriter,
            filePersistenceConfig = mockFilePersistenceConfig,
            internalLogger = mockInternalLogger
        )

        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)

        whenever(mockBatchWriter.writeData(fakeBatchFile, serializedData, true)) doReturn true

        // When
        val result = testedWriter.write(serializedData, serializedBatchMetadata)

        // Then
        assertThat(result).isTrue

        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {null or empty metadata}`(
        @StringForgery data: String,
        forge: Forge
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        whenever(mockBatchWriter.writeData(fakeBatchFile, serializedData, true)) doReturn true

        // When
        val result = testedWriter.write(serializedData, forge.aNullable { ByteArray(0) })

        // Then
        assertThat(result).isTrue

        verify(mockBatchWriter).writeData(
            fakeBatchFile,
            serializedData,
            true
        )
        verifyNoMoreInteractions(mockBatchWriter)
        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {item is too big}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val maxItemSize = serializedData.size - 1
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn maxItemSize.toLong()

        // When
        val result = testedWriter.write(serializedData, serializedBatchMetadata)

        // Then
        assertThat(result).isFalse

        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {write operation failed}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(
            mockBatchWriter.writeData(fakeBatchFile, serializedData, true)
        ) doReturn false

        // When
        val result = testedWriter.write(serializedData, serializedBatchMetadata)

        // Then
        assertThat(result).isFalse

        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 log warning 𝕎 write() {write metadata failed}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(
            mockBatchWriter.writeData(fakeBatchFile, serializedData, true)
        ) doReturn true
        whenever(
            mockMetaReaderWriter.writeData(fakeBatchMetadataFile, serializedBatchMetadata, false)
        ) doReturn false

        // When
        val result = testedWriter.write(serializedData, serializedBatchMetadata)

        // Then
        assertThat(result).isTrue()

        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            WARNING_METADATA_WRITE_FAILED.format(
                Locale.US,
                fakeBatchMetadataFile
            ),
            null,
            emptyMap()
        )
    }

    // endregion

    // region currentMetadata

    @Test
    fun `𝕄 not read metadata 𝕎 currentMetadata() {no available file}`() {
        // Given
        testedWriter = FileEventBatchWriter(
            batchFile = fakeBatchFile,
            metadataFile = null,
            eventsWriter = mockBatchWriter,
            metadataReaderWriter = mockMetaReaderWriter,
            filePersistenceConfig = mockFilePersistenceConfig,
            internalLogger = mockInternalLogger
        )

        // When
        val meta = testedWriter.currentMetadata()

        // Then
        assertThat(meta).isNull()
        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 read metadata 𝕎 currentMetadata()`(
        @StringForgery fakeMetadata: String
    ) {
        // Given
        testedWriter = FileEventBatchWriter(
            batchFile = fakeBatchFile,
            metadataFile = fakeBatchMetadataFile,
            eventsWriter = mockBatchWriter,
            metadataReaderWriter = mockMetaReaderWriter,
            filePersistenceConfig = mockFilePersistenceConfig,
            internalLogger = mockInternalLogger
        )
        whenever(mockMetaReaderWriter.readData(fakeBatchMetadataFile)) doReturn
            fakeMetadata.toByteArray()

        // When
        val meta = testedWriter.currentMetadata()

        // Then
        assertThat(meta).isEqualTo(fakeMetadata.toByteArray())
    }

    // endregion
}
