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
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.data.upload.UploadStatus.Companion.UNKNOWN_RESPONSE_CODE
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.persistence.BatchData
import com.datadog.android.core.internal.persistence.BatchId
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer

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

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @StringForgery
    lateinit var fakeInstanceName: String

    @Forgery
    lateinit var fakeWorkerParameters: WorkerParameters

    var fakeFeaturesCount: Int = 0

    lateinit var mockFeatures: List<SdkFeature>

    lateinit var mockUploaders: List<DataUploader>

    lateinit var mockStorages: List<Storage>

    lateinit var fakeFeatureBatches: List<List<List<RawBatchEvent>>>

    lateinit var fakeFeatureBatchIds: List<List<BatchId>>

    lateinit var fakeFeatureBatchMetadata: List<List<ByteArray?>>

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockSdkCore.getDatadogContext()) doReturn fakeDatadogContext
        Datadog.registry.register(fakeInstanceName, mockSdkCore)

        val fakeData = Data.Builder()
            .putString(UploadWorker.DATADOG_INSTANCE_NAME, fakeInstanceName)
            .build()
        fakeWorkerParameters = fakeWorkerParameters.copyWith(fakeData)

        fakeFeaturesCount = forge.anInt(2, 8)
        createFakeBatches(forge)
        stubFeaturesStorage()

        testedWorker = UploadWorker(
            appContext.mockInstance,
            fakeWorkerParameters
        )
    }

    @AfterEach
    fun `tear down`() {
        Datadog.registry.clear()
    }

    // region setup

    private fun createFakeBatches(forge: Forge) {
        fakeFeatureBatches = List(fakeFeaturesCount) {
            forge.aList { forge.aList { forge.getForgery() } }
        }

        fakeFeatureBatchIds = List(fakeFeaturesCount) { featureIndex ->
            forge.aList(fakeFeatureBatches[featureIndex].size) { BatchId(forge.aString()) }
        }

        fakeFeatureBatchMetadata = List(fakeFeaturesCount) { featureIndex ->
            forge.aList(fakeFeatureBatches[featureIndex].size) { forge.aNullable { aString().toByteArray() } }
        }
    }

    private fun stubFeaturesStorage() {
        mockFeatures = List(fakeFeaturesCount) { mock() }
        mockUploaders = List(fakeFeaturesCount) { mock() }
        mockStorages = List(fakeFeaturesCount) { mock() }

        whenever(mockSdkCore.getAllFeatures()) doReturn mockFeatures

        mockFeatures.forEachIndexed { featureIndex, feature ->
            whenever(feature.uploader) doReturn mockUploaders[featureIndex]
            whenever(feature.storage) doReturn mockStorages[featureIndex]

            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]
            val fakeBatchMetadata = fakeFeatureBatchMetadata[featureIndex]

            val batchesCount = fakeBatches.size
            whenever(mockStorages[featureIndex].readNextBatch())
                .thenAnswer(object : Answer<BatchData?> {
                    var invocationCount: Int = 0

                    override fun answer(invocation: InvocationOnMock): BatchData? {
                        if (invocationCount >= batchesCount) {
                            return null
                        }
                        val fakeBatch = fakeBatches[invocationCount]
                        val fakeBatchId = fakeBatchIds[invocationCount]
                        val fakeMetadata = fakeBatchMetadata[invocationCount]
                        invocationCount++

                        return BatchData(
                            id = fakeBatchId,
                            data = fakeBatch,
                            metadata = fakeMetadata
                        )
                    }
                })
        }
    }

    private fun stubFeaturesUploaders(
        successStatusCode: Int = 202,
        successfulUntilIdx: Int = Int.MAX_VALUE,
        secondaryStatus: UploadStatus = UploadStatus.UnknownStatus
    ) {
        mockFeatures.forEachIndexed { featureIndex, _ ->
            val mockUploader = mockUploaders[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeBatchMetadata = fakeFeatureBatchMetadata[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, batch ->
                val status = if (batchIndex < successfulUntilIdx) {
                    UploadStatus.Success(successStatusCode)
                } else {
                    secondaryStatus
                }
                whenever(
                    mockUploader.upload(
                        fakeDatadogContext,
                        batch,
                        fakeBatchMetadata[batchIndex]
                    )
                ) doReturn status
            }
        }
    }

    // endregion

    // region doWork

    @Test
    fun `M send all batches W doWork() {all success}`(
        @IntForgery(200, 300) successStatusCode: Int
    ) {
        // Given
        stubFeaturesUploaders(successStatusCode)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                verify(mockUploader).upload(
                    context = fakeDatadogContext,
                    batch = fakeBatch,
                    batchMeta = fakeMetadata[batchIndex]
                )
                verify(mockStorage).confirmBatchRead(
                    batchId = fakeBatchIds[batchIndex],
                    removalReason = RemovalReason.IntakeCode(successStatusCode),
                    deleteBatch = true
                )
            }
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {unauthorized}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @IntForgery(400, 499) failureStatusCode: Int

    ) {
        // Given
        val failingStatus = UploadStatus.InvalidTokenError(failureStatusCode)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(failureStatusCode),
                        deleteBatch = true
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {rate limiting}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @IntForgery(400, 499) failureStatusCode: Int
    ) {
        // Given
        val failingStatus = UploadStatus.HttpClientRateLimiting(failureStatusCode)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(failureStatusCode),
                        deleteBatch = false
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {client error}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @IntForgery(400, 499) failureStatusCode: Int
    ) {
        // Given
        val failingStatus = UploadStatus.HttpClientError(failureStatusCode)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(failureStatusCode),
                        deleteBatch = true
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {server error}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @IntForgery(400, 499) failureStatusCode: Int
    ) {
        // Given
        val failingStatus = UploadStatus.HttpServerError(failureStatusCode)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(failureStatusCode),
                        deleteBatch = false
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {redirection}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @IntForgery(300, 399) failureStatusCode: Int
    ) {
        // Given
        val failingStatus = UploadStatus.HttpRedirection(failureStatusCode)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(failureStatusCode),
                        deleteBatch = true
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {unknown http error}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @IntForgery(400, 499) failureStatusCode: Int
    ) {
        // Given
        val failingStatus = UploadStatus.UnknownHttpError(failureStatusCode)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(failureStatusCode),
                        deleteBatch = true
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {network error}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @Forgery throwable: Exception
    ) {
        // Given
        val failingStatus = UploadStatus.NetworkError(throwable)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(UNKNOWN_RESPONSE_CODE),
                        deleteBatch = false
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {dns error}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @Forgery throwable: Exception
    ) {
        // Given
        val failingStatus = UploadStatus.DNSError(throwable)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(UNKNOWN_RESPONSE_CODE),
                        deleteBatch = false
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {request creation error}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @Forgery throwable: Exception
    ) {
        // Given
        val failingStatus = UploadStatus.RequestCreationError(throwable)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(UNKNOWN_RESPONSE_CODE),
                        deleteBatch = true
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {unknown exception}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int,
        @Forgery throwable: Exception
    ) {
        // Given
        val failingStatus = UploadStatus.UnknownException(throwable)
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(UNKNOWN_RESPONSE_CODE),
                        deleteBatch = false
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    @Test
    fun `M send all batches until failure W doWork() {unknown status}`(
        @IntForgery(200, 300) successStatusCode: Int,
        @IntForgery(0, 8) successfulBatchCount: Int
    ) {
        // Given
        val failingStatus = UploadStatus.UnknownStatus
        stubFeaturesUploaders(successStatusCode, successfulBatchCount, failingStatus)

        // When
        val result = testedWorker.doWork()

        // Then
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        repeat(fakeFeaturesCount) { featureIndex ->
            val mockUploader = mockUploaders[featureIndex]
            val mockStorage = mockStorages[featureIndex]
            val fakeBatches = fakeFeatureBatches[featureIndex]
            val fakeMetadata = fakeFeatureBatchMetadata[featureIndex]
            val fakeBatchIds = fakeFeatureBatchIds[featureIndex]

            fakeBatches.forEachIndexed { batchIndex, fakeBatch ->
                // n successful batches
                if (batchIndex < successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(successStatusCode),
                        deleteBatch = true
                    )
                }

                // failing batch
                if (batchIndex == successfulBatchCount) {
                    verify(mockUploader).upload(
                        context = fakeDatadogContext,
                        batch = fakeBatch,
                        batchMeta = fakeMetadata[batchIndex]
                    )
                    verify(mockStorage).confirmBatchRead(
                        batchId = fakeBatchIds[batchIndex],
                        removalReason = RemovalReason.IntakeCode(UNKNOWN_RESPONSE_CODE),
                        deleteBatch = true
                    )
                }
            }

            verifyNoMoreInteractions(mockUploader)
        }
    }

    // endregion

    // region Internal

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
        fun errorWithRetryStatusValues(): List<UploadStatus> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            return listOf(
                forge.getForgery(UploadStatus.Success::class.java),

                forge.getForgery(UploadStatus.InvalidTokenError::class.java),
                forge.getForgery(UploadStatus.HttpClientRateLimiting::class.java),
                forge.getForgery(UploadStatus.HttpClientError::class.java),
                forge.getForgery(UploadStatus.HttpServerError::class.java),
                forge.getForgery(UploadStatus.HttpRedirection::class.java),
                forge.getForgery(UploadStatus.UnknownHttpError::class.java),

                forge.getForgery(UploadStatus.NetworkError::class.java),
                forge.getForgery(UploadStatus.DNSError::class.java),
                forge.getForgery(UploadStatus.RequestCreationError::class.java),

                forge.getForgery(UploadStatus.UnknownException::class.java),
                forge.getForgery(UploadStatus.UnknownStatus::class.java)
            )
        }
    }
}
