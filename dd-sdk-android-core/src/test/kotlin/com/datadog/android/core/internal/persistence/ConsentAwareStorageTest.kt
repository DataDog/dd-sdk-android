/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.metrics.MetricsDispatcher
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.FileReaderWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReaderWriter
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

    @Mock
    lateinit var mockMetricsDispatcher: MetricsDispatcher

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeEventType: EventType

    @Mock
    lateinit var mockMetric: PerformanceMetric

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeRootDirName: String

    @Forgery
    lateinit var mockPendingRootParentFile: File

    @Forgery
    lateinit var mockGrantedRootParentFile: File

    @Mock
    lateinit var mockConsentProvider: ConsentProvider

    @StringForgery
    lateinit var fakeFeatureName: String

    @IntForgery(min = 0, max = 100)
    var fakePendingBatches: Int = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockConsentProvider.getConsent()) doReturn forge.aValueFrom(TrackingConsent::class.java)
        whenever(mockPendingOrchestrator.getRootDir()) doReturn File(mockPendingRootParentFile, fakeRootDirName)
        whenever(mockGrantedOrchestrator.getRootDir()) doReturn File(mockGrantedRootParentFile, fakeRootDirName)
        whenever(mockPendingOrchestrator.getRootDirName()) doReturn fakeRootDirName
        whenever(mockGrantedOrchestrator.getRootDirName()) doReturn fakeRootDirName
        whenever((mockGrantedOrchestrator).decrementAndGetPendingFilesCount())
            .thenReturn(fakePendingBatches - 1)
        whenever((mockPendingOrchestrator).decrementAndGetPendingFilesCount())
            .thenReturn(fakePendingBatches - 1)

        whenever(
            mockInternalLogger.startPerformanceMeasure(
                "com.datadog.android.core.internal.persistence.ConsentAwareStorage",
                TelemetryMetricType.MethodCalled,
                0.001f,
                "writeCurrentBatch[$fakeFeatureName]"
            )
        ) doReturn mockMetric

        testedStorage = ConsentAwareStorage(
            // same thread executor
            executorService = FakeSameThreadExecutorService(),
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
            mockInternalLogger,
            mockFilePersistenceConfig,
            mockMetricsDispatcher,
            mockConsentProvider,
            fakeFeatureName
        )
    }

    // region writeCurrentBatch

    @Test
    fun `M provide writer W writeCurrentBatch() {consent=granted}`(
        @BoolForgery forceNewBatch: Boolean,
        @Forgery file: File,
        forge: Forge
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockConsentProvider.getConsent()) doReturn TrackingConsent.GRANTED
        whenever(mockGrantedOrchestrator.getWritableFile(forceNewBatch)) doReturn file
        val mockMetaFile: File? = forge.aNullable { mock() }
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile

        // When
        testedStorage.writeCurrentBatch(fakeDatadogContext, forceNewBatch, callback = mockCallback)

        // Then
        verify(mockGrantedOrchestrator).getWritableFile(forceNewBatch)
        verify(mockGrantedOrchestrator).getMetadataFile(file)
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(FileEventBatchWriter::class.java)
        }
        verify(mockMetric).stopAndSend(true)
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockMetric
        )
    }

    @Test
    fun `M provide no-op writer W writeCurrentBatch(){granted, no file}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockConsentProvider.getConsent()) doReturn TrackingConsent.GRANTED
        whenever(mockGrantedOrchestrator.getWritableFile(forceNewBatch)) doReturn null

        // When
        testedStorage.writeCurrentBatch(fakeDatadogContext, forceNewBatch, callback = mockCallback)

        // Then
        verify(mockGrantedOrchestrator).getWritableFile(forceNewBatch)
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(NoOpEventBatchWriter::class.java)
        }
        verify(mockMetric).stopAndSend(false)
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockMetric
        )
    }

    @Test
    fun `M provide writer W writeCurrentBatch() {consent=pending}`(
        @BoolForgery forceNewBatch: Boolean,
        @Forgery file: File,
        forge: Forge
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockConsentProvider.getConsent()) doReturn TrackingConsent.PENDING
        whenever(mockPendingOrchestrator.getWritableFile(forceNewBatch)) doReturn file
        val mockMetaFile: File? = forge.aNullable { mock() }
        whenever(mockPendingOrchestrator.getMetadataFile(file)) doReturn mockMetaFile

        // When
        testedStorage.writeCurrentBatch(fakeDatadogContext, forceNewBatch, callback = mockCallback)

        // Then
        verify(mockPendingOrchestrator).getWritableFile(forceNewBatch)
        verify(mockPendingOrchestrator).getMetadataFile(file)
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(FileEventBatchWriter::class.java)
        }
        verify(mockMetric).stopAndSend(true)
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockMetric
        )
    }

    @Test
    fun `M provide no-op writer W writeCurrentBatch() {pending, no file}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockConsentProvider.getConsent()) doReturn TrackingConsent.PENDING
        whenever(mockPendingOrchestrator.getWritableFile(forceNewBatch)) doReturn null

        // When
        testedStorage.writeCurrentBatch(fakeDatadogContext, forceNewBatch, callback = mockCallback)

        // Then
        verify(mockPendingOrchestrator).getWritableFile(forceNewBatch)
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(NoOpEventBatchWriter::class.java)
        }
        verify(mockMetric).stopAndSend(false)
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockMetric
        )
    }

    @Test
    fun `M provide no-op writer W writeCurrentBatch() {not_granted}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockConsentProvider.getConsent()) doReturn TrackingConsent.NOT_GRANTED
        whenever(
            mockInternalLogger.startPerformanceMeasure(
                "com.datadog.android.core.internal.persistence.ConsentAwareStorage",
                TelemetryMetricType.MethodCalled,
                0.001f,
                "writeCurrentBatch[null]"
            )
        ) doReturn mockMetric

        // When
        testedStorage.writeCurrentBatch(fakeDatadogContext, forceNewBatch, callback = mockCallback)

        // Then
        argumentCaptor<EventBatchWriter> {
            verify(mockCallback).invoke(capture())
            assertThat(firstValue).isInstanceOf(NoOpEventBatchWriter::class.java)
        }
        verify(mockMetric).stopAndSend(false)
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockMetric
        )
    }

    // endregion

    @Test
    fun `M log error W writeCurrentBatch() { task was rejected }`(
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
            mockFilePersistenceConfig,
            mockMetricsDispatcher,
            mockConsentProvider,
            fakeFeatureName
        )

        // When
        testedStorage.writeCurrentBatch(fakeDatadogContext, forceNewBatch) {
            // no-op
        }

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule Data write task on the executor",
            RejectedExecutionException::class.java,
            false
        )
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockMetric
        )
    }

    @Test
    fun `M do sequential metadata write W writeCurrentBatch() { multithreaded }`(
        @IntForgery(min = 2, max = 10) threadsCount: Int,
        @BoolForgery forceNewBatch: Boolean,
        @Forgery file: File,
        forge: Forge
    ) {
        // Given
        val executor = Executors.newFixedThreadPool(threadsCount)
        testedStorage = ConsentAwareStorage(
            executor,
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockBatchReaderWriter,
            mockMetaReaderWriter,
            mockFileMover,
            mockInternalLogger,
            mockFilePersistenceConfig,
            mockMetricsDispatcher,
            mockConsentProvider,
            fakeFeatureName
        )
        var accumulator: Byte = 0
        val event = forge.aString().toByteArray()
        // each write operation is going to increase value in meta by 1
        // in the end if some write operation was parallel to another, total number in meta
        // won't be equal to the number of threads
        // if write operations are parallel, there is a chance that there will be a conflict
        // updating the meta (applying different updates to the same original state).
        val callback: (EventBatchWriter) -> Unit = {
            val value = it.currentMetadata()?.first() ?: 0
            it.write(
                event = RawBatchEvent(data = event),
                batchMetadata = byteArrayOf((value + 1).toByte()),
                eventType = fakeEventType
            )
        }
        whenever(mockConsentProvider.getConsent()) doReturn TrackingConsent.GRANTED
        whenever(mockGrantedOrchestrator.getWritableFile(forceNewBatch)) doReturn file
        val mockMetaFile = mock<File>().apply { whenever(exists()) doReturn true }
        whenever(mockMetaReaderWriter.readData(mockMetaFile)) doAnswer {
            byteArrayOf(accumulator)
        }
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(
            mockBatchReaderWriter.writeData(eq(file), data = any(), append = any())
        ) doReturn true
        whenever(
            mockMetaReaderWriter.writeData(eq(mockMetaFile), data = any(), append = any())
        ) doAnswer {
            val value = it.getArgument<ByteArray>(1).first()
            accumulator = value
            true
        }
        whenever(mockFilePersistenceConfig.maxItemSize) doReturn (event.size + 1).toLong()

        // When
        repeat(threadsCount) {
            testedStorage.writeCurrentBatch(fakeDatadogContext, forceNewBatch, callback = callback)
        }
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        assertThat(accumulator).isEqualTo(threadsCount.toByte())
    }

    // region readNextBatch

    @Test
    fun `M provide batchData W readNextBatch()`(
        @Forgery fakeData: List<RawBatchEvent>,
        @StringForgery metadata: String,
        @Forgery batchFile: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn batchFile
        whenever(mockBatchReaderWriter.readData(batchFile)) doReturn fakeData

        val mockMetaFile = mock<File>().apply {
            whenever(exists()) doReturn true
        }

        whenever(mockGrantedOrchestrator.getMetadataFile(batchFile)) doReturn mockMetaFile
        val mockMetadata = metadata.toByteArray()
        whenever(mockMetaReaderWriter.readData(mockMetaFile)) doReturn mockMetadata

        // Whenever
        val batchData = testedStorage.readNextBatch()

        // Then
        assertThat(batchData).isNotNull
        assertThat(batchData?.id).isNotNull
        assertThat(batchData?.data).isEqualTo(fakeData)
        assertThat(batchData?.metadata).isEqualTo(mockMetadata)
    }

    @Test
    fun `M provide batchData W readNextBatch() { no metadata file provided }`(
        @Forgery fakeData: List<RawBatchEvent>,
        @Forgery batchFile: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn batchFile
        whenever(mockBatchReaderWriter.readData(batchFile)) doReturn fakeData
        whenever(mockGrantedOrchestrator.getMetadataFile(batchFile)) doReturn null

        // Whenever
        val batchData = testedStorage.readNextBatch()

        // Then
        assertThat(batchData).isNotNull
        assertThat(batchData?.id).isNotNull
        assertThat(batchData?.data).isEqualTo(fakeData)
        assertThat(batchData?.metadata).isNull()
    }

    @Test
    fun `M provide batchData W readNextBatch() { metadata file doesn't exist }`(
        @Forgery fakeData: List<RawBatchEvent>,
        @Forgery batchFile: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn batchFile
        whenever(mockBatchReaderWriter.readData(batchFile)) doReturn fakeData

        val mockMetaFile = mock<File>().apply {
            whenever(exists()) doReturn false
        }

        whenever(mockGrantedOrchestrator.getMetadataFile(batchFile)) doReturn mockMetaFile

        // Whenever
        val batchData = testedStorage.readNextBatch()

        // Then
        assertThat(batchData).isNotNull
        assertThat(batchData?.id).isNotNull
        assertThat(batchData?.data).isEqualTo(fakeData)
        assertThat(batchData?.metadata).isNull()
    }

    @Test
    fun `M return null no batch available W readNextBatch() {no file}`() {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn null

        // Whenever
        val batchData = testedStorage.readNextBatch()

        // Then
        assertThat(batchData).isNull()
    }

    // endregion

    // region confirmBatchRead

    @Test
    fun `M read batch twice if released W readNextBatch()+confirmBatchRead() {delete=false}`(
        @Forgery reason: RemovalReason,
        @Forgery file: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        val mockMetaFile: File = mock()
        whenever(mockMetaFile.exists()) doReturn true
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockGrantedOrchestrator.getReadableFile(setOf(file))) doReturn null

        // When
        val batchData1 = testedStorage.readNextBatch()
        testedStorage.confirmBatchRead(batchData1!!.id, reason, false)
        val batchData2 = testedStorage.readNextBatch()

        // Then
        verify(mockFileMover, never()).delete(file)
        verify(mockFileMover, never()).delete(mockMetaFile)
        assertThat(batchData1).isEqualTo(batchData2)
    }

    @Test
    fun `M delete batch files W readNextBatch()+confirmBatchRead() {delete=true}`(
        @Forgery file: File,
        @Forgery reason: RemovalReason,
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
            mockFilePersistenceConfig,
            mockMetricsDispatcher,
            mockConsentProvider,
            fakeFeatureName
        )

        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        val mockMetaFile: File = mock()
        whenever(mockMetaFile.exists()) doReturn true
        whenever(mockMetaFile.path) doReturn fakeMetaFilePath
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile
        whenever(mockFileMover.delete(file)) doReturn true
        doReturn(true).whenever(mockFileMover).delete(mockMetaFile)

        // When
        val batchData1 = testedStorage.readNextBatch()
        testedStorage.confirmBatchRead(batchData1!!.id, reason, true)
        testedStorage.readNextBatch()

        // Then
        verify(mockFileMover).delete(file)
        verify(mockFileMover).delete(mockMetaFile)
        verify(mockMetricsDispatcher).sendBatchDeletedMetric(
            eq(file),
            eq(reason),
            eq(fakePendingBatches - 1)
        )
    }

    @Test
    fun `M keep batch file locked W readNextBatch()+confirmBatchRead() {delete=true, != batchId}`(
        @Forgery file: File,
        @Forgery reason: RemovalReason,
        @Forgery anotherFile: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        val mockMetaFile: File = mock()
        whenever(mockMetaFile.exists()) doReturn true
        whenever(mockGrantedOrchestrator.getMetadataFile(file)) doReturn mockMetaFile

        // When
        testedStorage.readNextBatch()
        testedStorage.confirmBatchRead(BatchId.fromFile(anotherFile), reason, false)
        testedStorage.readNextBatch()

        // Then
        verify(mockFileMover, never()).delete(file)
        verify(mockFileMover, never()).delete(mockMetaFile)
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M warn W readNextBatch() + confirmBatchRead() {delete batch fails}`(
        @Forgery reason: RemovalReason,
        @Forgery file: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        whenever(mockFileMover.delete(file)) doReturn false

        // When
        val batchData1 = testedStorage.readNextBatch()
        testedStorage.confirmBatchRead(batchData1!!.id, reason, true)
        testedStorage.readNextBatch()

        // Then
        verify(mockFileMover).delete(file)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            ConsentAwareStorage.WARNING_DELETE_FAILED.format(Locale.US, file.path)
        )
        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockMetricsDispatcher)
    }

    @Test
    fun `M warn W readNextBatch() + confirmBatchRead() {delete batch meta fails}`(
        @Forgery reason: RemovalReason,
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
        val batchData1 = testedStorage.readNextBatch()
        testedStorage.confirmBatchRead(batchData1!!.id, reason, true)

        // Then
        verify(mockFileMover).delete(file)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            ConsentAwareStorage.WARNING_DELETE_FAILED.format(Locale.US, mockMetaFile.path)
        )
        verifyNoMoreInteractions(mockInternalLogger)
        verify(mockMetricsDispatcher).sendBatchDeletedMetric(
            eq(file),
            eq(reason),
            eq(fakePendingBatches - 1)
        )
    }

    // endregion

    // region dropAll

    @Test
    fun `M delete everything W dropAll()`(
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
            mockFilePersistenceConfig,
            mockMetricsDispatcher,
            mockConsentProvider,
            fakeFeatureName
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
        verify(mockMetricsDispatcher).sendBatchDeletedMetric(
            eq(grantedFile),
            argThat {
                this is RemovalReason.Flushed
            },
            eq(fakePendingBatches - 1)
        )
        verify(mockMetricsDispatcher).sendBatchDeletedMetric(
            eq(pendingFile),
            argThat {
                this is RemovalReason.Flushed
            },
            eq(fakePendingBatches - 1)
        )
    }

    @Test
    fun `M delete everything W dropAll() { there are locked batches }`(
        @Forgery files: List<File>,
        forge: Forge
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
            mockFilePersistenceConfig,
            mockMetricsDispatcher,
            mockConsentProvider,
            fakeFeatureName
        )

        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturnConsecutively files
        val mockMetaFiles = files.map {
            val fakeMetaFilePath = forge.anAlphabeticalString()
            val mockMetaFile = mock<File>()
            whenever(mockMetaFile.exists()) doReturn true
            whenever(mockMetaFile.path) doReturn fakeMetaFilePath
            whenever(mockGrantedOrchestrator.getMetadataFile(it)) doReturn mockMetaFile
            doReturn(true).whenever(mockFileMover).delete(mockMetaFile)
            mockMetaFile
        }
        files.forEach {
            whenever(mockFileMover.delete(it)) doReturn true
        }

        // ConcurrentModificationException is thrown only during the next check after remove,
        // so to make sure it is not thrown we need at least 2 locked batches
        repeat(files.size) {
            testedStorage.readNextBatch()
        }

        // When
        testedStorage.dropAll()

        // Then
        files.forEachIndexed { index, file ->
            verify(mockFileMover).delete(file)
            verify(mockFileMover).delete(mockMetaFiles[index])
            verify(mockMetricsDispatcher).sendBatchDeletedMetric(
                eq(file),
                argThat {
                    this is RemovalReason.Flushed
                },
                eq(fakePendingBatches - 1)
            )
        }
    }

    // endregion
}
