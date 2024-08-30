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
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.persistence.BatchData
import com.datadog.android.core.internal.persistence.BatchId
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
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
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer
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
    lateinit var mockUploaderA: DataUploader

    @Mock
    lateinit var mockFeatureB: SdkFeature

    @Mock
    lateinit var mockStorageB: Storage

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
    fun `M send batches W doWork() {single batch per feature}`(
        @Forgery batchA: List<RawBatchEvent>,
        @StringForgery batchAMeta: String,
        @Forgery batchB: List<RawBatchEvent>,
        @StringForgery batchBMeta: String,
        forge: Forge
    ) {
        // Given
        val batchAMetadata = forge.aNullable { batchAMeta.toByteArray() }
        val batchBMetadata = forge.aNullable { batchBMeta.toByteArray() }

        val batchId1 = mock<BatchId>()
        val batchId2 = mock<BatchId>()
        stubReadSequence(
            mockStorageA,
            batchId1,
            batchA,
            batchAMetadata
        )

        stubReadSequence(
            mockStorageB,
            batchId2,
            batchB,
            batchBMetadata
        )

        val uploadStatus1 = forge.getForgery(UploadStatus.Success::class.java)
        val uploadStatus2 = forge.getForgery(UploadStatus.Success::class.java)
        whenever(
            mockUploaderA.upload(
                fakeContext,
                batchA,
                batchAMetadata
            )
        ) doReturn uploadStatus1
        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMetadata
            )
        ) doReturn uploadStatus2

        // When
        val result = testedWorker.doWork()

        // Then
        verify(mockUploaderA).upload(
            fakeContext,
            batchA,
            batchAMetadata
        )
        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMetadata
        )

        verify(mockStorageA).confirmBatchRead(
            eq(batchId1),
            argThat { this.toString() == "intake-code-${uploadStatus1.code}" },
            eq(true)
        )
        verify(mockStorageB).confirmBatchRead(
            eq(batchId2),
            argThat { this.toString() == "intake-code-${uploadStatus2.code}" },
            eq(true)
        )

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @ParameterizedTest
    @MethodSource("errorStatusValues")
    fun `M send and keep batches W doWork() {single batch per feature with error}`(
        status: UploadStatus,
        @Forgery batchA: List<RawBatchEvent>,
        @StringForgery batchAMeta: String,
        @Forgery batchB: List<RawBatchEvent>,
        @StringForgery batchBMeta: String,
        forge: Forge
    ) {
        // Given
        val batchAMetadata = forge.aNullable { batchAMeta.toByteArray() }
        val batchBMetadata = forge.aNullable { batchBMeta.toByteArray() }

        val batchId1 = mock<BatchId>()
        stubReadSequence(
            mockStorageA,
            batchId1,
            batchA,
            batchAMetadata
        )

        val batchId2 = mock<BatchId>()
        stubReadSequence(
            mockStorageB,
            batchId2,
            batchB,
            batchBMetadata
        )

        whenever(
            mockUploaderA.upload(
                fakeContext,
                batchA,
                batchAMetadata
            )
        ) doReturn status
        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMetadata
            )
        ) doReturn status

        // When
        val result = testedWorker.doWork()

        // Then
        verify(mockUploaderA).upload(
            fakeContext,
            batchA,
            batchAMetadata
        )
        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMetadata
        )
        verify(mockStorageA).confirmBatchRead(
            eq(batchId1),
            argThat { this.toString() == "intake-code-${status.code}" },
            eq(!status.shouldRetry)
        )
        verify(mockStorageB).confirmBatchRead(
            eq(batchId2),
            argThat { this.toString() == "intake-code-${status.code}" },
            eq(!status.shouldRetry)
        )

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `M send batches W doWork() {multiple batches, all Success}`(forge: Forge) {
        // Given
        val batchesA = forge.aList {
            aList { RawBatchEvent(aString().toByteArray()) }
        }
        val batchesAMeta = forge.aList(batchesA.size) {
            forge.aNullable { forge.aString().toByteArray() }
        }
        val aIds = forge.aList(batchesA.size) { mock<BatchId>() }

        val batchB = forge.aList { RawBatchEvent(aString().toByteArray()) }
        val batchBMeta = forge.aString().toByteArray()

        stubMultipleReadSequence(
            mockStorageA,
            aIds,
            batchesA,
            batchesAMeta
        )

        val batchId = mock<BatchId>()
        stubReadSequence(
            mockStorageB,
            batchId,
            batchB,
            batchBMeta
        )

        val aStatuses = batchesA.map { forge.getForgery(UploadStatus.Success::class.java) }
        batchesA.forEachIndexed { index, batch ->
            whenever(
                mockUploaderA.upload(
                    fakeContext,
                    batch,
                    batchesAMeta[index]
                )
            ) doReturn aStatuses[index]
        }

        val successStatus = forge.getForgery(UploadStatus.Success::class.java)
        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMeta
            )
        ) doReturn successStatus

        // When
        val result = testedWorker.doWork()

        // Then
        batchesA.forEachIndexed { index, batch ->
            verify(mockUploaderA).upload(
                fakeContext,
                batch,
                batchesAMeta[index]
            )
            verify(mockStorageA).confirmBatchRead(
                eq(aIds[index]),
                argThat { this.toString() == "intake-code-${aStatuses[index].code}" },
                eq(true)
            )
        }

        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMeta
        )
        verify(mockStorageB).confirmBatchRead(
            eq(batchId),
            argThat { this.toString() == "intake-code-${successStatus.code}" },
            eq(true)
        )
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `M send batches W doWork() {multiple batches, all Success, async storage}`(forge: Forge) {
        // Given
        val batchesA = forge.aList {
            aList { RawBatchEvent(aString().toByteArray()) }
        }
        val batchesAMeta = forge.aList(batchesA.size) {
            aNullable { aString().toByteArray() }
        }
        val aIds = forge.aList(batchesA.size) { mock<BatchId>() }

        val batchB = forge.aList { RawBatchEvent(aString().toByteArray()) }
        val batchBMeta = forge.aString().toByteArray()
        val aStatuses = batchesA.map { forge.getForgery(UploadStatus.Success::class.java) }

        stubMultipleReadSequence(
            mockStorageA,
            aIds,
            batchesA,
            batchesAMeta
        )

        val batchId = mock<BatchId>()
        stubReadSequence(
            mockStorageB,
            batchId,
            batchB,
            batchBMeta
        )

        val executorService = Executors.newSingleThreadExecutor()
        whenever(mockFeatureA.storage) doReturn StorageDelegate(mockStorageA)
        whenever(mockFeatureB.storage) doReturn StorageDelegate(mockStorageB)

        batchesA.forEachIndexed { index, batch ->
            whenever(
                mockUploaderA.upload(
                    fakeContext,
                    batch,
                    batchesAMeta[index]
                )
            ) doReturn aStatuses[index]
        }

        val successStatus = forge.getForgery(UploadStatus.Success::class.java)
        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMeta
            )
        ) doReturn successStatus

        // When
        val result = testedWorker.doWork()

        // Then
        batchesA.forEachIndexed { index, batch ->
            verify(mockUploaderA).upload(
                fakeContext,
                batch,
                batchesAMeta[index]
            )
            verify(mockStorageA).confirmBatchRead(
                eq(aIds[index]),
                argThat { this.toString() == "intake-code-${aStatuses[index].code}" },
                eq(true)
            )
        }

        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMeta
        )
        verify(mockStorageB).confirmBatchRead(
            eq(batchId),
            argThat { this.toString() == "intake-code-${successStatus.code}" },
            eq(true)
        )

        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())

        executorService.shutdown()
    }

    @ParameterizedTest
    @MethodSource("errorStatusValues")
    fun `M send batches W doWork() {multiple batches, some fails with retry}`(
        failingStatus: UploadStatus,
        forge: Forge
    ) {
        // Given
        val batchesA = forge.aList {
            aList { RawBatchEvent(aString().toByteArray()) }
        }
        val batchesAMeta = forge.aList(batchesA.size) {
            aNullable { aString().toByteArray() }
        }
        val aIds = forge.aList(batchesA.size) { mock<BatchId>() }

        val batchB = forge.aList { RawBatchEvent(aString().toByteArray()) }
        val batchBMeta = forge.aString().toByteArray()

        val failingBatchIndex = forge.anInt(min = 0, max = batchesA.size)
        val aStatuses = List(batchesA.size) { index ->
            if (index == failingBatchIndex) {
                failingStatus
            } else {
                forge.getForgery(UploadStatus.Success::class.java)
            }
        }

        stubMultipleReadSequence(
            mockStorageA,
            aIds,
            batchesA,
            batchesAMeta
        )

        val batchId = mock<BatchId>()
        stubReadSequence(
            mockStorageB,
            batchId,
            batchB,
            batchBMeta
        )

        val fakeUploadSuccess2 = forge.getForgery(UploadStatus.Success::class.java)
        batchesA.forEachIndexed { index, batch ->
            whenever(
                mockUploaderA.upload(
                    fakeContext,
                    batch,
                    batchesAMeta[index]
                )
            ) doReturn aStatuses[index]
        }

        whenever(
            mockUploaderB.upload(
                fakeContext,
                batchB,
                batchBMeta
            )
        ) doReturn fakeUploadSuccess2

        // When
        val result = testedWorker.doWork()

        // Then
        batchesA.forEachIndexed { index, batch ->
            verify(mockUploaderA).upload(
                fakeContext,
                batch,
                batchesAMeta[index]
            )

            if (index == failingBatchIndex) {
                verify(mockStorageA).confirmBatchRead(
                    eq(aIds[index]),
                    argThat { this.toString() == "intake-code-${aStatuses[index].code}" },
                    eq(!failingStatus.shouldRetry)
                )
            } else {
                verify(mockStorageA).confirmBatchRead(
                    eq(aIds[index]),
                    argThat { this.toString() == "intake-code-${aStatuses[index].code}" },
                    eq(true)
                )
            }
        }

        verify(mockUploaderB).upload(
            fakeContext,
            batchB,
            batchBMeta
        )
        verify(mockStorageB).confirmBatchRead(
            eq(batchId),
            argThat { this.toString() == "intake-code-${fakeUploadSuccess2.code}" },
            eq(true)
        )
        assertThat(result)
            .isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `M log error W doWork() { SDK is not initialized }`() {
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
        verifyNoInteractions(mockFeatureA, mockUploaderA)
        verifyNoInteractions(mockFeatureB, mockUploaderB)

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
        batchId: BatchId,
        batch: List<RawBatchEvent>,
        batchMetadata: ByteArray?
    ) {
        stubMultipleReadSequence(
            storage,
            listOf(batchId),
            listOf(batch),
            listOf(batchMetadata)
        )
    }

    private fun stubMultipleReadSequence(
        storage: Storage,
        batchIds: List<BatchId>,
        batches: List<List<RawBatchEvent>>,
        batchMetadata: List<ByteArray?>
    ) {
        whenever(storage.readNextBatch())
            .thenAnswer(object : Answer<BatchData?> {
                var invocationCount: Int = 0

                override fun answer(invocation: InvocationOnMock): BatchData? {
                    if (invocationCount >= batches.size) {
                        return null
                    }

                    val batchId = batchIds[invocationCount]
                    return BatchData(
                        id = batchId,
                        data = batches[invocationCount],
                        metadata = batchMetadata[invocationCount++]
                    )
                }
            })
    }

    private class StorageDelegate(
        private val delegate: Storage
    ) : Storage {
        override fun writeCurrentBatch(
            datadogContext: DatadogContext,
            forceNewBatch: Boolean,
            callback: (EventBatchWriter) -> Unit
        ) {
            fail("we don't expect this one to be called")
        }

        override fun readNextBatch(): BatchData? {
            return delegate.readNextBatch()
        }

        override fun confirmBatchRead(
            batchId: BatchId,
            removalReason: RemovalReason,
            deleteBatch: Boolean
        ) {
            delegate.confirmBatchRead(batchId, removalReason, deleteBatch)
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

        @JvmStatic
        fun errorStatusValues(): List<UploadStatus> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            return listOf(
                forge.getForgery(UploadStatus.HttpClientError::class.java),
                forge.getForgery(UploadStatus.HttpClientRateLimiting::class.java),
                forge.getForgery(UploadStatus.HttpRedirection::class.java),
                forge.getForgery(UploadStatus.HttpServerError::class.java),
                forge.getForgery(UploadStatus.InvalidTokenError::class.java),
                forge.getForgery(UploadStatus.NetworkError::class.java),
                forge.getForgery(UploadStatus.RequestCreationError::class.java),
                forge.getForgery(UploadStatus.UnknownException::class.java),
                forge.getForgery(UploadStatus.UnknownHttpError::class.java),
                forge.getForgery(UploadStatus.UnknownStatus::class.java)
            )
        }
    }
}
