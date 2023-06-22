/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.datadog.android.Datadog
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.BatchConfirmation
import com.datadog.android.v2.core.internal.storage.BatchId
import com.datadog.android.v2.core.internal.storage.BatchReader
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UploadWorkerTest {

    private lateinit var testedWorker: Worker

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockFeatureA: SdkFeature

    @Mock
    lateinit var mockStorageA: Storage

    @Mock
    lateinit var mockBatchReaderA: BatchReader

    @Mock
    lateinit var mockUploaderA: DataUploader

    @Mock
    lateinit var mockFeatureB: SdkFeature

    @Mock
    lateinit var mockStorageB: Storage

    @Mock
    lateinit var mockBatchReaderB: BatchReader

    @Mock
    lateinit var mockUploaderB: DataUploader

    @Forgery
    lateinit var fakeWorkerParameters: WorkerParameters

    @StringForgery
    lateinit var fakeInstanceName: String

    @Forgery
    lateinit var fakeContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getDatadogContext()) doReturn fakeContext
        Datadog.registry.register(fakeInstanceName, mockSdkCore)
        val fakeData = Data.Builder()
            .putString(UploadWorker.DATADOG_INSTANCE_NAME, fakeInstanceName)
            .build()
        fakeWorkerParameters = fakeWorkerParameters.copyWith(fakeData)

        stubFeatures(
            mockSdkCore,
            listOf(mockFeatureA, mockFeatureB),
            listOf(mockStorageA, mockStorageB),
            listOf(mockUploaderA, mockUploaderB)
        )

        testedWorker = UploadWorker(
            appContext.mockInstance,
            fakeWorkerParameters
        )
    }

    @AfterEach
    fun `tear down`() {
        Datadog.registry.clear()
    }

    // region doWork

    @Test
    fun `𝕄 send batches 𝕎 doWork() {single batch per feature}`(
        @StringForgery batchA: List<String>,
        @StringForgery batchAMeta: String,
        @StringForgery batchB: List<String>,
        @StringForgery batchBMeta: String,
        forge: Forge
    ) {
        // Given
        val batchAData = batchA.map { it.toByteArray() }
        val batchBData = batchB.map { it.toByteArray() }
        val batchAMetadata = forge.aNullable { batchAMeta.toByteArray() }
        val batchBMetadata = forge.aNullable { batchBMeta.toByteArray() }

        val batchAConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageA,
            mockBatchReaderA,
            mock(),
            batchAConfirmation,
            batchAData,
            batchAMetadata
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchBData,
            batchBMetadata
        )

        whenever(
            mockUploaderA.upload(
                fakeContext,
                batchAData,
                batchAMetadata
            )
        ) doReturn UploadStatus.SUCCESS
        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchBData,
                batchBMetadata
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        val result = testedWorker.doWork()

        // Then
        verify(mockUploaderA).upload(
            fakeContext,
            batchAData,
            batchAMetadata
        )
        verify(mockUploaderB).upload(
            fakeContext,
            batchBData,
            batchBMetadata
        )

        verify(batchAConfirmation).markAsRead(true)
        verify(batchBConfirmation).markAsRead(true)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send and keep batches 𝕎 doWork() {single batch per feature with error}`(
        status: UploadStatus,
        @StringForgery batchA: List<String>,
        @StringForgery batchAMeta: String,
        @StringForgery batchB: List<String>,
        @StringForgery batchBMeta: String,
        forge: Forge
    ) {
        // Given
        val batchAData = batchA.map { it.toByteArray() }
        val batchBData = batchB.map { it.toByteArray() }
        val batchAMetadata = forge.aNullable { batchAMeta.toByteArray() }
        val batchBMetadata = forge.aNullable { batchBMeta.toByteArray() }

        val batchAConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageA,
            mockBatchReaderA,
            mock(),
            batchAConfirmation,
            batchAData,
            batchAMetadata
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchBData,
            batchBMetadata
        )

        whenever(
            mockUploaderA.upload(
                fakeContext,
                batchAData,
                batchAMetadata
            )
        ) doReturn status
        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchBData,
                batchBMetadata
            )
        ) doReturn status

        // When
        val result = testedWorker.doWork()

        // Then
        verify(mockUploaderA).upload(
            fakeContext,
            batchAData,
            batchAMetadata
        )
        verify(mockUploaderB).upload(
            fakeContext,
            batchBData,
            batchBMetadata
        )
        verify(batchAConfirmation).markAsRead(false)
        verify(batchBConfirmation).markAsRead(false)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `𝕄 send batches 𝕎 doWork() {multiple batches, all Success}`(forge: Forge) {
        // Given
        val batchesA = forge.aList {
            forge.aList { forge.aString().toByteArray() }
        }
        val batchesAMeta = forge.aList(batchesA.size) {
            forge.aNullable { forge.aString().toByteArray() }
        }
        val aIds = forge.aList(batchesA.size) { mock<BatchId>() }
        val aConfirmations = forge.aList(batchesA.size) { mock<BatchConfirmation>() }
        val aReaders = forge.aList(batchesA.size) { mock<BatchReader>() }

        val batchB = forge.aList { forge.aString().toByteArray() }
        val batchBMeta = forge.aString().toByteArray()

        stubMultipleReadSequence(
            mockStorageA,
            aReaders,
            aIds,
            aConfirmations,
            batchesA,
            batchesAMeta
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchB,
            batchBMeta
        )

        batchesA.forEachIndexed { index, batch ->
            whenever(
                mockUploaderA.upload(
                    fakeContext,
                    batch,
                    batchesAMeta[index]
                )
            ) doReturn UploadStatus.SUCCESS
        }

        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMeta
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        val result = testedWorker.doWork()

        // Then
        batchesA.forEachIndexed { index, batch ->
            verify(mockUploaderA).upload(
                fakeContext,
                batch,
                batchesAMeta[index]
            )

            verify(aConfirmations[index]).markAsRead(true)
        }

        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMeta
        )
        verify(batchBConfirmation).markAsRead(true)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `𝕄 send batches 𝕎 doWork() {multiple batches, all Success, async storage}`(forge: Forge) {
        // Given
        val batchesA = forge.aList {
            forge.aList { forge.aString().toByteArray() }
        }
        val batchesAMeta = forge.aList(batchesA.size) {
            forge.aNullable { forge.aString().toByteArray() }
        }
        val aIds = forge.aList(batchesA.size) { mock<BatchId>() }
        val aConfirmations = forge.aList(batchesA.size) { mock<BatchConfirmation>() }
        val aReaders = forge.aList(batchesA.size) { mock<BatchReader>() }

        val batchB = forge.aList { forge.aString().toByteArray() }
        val batchBMeta = forge.aString().toByteArray()

        stubMultipleReadSequence(
            mockStorageA,
            aReaders,
            aIds,
            aConfirmations,
            batchesA,
            batchesAMeta
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchB,
            batchBMeta
        )

        val executorService = Executors.newSingleThreadExecutor()
        whenever(mockFeatureA.storage) doReturn AsyncStorageDelegate(mockStorageA, executorService)
        whenever(mockFeatureB.storage) doReturn AsyncStorageDelegate(mockStorageB, executorService)

        batchesA.forEachIndexed { index, batch ->
            whenever(
                mockUploaderA.upload(
                    fakeContext,
                    batch,
                    batchesAMeta[index]
                )
            ) doReturn UploadStatus.SUCCESS
        }

        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMeta
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        val result = testedWorker.doWork()

        // Then
        batchesA.forEachIndexed { index, batch ->
            verify(mockUploaderA).upload(
                fakeContext,
                batch,
                batchesAMeta[index]
            )

            verify(aConfirmations[index]).markAsRead(true)
        }

        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMeta
        )
        verify(batchBConfirmation).markAsRead(true)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())

        executorService.shutdown()
    }

    @ParameterizedTest
    @EnumSource(UploadStatus::class, names = ["SUCCESS"], mode = EnumSource.Mode.EXCLUDE)
    fun `𝕄 send batches 𝕎 doWork() {multiple batches, some fails}`(
        failingStatus: UploadStatus,
        forge: Forge
    ) {
        // Given
        val batchesA = forge.aList {
            forge.aList { forge.aString().toByteArray() }
        }
        val batchesAMeta = forge.aList(batchesA.size) {
            forge.aNullable { forge.aString().toByteArray() }
        }
        val aIds = forge.aList(batchesA.size) { mock<BatchId>() }
        val aConfirmations = forge.aList(batchesA.size) { mock<BatchConfirmation>() }
        val aReaders = forge.aList(batchesA.size) { mock<BatchReader>() }

        val batchB = forge.aList { forge.aString().toByteArray() }
        val batchBMeta = forge.aString().toByteArray()

        val failingBatchIndex = forge.anInt(min = 0, max = batchesA.size)

        stubMultipleReadSequence(
            mockStorageA,
            aReaders,
            aIds,
            aConfirmations,
            batchesA,
            batchesAMeta
        )

        val batchBConfirmation = mock<BatchConfirmation>()
        stubReadSequence(
            mockStorageB,
            mockBatchReaderB,
            mock(),
            batchBConfirmation,
            batchB,
            batchBMeta
        )

        batchesA.forEachIndexed { index, batch ->
            whenever(
                mockUploaderA.upload(
                    fakeContext,
                    batch,
                    batchesAMeta[index]
                )
            ) doReturn if (index == failingBatchIndex) failingStatus else UploadStatus.SUCCESS
        }

        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMeta
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        val result = testedWorker.doWork()

        // Then
        batchesA.forEachIndexed { index, batch ->
            verify(mockUploaderA).upload(
                fakeContext,
                batch,
                batchesAMeta[index]
            )

            if (index != failingBatchIndex) {
                verify(aConfirmations[index]).markAsRead(true)
            } else {
                verify(aConfirmations[index]).markAsRead(false)
            }
        }

        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMeta
        )
        verify(batchBConfirmation).markAsRead(true)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `𝕄 log error 𝕎 doWork() { SDK is not initialized }`() {
        // Given
        Datadog.registry.clear()

        // When
        val result = testedWorker.doWork()

        // Then
        logger.mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UploadWorker.MESSAGE_NOT_INITIALIZED
        )
        verifyNoInteractions(mockFeatureA, mockBatchReaderA, mockUploaderA)
        verifyNoInteractions(mockFeatureB, mockBatchReaderB, mockUploaderB)

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    // endregion

    // region private

    private fun stubFeatures(
        core: InternalSdkCore,
        features: List<SdkFeature>,
        storages: List<Storage>,
        uploaders: List<DataUploader>
    ) {
        whenever(core.getAllFeatures()) doReturn features
        features.forEachIndexed { index, feature ->
            whenever(feature.uploader) doReturn uploaders[index]
            whenever(feature.storage) doReturn storages[index]
        }
    }

    private fun stubReadSequence(
        storage: Storage,
        batchReader: BatchReader,
        batchId: BatchId,
        batchConfirmation: BatchConfirmation,
        batch: List<ByteArray>,
        batchMetadata: ByteArray?
    ) {
        stubMultipleReadSequence(
            storage,
            listOf(batchReader),
            listOf(batchId),
            listOf(batchConfirmation),
            listOf(batch),
            listOf(batchMetadata)
        )
    }

    private fun stubMultipleReadSequence(
        storage: Storage,
        batchReaders: List<BatchReader>,
        batchIds: List<BatchId>,
        batchConfirmations: List<BatchConfirmation>,
        batches: List<List<ByteArray>>,
        batchMetadata: List<ByteArray?>
    ) {
        whenever(storage.readNextBatch(any(), any()))
            .thenAnswer(object : Answer<Unit> {
                var invocationCount: Int = 0

                override fun answer(invocation: InvocationOnMock) {
                    if (invocationCount >= batches.size) {
                        (invocation.getArgument<() -> Unit>(0)).invoke()
                        return
                    }

                    val reader = batchReaders[invocationCount]
                    val batchId = batchIds[invocationCount]
                    val batchConfirmation = batchConfirmations[invocationCount]

                    whenever(reader.read()) doReturn batches[invocationCount]
                    whenever(reader.currentMetadata()) doReturn batchMetadata[invocationCount]

                    invocationCount++

                    whenever(storage.confirmBatchRead(eq(batchId), any())) doAnswer {
                        (it.getArgument<(BatchConfirmation) -> Unit>(1)).invoke(batchConfirmation)
                    }

                    (invocation.getArgument<(BatchId, BatchReader) -> Unit>(1)).invoke(
                        batchId,
                        reader
                    )
                }
            })
    }

    private class AsyncStorageDelegate(
        private val delegate: Storage,
        private val executor: Executor
    ) : Storage {
        override fun writeCurrentBatch(
            datadogContext: DatadogContext,
            forceNewBatch: Boolean,
            callback: (EventBatchWriter) -> Unit
        ) {
            fail("we don't expect this one to be called")
        }

        override fun readNextBatch(
            noBatchCallback: () -> Unit,
            batchCallback: (BatchId, BatchReader) -> Unit
        ) {
            executor.execute {
                delegate.readNextBatch(
                    noBatchCallback,
                    batchCallback
                )
            }
        }

        override fun confirmBatchRead(batchId: BatchId, callback: (BatchConfirmation) -> Unit) {
            executor.execute { delegate.confirmBatchRead(batchId, callback) }
        }

        override fun dropAll() {
            fail("we don't expect this one to be called")
        }
    }

    private fun WorkerParameters.copyWith(
        inputData: Data
    ): WorkerParameters {
        return WorkerParameters(
            id,
            inputData,
            tags,
            runtimeExtras,
            runAttemptCount,
            generation,
            backgroundExecutor,
            taskExecutor,
            workerFactory,
            progressUpdater,
            foregroundUpdater
        )
    }

    // endregion

    companion object {
        val logger = InternalLoggerTestConfiguration()
        val appContext = ApplicationContextTestConfiguration(Context::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, logger)
        }
    }
}
