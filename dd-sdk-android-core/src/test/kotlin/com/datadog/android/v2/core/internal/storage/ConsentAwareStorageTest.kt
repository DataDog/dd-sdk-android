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
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.fail
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
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
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFilePersistenceConfig: FilePersistenceConfig

    @BeforeEach
    fun `set up`() {
        testedStorage = ConsentAwareStorage(
            // same thread executor
            executorService = FakeSameThreadExecutorService(),
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
            mockInternalLogger,
            mockFilePersistenceConfig
        )
    }

    // region writeCurrentBatch

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch() {consent=granted}`(
        @BoolForgery forceNewBatch: Boolean,
        @Forgery file: File,
        forge: Forge
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile(forceNewBatch)) doReturn file
        val mockMetaFile: File? = forge.aNullable { mock() }
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, callback = mockCallback)

        // Then
        verify(mockGrantedOrchestrator).getWritableFile(forceNewBatch)
        verify(mockGrantedOrchestrator).getMetadataFile(file)
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(FileEventBatchWriter::class.java)
        }

        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `𝕄 provide no-op writer 𝕎 writeCurrentBatch(){granted, no file}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile(forceNewBatch)) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, callback = mockCallback)

        // Then
        verify(mockGrantedOrchestrator).getWritableFile(forceNewBatch)
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(NoOpEventBatchWriter::class.java)
        }

        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch() {consent=pending}`(
        @BoolForgery forceNewBatch: Boolean,
        @Forgery file: File,
        forge: Forge
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile(forceNewBatch)) doReturn file
        val mockMetaFile: File? = forge.aNullable { mock() }
        whenever(mockPendingOrchestrator.getMetadataFile(file)) doReturn mockMetaFile

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, callback = mockCallback)

        // Then
        verify(mockPendingOrchestrator).getWritableFile(forceNewBatch)
        verify(mockPendingOrchestrator).getMetadataFile(file)
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(FileEventBatchWriter::class.java)
        }

        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `𝕄 provide no-op writer 𝕎 writeCurrentBatch() {pending, no file}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile(forceNewBatch)) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, callback = mockCallback)

        // Then
        verify(mockPendingOrchestrator).getWritableFile(forceNewBatch)
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(NoOpEventBatchWriter::class.java)
        }

        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter
        )
    }

    @Test
    fun `𝕄 provide no-op writer 𝕎 writeCurrentBatch() {not_granted}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, callback = mockCallback)

        // Then
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(NoOpEventBatchWriter::class.java)
        }
        verifyNoInteractions(
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockGrantedOrchestrator,
            mockPendingOrchestrator
        )
    }

    // endregion

    @Test
    fun `𝕄 log error 𝕎 writeCurrentBatch() { task was rejected }`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val mockExecutor = mock<ExecutorService>()
        whenever(mockExecutor.submit(any())) doThrow RejectedExecutionException()
        testedStorage = ConsentAwareStorage(
            // same thread executor
            mockExecutor,
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
            mockInternalLogger,
            mockFilePersistenceConfig
        )

        // When
        testedStorage.writeCurrentBatch(fakeDatadogContext, forceNewBatch) {
            // no-op
        }

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            eq(ConsentAwareStorage.ERROR_WRITE_CONTEXT_EXECUTION_REJECTED),
            isA<RejectedExecutionException>()
        )
    }

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
        testedStorage.readNextBatch { _, reader ->
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
        testedStorage.readNextBatch { _, reader ->
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
        testedStorage.readNextBatch { _, reader ->
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
        testedStorage.readNextBatch { _, reader ->
            readData = reader.read()
        }
        testedStorage.readNextBatch { _, _ ->
            fail { "Callback should not have been called again" }
        }

        // Then
        assertThat(readData).isEqualTo(mockData)
    }

    @Test
    fun `𝕄 notify no batch available 𝕎 readNextBatch() {no file}`() {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn null
        val mockNoBatchCallback = mock<() -> Unit>()

        // When
        testedStorage.readNextBatch(
            noBatchCallback = mockNoBatchCallback
        ) { _, _ ->
            fail { "Callback should not have been called here" }
        }

        // Then
        verify(mockNoBatchCallback).invoke()
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
            executorService = FakeSameThreadExecutorService(),
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
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
        testedStorage.readNextBatch { id, _ ->
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
        testedStorage.readNextBatch { id, _ ->
            batchId1 = id
        }
        testedStorage.readNextBatch { _, _ ->
            fail { "Callback should not have been called here" }
        }
        testedStorage.confirmBatchRead(batchId1!!) { confirm ->
            confirm.markAsRead(false)
        }
        testedStorage.readNextBatch { id, _ ->
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
        testedStorage.readNextBatch { _, _ ->
            // no-op
        }
        testedStorage.confirmBatchRead(BatchId.fromFile(anotherFile)) { confirm ->
            confirm.markAsRead(true)
        }
        testedStorage.readNextBatch { _, _ ->
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
        testedStorage.readNextBatch { id, _ ->
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
            ConsentAwareStorage.WARNING_DELETE_FAILED.format(Locale.US, file.path)
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
        testedStorage.readNextBatch { id, _ ->
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
            ConsentAwareStorage.WARNING_DELETE_FAILED.format(Locale.US, mockMetaFile.path)
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    // endregion

    // region dropAll

    @Test
    fun `𝕄 delete everything 𝕎 dropAll()`(
        @Forgery pendingFile: File,
        @Forgery grantedFile: File,
        @StringForgery fakePendingMetaFilePath: String,
        @StringForgery fakeGrantedMetaFilePath: String
    ) {
        // Given
        testedStorage = ConsentAwareStorage(
            executorService = FakeSameThreadExecutorService(),
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
            mockInternalLogger,
            mockFilePersistenceConfig
        )

        whenever(mockGrantedOrchestrator.getAllFiles()) doReturn listOf(grantedFile)
        whenever(mockPendingOrchestrator.getAllFiles()) doReturn listOf(pendingFile)
        val mockPendingMetaFile: File = mock()
        whenever(mockPendingMetaFile.exists()) doReturn true
        whenever(mockPendingMetaFile.path) doReturn fakePendingMetaFilePath
        val mockGrantedMetaFile: File = mock()
        whenever(mockGrantedMetaFile.exists()) doReturn true
        whenever(mockGrantedMetaFile.path) doReturn fakeGrantedMetaFilePath
        whenever(mockGrantedOrchestrator.getMetadataFile(grantedFile)) doReturn mockGrantedMetaFile
        whenever(mockFileMover.delete(grantedFile)) doReturn true
        whenever(mockPendingOrchestrator.getMetadataFile(pendingFile)) doReturn mockPendingMetaFile
        whenever(mockFileMover.delete(pendingFile)) doReturn true
        doReturn(true).whenever(mockFileMover).delete(mockGrantedMetaFile)
        doReturn(true).whenever(mockFileMover).delete(mockPendingMetaFile)

        // When
        testedStorage.dropAll()

        // Then
        verify(mockFileMover).delete(grantedFile)
        verify(mockFileMover).delete(mockGrantedMetaFile)
        verify(mockFileMover).delete(pendingFile)
        verify(mockFileMover).delete(mockPendingMetaFile)
    }

    @Test
    fun `𝕄 delete everything 𝕎 dropAll() { there is locked batch }`(
        @Forgery file: File,
        @StringForgery fakeMetaFilePath: String
    ) {
        // Given
        testedStorage = ConsentAwareStorage(
            executorService = FakeSameThreadExecutorService(),
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
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

        testedStorage.readNextBatch { _, _ ->
            // no-op
        }

        // When
        testedStorage.dropAll()

        // Then
        verify(mockFileMover).delete(file)
        verify(mockFileMover).delete(mockMetaFile)
    }

    // endregion

    class FakeSameThreadExecutorService : AbstractExecutorService() {

        private var isShutdown = false

        override fun execute(command: Runnable?) {
            command?.run()
        }

        override fun shutdown() {
            isShutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            isShutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = isShutdown

        override fun isTerminated(): Boolean = isShutdown

        override fun awaitTermination(timeout: Long, unit: TimeUnit?): Boolean {
            return true
        }
    }
}
