/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.BatchWriterListener
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.fail
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ProhibitLeavingStaticMocksExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ConsentAwareStorageTest {

    private lateinit var testedStorage: Storage

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockPendingOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockGrantedOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockBatchReaderWriter: BatchFileReaderWriter

    @Mock
    lateinit var mockMetaReaderWriter: FileReaderWriter

    @Mock
    lateinit var mockFileMover: FileMover

    @Mock
    lateinit var mockListener: BatchWriterListener

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFilePersistenceConfig: FilePersistenceConfig

    @BeforeEach
    fun `set up`() {
        testedStorage = ConsentAwareStorage(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
            mockListener,
            mockInternalLogger,
            mockFilePersistenceConfig
        )
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn Long.MAX_VALUE
    }

    // region writeCurrentBatch

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch() {consent=granted}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file
        val mockMetaFile: File = mock()
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockMetaReaderWriter.readData(mockMetaFile)) doReturn serializedMetadata
        whenever(mockBatchReaderWriter.writeData(file, serializedData, true)) doReturn true

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            val meta = it.currentMetadata()
            val updatedMeta = meta?.reversedArray()
            it.write(serializedData, eventId, updatedMeta)
        }

        // Then
        verify(mockGrantedOrchestrator).getWritableFile()
        verify(mockGrantedOrchestrator).getMetadataFile(file)
        verify(mockBatchReaderWriter).writeData(
            file,
            serializedData,
            append = true
        )
        verify(mockMetaReaderWriter).writeData(
            mockMetaFile,
            serializedMetadata.reversedArray(),
            append = false
        )
        verify(mockMetaReaderWriter).readData(mockMetaFile)

        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() {consent=granted, empty array}`(
        @StringForgery eventId: String,
        @StringForgery batchMetadata: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = ByteArray(0)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockMetaFile: File = mock()
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockGrantedOrchestrator).getWritableFile()
        verify(mockGrantedOrchestrator).getMetadataFile(file)

        verifyZeroInteractions(
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockPendingOrchestrator
        )
        verifyNoMoreInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = granted}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file
        whenever(mockBatchReaderWriter.writeData(file, serializedData, true)) doReturn true

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = granted, empty array}`(
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = ByteArray(0)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = granted, no available file}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = granted, item is too big}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file
        val maxItemSize = serializedData.size - 1
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn maxItemSize.toLong()

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            ConsentAwareStorage.ERROR_LARGE_DATA.format(
                Locale.US,
                serializedData.size,
                maxItemSize
            ),
            null,
            emptyMap()
        )
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = granted, write operation failed}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery batchFile: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn batchFile
        whenever(mockBatchReaderWriter.writeData(batchFile, serializedData, true)) doReturn false

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 not read metadata 𝕎 currentMetadata() {consent = granted, no available file}`() {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn null

        // When
        var meta: ByteArray? = null
        testedStorage.writeCurrentBatch(sdkContext) {
            meta = it.currentMetadata()
        }

        // Then
        assertThat(meta).isNull()
        verifyZeroInteractions(mockBatchReaderWriter, mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {consent = granted, no available file}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verifyZeroInteractions(mockBatchReaderWriter, mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {consent = granted, null or empty metadata}`(
        @StringForgery data: String,
        @Forgery file: File,
        @StringForgery eventId: String,
        forge: Forge
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockMetaFile: File = mock()
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, forge.aNullable { ByteArray(0) })
        }

        // Then
        verify(mockBatchReaderWriter).writeData(
            file,
            serializedData,
            true
        )
        verifyNoMoreInteractions(mockBatchReaderWriter)
        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {consent = granted, item is too big}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockMetaFile: File = mock()
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file
        val maxItemSize = serializedData.size - 1
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn maxItemSize.toLong()

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {consent = granted, write operation failed}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockMetaFile: File = mock()
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file
        whenever(mockBatchReaderWriter.writeData(file, serializedData, true)) doReturn false

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch() {consent=pending}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)

        val mockMetaFile: File = mock()
        whenever(mockPendingOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file
        whenever(mockMetaReaderWriter.readData(mockMetaFile)) doReturn serializedBatchMetadata
        whenever(mockBatchReaderWriter.writeData(file, serializedData, true)) doReturn true

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            val meta = it.currentMetadata()
            val updatedMeta = meta?.reversedArray()
            it.write(serializedData, eventId, updatedMeta)
        }

        // Then
        verify(mockPendingOrchestrator).getWritableFile()
        verify(mockPendingOrchestrator).getMetadataFile(file)
        verify(mockMetaReaderWriter).readData(mockMetaFile)
        verify(mockBatchReaderWriter).writeData(
            file,
            serializedData,
            append = true
        )
        verify(mockMetaReaderWriter).writeData(
            mockMetaFile,
            serializedBatchMetadata.reversedArray(),
            append = false
        )
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() {consent=pending, empty array}`(
        @StringForgery eventId: String,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedData = ByteArray(0)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verifyZeroInteractions(
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockGrantedOrchestrator
        )
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = pending}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file
        whenever(mockBatchReaderWriter.writeData(file, serializedData, true)) doReturn true

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = pending, empty array}`(
        @StringForgery eventId: String,
        @StringForgery batchMetadata: String
    ) {
        // Given
        val serializedData = ByteArray(0)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = pending, no available file}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = pending, item is too big}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file
        val maxItemSize = serializedData.size - 1
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn maxItemSize.toLong()

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            ConsentAwareStorage.ERROR_LARGE_DATA.format(
                Locale.US,
                serializedData.size,
                maxItemSize
            ),
            null,
            emptyMap()
        )
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = pending, write operation failed}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file
        whenever(mockBatchReaderWriter.writeData(file, serializedData, true)) doReturn false

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 not read metadata 𝕎 currentMetadata() {consent = pending, no available file}`() {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn null

        // When
        var meta: ByteArray? = null
        testedStorage.writeCurrentBatch(sdkContext) {
            meta = it.currentMetadata()
        }

        // Then
        assertThat(meta).isNull()
        verifyZeroInteractions(mockBatchReaderWriter, mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {consent = pending, no available file}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verifyZeroInteractions(mockBatchReaderWriter, mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {consent = pending, null or empty metadata}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File,
        forge: Forge
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockMetaFile: File = mock()
        whenever(mockPendingOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, forge.aNullable { ByteArray(0) })
        }

        // Then
        verify(mockBatchReaderWriter).writeData(
            file,
            serializedData,
            true
        )
        verifyNoMoreInteractions(mockBatchReaderWriter)
        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {consent = pending, item is too big}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockMetaFile: File = mock()
        whenever(mockPendingOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file
        val maxItemSize = serializedData.size - 1
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn maxItemSize.toLong()

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 not write metadata 𝕎 write() {consent = pending, write operation failed}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockMetaFile: File = mock()
        whenever(mockPendingOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file
        whenever(mockBatchReaderWriter.writeData(file, serializedData, true)) doReturn false

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verifyZeroInteractions(mockMetaReaderWriter)
    }

    @Test
    fun `𝕄 provide no op writer 𝕎 writeCurrentBatch() {consent=not_granted}`(
        @StringForgery data: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            val meta = it.currentMetadata()
            val updatedMeta = meta?.reversedArray()
            it.write(serializedData, eventId, updatedMeta)
        }

        // Then
        verifyZeroInteractions(
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockGrantedOrchestrator,
            mockPendingOrchestrator
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() {consent=not_granted, empty array}`(
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = ByteArray(0)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verifyZeroInteractions(
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockGrantedOrchestrator,
            mockPendingOrchestrator
        )
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = not_granted}`(
        @StringForgery data: String,
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = data.toByteArray(Charsets.UTF_8)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = not_granted, empty array}`(
        @StringForgery batchMetadata: String,
        @StringForgery eventId: String
    ) {
        // Given
        val serializedData = ByteArray(0)
        val serializedBatchMetadata = batchMetadata.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serializedData, eventId, serializedBatchMetadata)
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 not read metadata 𝕎 currentMetadata() {consent = not_granted}`() {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        var meta: ByteArray? = null
        testedStorage.writeCurrentBatch(sdkContext) {
            meta = it.currentMetadata()
        }

        // Then
        assertThat(meta).isNull()
        verifyZeroInteractions(mockBatchReaderWriter, mockMetaReaderWriter)
    }

    // endregion

    // region readNextBatch

    @Test
    fun `𝕄 provide reader 𝕎 readNextBatch()`(
        @StringForgery data: List<String>,
        @StringForgery metadata: String,
        @Forgery batchFile: File
    ) {
        // Given
        val mockData = data.map { it.toByteArray() }
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn batchFile
        whenever(mockBatchReaderWriter.readData(batchFile)) doReturn mockData

        val mockMetaFile = mock<File>().apply {
            whenever(exists()) doReturn true
        }

        whenever(mockGrantedOrchestrator.getMetadataFile(batchFile)) doReturn mockMetaFile
        val mockMetadata = metadata.toByteArray()
        whenever(mockMetaReaderWriter.readData(mockMetaFile)) doReturn mockMetadata

        // Whenever
        var readData: List<ByteArray>? = null
        var readMetadata: ByteArray? = null
        testedStorage.readNextBatch(fakeDatadogContext) { _, reader ->
            readMetadata = reader.currentMetadata()
            readData = reader.read()
        }

        // Then
        assertThat(readData).isEqualTo(mockData)
        assertThat(readMetadata).isEqualTo(mockMetadata)
    }

    @Test
    fun `𝕄 provide reader 𝕎 readNextBatch() { no metadata file provided }`(
        @StringForgery data: List<String>,
        @Forgery batchFile: File
    ) {
        // Given
        val mockData = data.map { it.toByteArray() }
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn batchFile
        whenever(mockBatchReaderWriter.readData(batchFile)) doReturn mockData

        // Whenever
        var readData: List<ByteArray>? = null
        var readMetadata: ByteArray? = null
        testedStorage.readNextBatch(fakeDatadogContext) { _, reader ->
            readMetadata = reader.currentMetadata()
            readData = reader.read()
        }

        // Then
        assertThat(readData).isEqualTo(mockData)
        assertThat(readMetadata).isNull()
    }

    @Test
    fun `𝕄 provide reader 𝕎 readNextBatch() { metadata file doesn't exist }`(
        @StringForgery data: List<String>,
        @Forgery batchFile: File
    ) {
        // Given
        val mockData = data.map { it.toByteArray() }
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn batchFile
        whenever(mockBatchReaderWriter.readData(batchFile)) doReturn mockData

        val mockMetaFile = mock<File>().apply {
            whenever(exists()) doReturn false
        }

        whenever(mockGrantedOrchestrator.getMetadataFile(batchFile)) doReturn mockMetaFile

        // Whenever
        var readData: List<ByteArray>? = null
        var readMetadata: ByteArray? = null
        testedStorage.readNextBatch(fakeDatadogContext) { _, reader ->
            readMetadata = reader.currentMetadata()
            readData = reader.read()
        }

        // Then
        assertThat(readData).isEqualTo(mockData)
        assertThat(readMetadata).isNull()
    }

    @Test
    fun `𝕄 provide reader only once 𝕎 readNextBatch()`(
        @StringForgery data: List<String>,
        @Forgery file: File
    ) {
        // Given
        val mockData = data.map { it.toByteArray() }
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        whenever(mockGrantedOrchestrator.getReadableFile(setOf(file))) doReturn null
        whenever(mockBatchReaderWriter.readData(file)) doReturn mockData

        // When
        var readData: List<ByteArray>? = null
        testedStorage.readNextBatch(fakeDatadogContext) { _, reader ->
            readData = reader.read()
        }
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            fail { "Callback should not have been called again" }
        }

        // Then
        assertThat(readData).isEqualTo(mockData)
    }

    @Test
    fun `𝕄 do nothing 𝕎 readNextBatch() {no file}`() {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn null

        // When
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            fail { "Callback should not have been called here" }
        }
    }

    // endregion

    // region confirmBatchRead

    @Test
    fun `𝕄 delete batch files 𝕎 readNextBatch()+confirmBatchRead() {delete=true}`(
        @Forgery file: File,
        @StringForgery fakeMetaFilePath: String
    ) {
        // Given
        testedStorage = ConsentAwareStorage(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
            mockListener,
            mockInternalLogger,
            mockFilePersistenceConfig
        )

        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        val mockMetaFile: File = mock()
        whenever(mockMetaFile.exists()) doReturn true
        whenever(mockMetaFile.path) doReturn fakeMetaFilePath
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockFileMover.delete(file)) doReturn true
        doReturn(true).whenever(mockFileMover).delete(mockMetaFile)

        // When
        var batchId: BatchId? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId = id
        }
        testedStorage.confirmBatchRead(batchId!!) { confirm ->
            confirm.markAsRead(true)
        }

        // Then
        verify(mockFileMover).delete(file)
        verify(mockFileMover).delete(mockMetaFile)
    }

    @Test
    fun `𝕄 read batch twice if released 𝕎 readNextBatch()+confirmBatchRead() {delete=false}`(
        @Forgery file: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        val mockMetaFile: File = mock()
        whenever(mockMetaFile.exists()) doReturn true
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockGrantedOrchestrator.getReadableFile(setOf(file))) doReturn null

        // When
        var batchId1: BatchId? = null
        var batchId2: BatchId? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId1 = id
        }
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            fail { "Callback should not have been called here" }
        }
        testedStorage.confirmBatchRead(batchId1!!) { confirm ->
            confirm.markAsRead(false)
        }
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId2 = id
        }

        // Then
        verify(mockFileMover, never()).delete(file)
        verify(mockFileMover, never()).delete(mockMetaFile)
        assertThat(batchId1).isEqualTo(batchId2)
    }

    @Test
    fun `𝕄 keep batch file locked 𝕎 readNextBatch()+confirmBatchRead() {delete=true, != batchId}`(
        @Forgery file: File,
        @Forgery anotherFile: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        val mockMetaFile: File = mock()
        whenever(mockMetaFile.exists()) doReturn true
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile

        // When
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            // no-op
        }
        testedStorage.confirmBatchRead(BatchId.fromFile(anotherFile)) { confirm ->
            confirm.markAsRead(true)
        }
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            fail { "Callback should not have been called here" }
        }

        // Then
        verify(mockFileMover, never()).delete(file)
        verify(mockFileMover, never()).delete(mockMetaFile)
    }

    @Test
    fun `𝕄 warn 𝕎 readNextBatch() + confirmBatchRead() {delete batch fails}`(
        @Forgery file: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        whenever(mockFileMover.delete(file)) doReturn false

        // When
        var batchId: BatchId? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId = id
        }
        testedStorage.confirmBatchRead(batchId!!) { confirm ->
            confirm.markAsRead(true)
        }

        // Then
        verify(mockFileMover).delete(file)
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            ConsentAwareStorage.WARNING_DELETE_FAILED.format(Locale.US, file.path),
            null,
            emptyMap()
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 warn 𝕎 readNextBatch() + confirmBatchRead() {delete batch meta fails}`(
        @Forgery file: File,
        @StringForgery fakeMetaFilePath: String
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        val mockMetaFile: File = mock()
        whenever(mockMetaFile.exists()) doReturn true
        whenever(mockMetaFile.path) doReturn fakeMetaFilePath
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockFileMover.delete(file)) doReturn true
        doReturn(false).whenever(mockFileMover).delete(mockMetaFile)

        // When
        var batchId: BatchId? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId = id
        }
        testedStorage.confirmBatchRead(batchId!!) { confirm ->
            confirm.markAsRead(true)
        }

        // Then
        verify(mockFileMover).delete(file)
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            ConsentAwareStorage.WARNING_DELETE_FAILED.format(Locale.US, mockMetaFile.path),
            null,
            emptyMap()
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    // endregion
}
