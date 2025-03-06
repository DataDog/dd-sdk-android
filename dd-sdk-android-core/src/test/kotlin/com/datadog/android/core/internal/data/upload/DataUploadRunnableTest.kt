/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.configuration.UploadSchedulerStrategy
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.BatchData
import com.datadog.android.core.internal.persistence.BatchId
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.system.SystemInfo
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.internal.telemetry.UploadQualityBlockers
import com.datadog.android.internal.telemetry.UploadQualityCategories
import com.datadog.android.internal.telemetry.UploadQualityEvent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mockito.stubbing.Answer
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataUploadRunnableTest {

    @Mock
    lateinit var mockThreadPoolExecutor: ScheduledThreadPoolExecutor

    @Mock
    lateinit var mockStorage: Storage

    @Mock
    lateinit var mockDataUploader: DataUploader

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockUploadSchedulerStrategy: UploadSchedulerStrategy

    @Forgery
    lateinit var fakeContext: DatadogContext

    @IntForgery(min = 1, max = 4)
    var fakeMaxBatchesPerJob: Int = 0

    @LongForgery
    var fakeDelayUntilNextUploadMs: Long = 0L

    @StringForgery
    lateinit var fakeFeatureName: String

    private lateinit var testedRunnable: DataUploadRunnable

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeNetworkInfo = NetworkInfo(
            forge.aValueFrom(
                enumClass = NetworkInfo.Connectivity::class.java,
                exclude = listOf(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
            )
        )
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        val fakeSystemInfo = SystemInfo(
            batteryFullOrCharging = true,
            batteryLevel = forge.anInt(min = 20, max = 100),
            powerSaveMode = false,
            onExternalPowerSource = true
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo
        whenever(mockUploadSchedulerStrategy.getMsDelayUntilNextUpload(any(), any(), anyOrNull(), anyOrNull()))
            .doReturn(fakeDelayUntilNextUploadMs)

        whenever(mockContextProvider.context) doReturn fakeContext

        testedRunnable = DataUploadRunnable(
            featureSdkCore = mockFeatureSdkCore,
            featureName = fakeFeatureName,
            threadPoolExecutor = mockThreadPoolExecutor,
            storage = mockStorage,
            dataUploader = mockDataUploader,
            contextProvider = mockContextProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `doesn't send batch when offline`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
        )
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn networkInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockDataUploader, mockStorage)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(fakeFeatureName, 0, null, null)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M send batch W run() { batteryFullOrCharging }`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @IntForgery(min = 0, max = DataUploadRunnable.LOW_BATTERY_THRESHOLD) batteryLevel: Int,
        forge: Forge
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryFullOrCharging = true,
            batteryLevel = batteryLevel,
            onExternalPowerSource = false,
            powerSaveMode = false
        )

        val batchId = mock<BatchId>()
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(mockStorage.readNextBatch()).thenReturn(BatchData(batchId, batch, batchMetadata))
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        val fakeUploadStatus = forge.getForgery(UploadStatus.Success::class.java)
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batch,
                batchMetadata,
                batchId
            )
        ) doReturn fakeUploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage, times(fakeMaxBatchesPerJob)).confirmBatchRead(
            eq(batchId),
            any(),
            eq(true)
        )
        verify(mockDataUploader, times(fakeMaxBatchesPerJob)).upload(fakeContext, batch, batchMetadata, batchId)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(
            fakeFeatureName,
            fakeMaxBatchesPerJob,
            fakeUploadStatus.code,
            null
        )
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M send batch W run() { battery level high }`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @IntForgery(min = DataUploadRunnable.LOW_BATTERY_THRESHOLD + 1) batteryLevel: Int,
        forge: Forge
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryLevel = batteryLevel,
            batteryFullOrCharging = false,
            onExternalPowerSource = false,
            powerSaveMode = false
        )
        val batchId = mock<BatchId>()
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(mockStorage.readNextBatch()).thenReturn(BatchData(batchId, batch, batchMetadata))
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        val fakeUploadStatus = forge.getForgery(UploadStatus.Success::class.java)
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batch,
                batchMetadata,
                batchId
            )
        ) doReturn fakeUploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage, times(fakeMaxBatchesPerJob)).confirmBatchRead(
            eq(batchId),
            any(),
            eq(true)
        )
        verify(mockDataUploader, times(fakeMaxBatchesPerJob)).upload(fakeContext, batch, batchMetadata, batchId)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(
            fakeFeatureName,
            fakeMaxBatchesPerJob,
            fakeUploadStatus.code,
            null
        )
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M send batch W run() { onExternalPower }`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @IntForgery(min = 0, max = DataUploadRunnable.LOW_BATTERY_THRESHOLD) batteryLevel: Int,
        forge: Forge
    ) {
        val fakeSystemInfo = SystemInfo(
            onExternalPowerSource = true,
            batteryLevel = batteryLevel,
            batteryFullOrCharging = false,
            powerSaveMode = false
        )
        val batchId = mock<BatchId>()
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(mockStorage.readNextBatch()).thenReturn(BatchData(batchId, batch, batchMetadata))
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        val fakeUploadStatus = forge.getForgery(UploadStatus.Success::class.java)
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batch,
                batchMetadata,
                batchId
            )
        ) doReturn fakeUploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage, times(fakeMaxBatchesPerJob)).confirmBatchRead(
            eq(batchId),
            any(),
            eq(true)
        )
        verify(mockDataUploader, times(fakeMaxBatchesPerJob)).upload(fakeContext, batch, batchMetadata, batchId)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(
            fakeFeatureName,
            fakeMaxBatchesPerJob,
            fakeUploadStatus.code,
            null
        )
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M not send batch W run() { not enough battery }`(
        @IntForgery(min = 0, max = DataUploadRunnable.LOW_BATTERY_THRESHOLD) batteryLevel: Int
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryLevel = batteryLevel,
            batteryFullOrCharging = false,
            onExternalPowerSource = false,
            powerSaveMode = false
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockStorage, mockDataUploader)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(fakeFeatureName, 0, null, null)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M not send batch W run() { batteryFullOrCharging, powerSaveMode }`(
        @IntForgery(min = 0, max = 100) batteryLevel: Int
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryFullOrCharging = true,
            powerSaveMode = true,
            batteryLevel = batteryLevel,
            onExternalPowerSource = false
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockStorage, mockDataUploader)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(fakeFeatureName, 0, null, null)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M not send batch W run() { batteryLeveHigh, powerSaveMode }`(
        @IntForgery(min = DataUploadRunnable.LOW_BATTERY_THRESHOLD + 1) batteryLevel: Int
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryLevel = batteryLevel,
            powerSaveMode = true,
            batteryFullOrCharging = false,
            onExternalPowerSource = false
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockStorage, mockDataUploader)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(fakeFeatureName, 0, null, null)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M not send batch W run() { onExternalPower, powerSaveMode }`(
        @IntForgery(min = 0, max = DataUploadRunnable.LOW_BATTERY_THRESHOLD) batteryLevel: Int
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            onExternalPowerSource = true,
            powerSaveMode = true,
            batteryLevel = batteryLevel,
            batteryFullOrCharging = false
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockStorage, mockDataUploader)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(fakeFeatureName, 0, null, null)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M do nothing W no batch to send`() {
        // Given
        whenever(mockStorage.readNextBatch()).thenReturn(null)

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage).readNextBatch()
        verifyNoMoreInteractions(mockStorage)
        verifyNoInteractions(mockDataUploader)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(fakeFeatureName, 0, null, null)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `batch sent successfully`(
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(mockStorage.readNextBatch()).thenReturn(BatchData(batchId, batch, batchMetadata))
        val fakeUploadStatus = forge.getForgery(UploadStatus.Success::class.java)
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batch,
                batchMetadata,
                batchId
            )
        ) doReturn fakeUploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage, times(fakeMaxBatchesPerJob)).confirmBatchRead(any(), any(), eq(true))
        verify(mockDataUploader, times(fakeMaxBatchesPerJob)).upload(fakeContext, batch, batchMetadata, batchId)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(
            fakeFeatureName,
            fakeMaxBatchesPerJob,
            fakeUploadStatus.code,
            null
        )
        verify(mockThreadPoolExecutor).remove(testedRunnable)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @ParameterizedTest
    @MethodSource("retryBatchStatusValues")
    fun `batch kept on error`(
        uploadStatus: UploadStatus,
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(mockStorage.readNextBatch()).thenReturn(BatchData(batchId, batch, batchMetadata))
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batch,
                batchMetadata,
                batchId
            )
        ) doReturn uploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage).confirmBatchRead(eq(batchId), any(), eq(false))
        verify(mockDataUploader).upload(fakeContext, batch, batchMetadata, batchId)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(
            fakeFeatureName,
            1,
            uploadStatus.code,
            uploadStatus.throwable
        )
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @ParameterizedTest
    @MethodSource("retryBatchStatusValues")
    fun `batch kept after n errors`(
        uploadStatus: UploadStatus,
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        @IntForgery(min = 3, max = 42) runCount: Int,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(mockStorage.readNextBatch()).thenReturn(BatchData(batchId, batch, batchMetadata))
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batch,
                batchMetadata,
                batchId
            )
        ) doReturn uploadStatus

        // WHen
        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        verify(mockStorage, times(runCount)).confirmBatchRead(eq(batchId), any(), eq(false))
        verify(mockDataUploader, times(runCount)).upload(fakeContext, batch, batchMetadata, batchId)
        verify(mockUploadSchedulerStrategy, times(runCount)).getMsDelayUntilNextUpload(
            fakeFeatureName,
            1,
            uploadStatus.code,
            uploadStatus.throwable
        )
        verify(mockThreadPoolExecutor, times(runCount))
            .schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @ParameterizedTest
    @MethodSource("dropBatchStatusValues")
    fun `batch dropped on error`(
        uploadStatus: UploadStatus,
        @Forgery batch: List<RawBatchEvent>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(mockStorage.readNextBatch()).thenReturn(BatchData(batchId, batch, batchMetadata))
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batch,
                batchMetadata,
                batchId
            )
        ) doReturn uploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage).confirmBatchRead(eq(batchId), any(), eq(true))
        verify(mockDataUploader).upload(fakeContext, batch, batchMetadata, batchId)
        verify(mockUploadSchedulerStrategy).getMsDelayUntilNextUpload(
            fakeFeatureName,
            1,
            uploadStatus.code,
            uploadStatus.throwable
        )
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    // region maxBatchesPerJob

    @Test
    fun `M handle the maxBatchesPerJob W run{maxBatchesPerJob smaller availableBatches}`(
        forge: Forge
    ) {
        // Given
        testedRunnable = DataUploadRunnable(
            featureSdkCore = mockFeatureSdkCore,
            featureName = fakeFeatureName,
            threadPoolExecutor = mockThreadPoolExecutor,
            storage = mockStorage,
            dataUploader = mockDataUploader,
            contextProvider = mockContextProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            internalLogger = mockInternalLogger
        )
        val batches = forge.aList(
            size = forge.anInt(
                min = fakeMaxBatchesPerJob + 1,
                max = fakeMaxBatchesPerJob + 1000
            )
        ) {
            aList { getForgery<RawBatchEvent>() }
        }
        val batchIds: List<BatchId> = batches.map { mock() }
        val batchMetadata = forge.aList(size = batches.size) { aNullable { aString().toByteArray() } }
        stubStorage(batchIds, batches, batchMetadata)
        batches.forEachIndexed { index, batch ->
            whenever(
                mockDataUploader.upload(
                    fakeContext,
                    batch,
                    batchMetadata[index],
                    batchIds[index]
                )
            ) doReturn forge.getForgery(UploadStatus.Success::class.java)
        }

        // When
        testedRunnable.run()

        // Then
        repeat(fakeMaxBatchesPerJob) { index ->
            val batch = batches[index]
            verify(mockDataUploader).upload(fakeContext, batch, batchMetadata[index], batchIds[index])
            verify(mockStorage).confirmBatchRead(
                eq(batchIds[index]),
                any(),
                eq(true)
            )
        }
        verifyNoMoreInteractions(mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `M exhaust the available batches W run {maxBatchesPerJob higher or equal availableBatches}`(
        forge: Forge
    ) {
        // Given
        testedRunnable = DataUploadRunnable(
            featureSdkCore = mockFeatureSdkCore,
            featureName = fakeFeatureName,
            threadPoolExecutor = mockThreadPoolExecutor,
            storage = mockStorage,
            dataUploader = mockDataUploader,
            contextProvider = mockContextProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            internalLogger = mockInternalLogger
        )
        val fakeBatchesCount = forge.anInt(
            min = 1,
            max = fakeMaxBatchesPerJob + 1
        )
        val batches = forge.aList(size = fakeBatchesCount) { aList { getForgery<RawBatchEvent>() } }
        val batchIds: List<BatchId> = batches.map { mock() }
        val batchMetadata = forge.aList(size = batches.size) { aNullable { aString().toByteArray() } }
        stubStorage(batchIds, batches, batchMetadata)
        val fakeUploadStatus = forge.getForgery(UploadStatus.Success::class.java)

        batches.forEachIndexed { index, batch ->
            whenever(
                mockDataUploader.upload(
                    fakeContext,
                    batch,
                    batchMetadata[index],
                    batchIds[index]
                )
            ) doReturn fakeUploadStatus
        }

        // When
        testedRunnable.run()

        // Then
        batches.forEachIndexed { index, batch ->
            verify(mockDataUploader).upload(fakeContext, batch, batchMetadata[index], batchIds[index])
            verify(mockStorage).confirmBatchRead(
                eq(batchIds[index]),
                any(),
                eq(true)
            )
        }
        verifyNoMoreInteractions(mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(testedRunnable, fakeDelayUntilNextUploadMs, TimeUnit.MILLISECONDS)
    }

    // endregion

    // region Internal

    private fun stubStorage(
        batchIds: List<BatchId>,
        batches: List<List<RawBatchEvent>>,
        batchMeta: List<ByteArray?>
    ) {
        reset(mockStorage)
        whenever(mockStorage.readNextBatch()) doAnswer object : Answer<BatchData?> {
            var index = 0

            override fun answer(invocation: InvocationOnMock): BatchData? {
                val data = if (index >= batches.size) {
                    null
                } else {
                    val batchData = BatchData(batchIds[index], batches[index], batchMeta[index])
                    batchData
                }
                index++
                return data
            }
        }
    }

    // endregion

    // region upload quality

    @Test
    fun `M send network failure upload quality event W run { response not 202 }`(forge: Forge) {
        // Given
        val mockRumFeature: FeatureScope = mock()
        whenever(mockFeatureSdkCore.getFeature(Feature.RUM_FEATURE_NAME))
            .thenReturn(mockRumFeature)
        testedRunnable = DataUploadRunnable(
            featureSdkCore = mockFeatureSdkCore,
            featureName = fakeFeatureName,
            threadPoolExecutor = mockThreadPoolExecutor,
            storage = mockStorage,
            dataUploader = mockDataUploader,
            contextProvider = mockContextProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            internalLogger = mockInternalLogger
        )
        val batches = forge.aList(size = 1) { aList { getForgery<RawBatchEvent>() } }
        val batchIds: List<BatchId> = batches.map { mock() }
        val batchMetadata = forge.aList(size = batches.size) { aNullable { aString().toByteArray() } }
        stubStorage(batchIds, batches, batchMetadata)
        val mockUploadStatus: UploadStatus = mock()
        whenever(mockUploadStatus.code).thenReturn(500)

        batches.forEachIndexed { index, batch ->
            whenever(
                mockDataUploader.upload(
                    fakeContext,
                    batch,
                    batchMetadata[index],
                    batchIds[index]
                )
            ) doReturn mockUploadStatus
        }

        // When
        testedRunnable.run()

        // Then
        argumentCaptor<UploadQualityEvent> {
            verify(mockRumFeature, times(2)).sendEvent(
                event = capture()
            )
            val firstEvent = firstValue as? UploadQualityEvent
            val secondEvent = secondValue as? UploadQualityEvent

            assertThat(firstEvent?.track).isEqualTo(fakeFeatureName)
            assertThat(firstEvent?.category).isEqualTo(UploadQualityCategories.FAILURE)
            assertThat(firstEvent?.specificType).isEqualTo(mockUploadStatus.code.toString())

            assertThat(secondEvent?.track).isEqualTo(fakeFeatureName)
            assertThat(secondEvent?.category).isEqualTo(UploadQualityCategories.COUNT)
            assertThat(secondEvent?.specificType).isNull()
        }
    }

    @Test
    fun `M not send network failure upload quality event W run { response 202 }`(forge: Forge) {
        // Given
        val mockRumFeature: FeatureScope = mock()
        whenever(mockFeatureSdkCore.getFeature(Feature.RUM_FEATURE_NAME))
            .thenReturn(mockRumFeature)
        testedRunnable = DataUploadRunnable(
            featureSdkCore = mockFeatureSdkCore,
            featureName = fakeFeatureName,
            threadPoolExecutor = mockThreadPoolExecutor,
            storage = mockStorage,
            dataUploader = mockDataUploader,
            contextProvider = mockContextProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            internalLogger = mockInternalLogger
        )
        val batches = forge.aList(size = 1) { aList { getForgery<RawBatchEvent>() } }
        val batchIds: List<BatchId> = batches.map { mock() }
        val batchMetadata = forge.aList(size = batches.size) { aNullable { aString().toByteArray() } }
        stubStorage(batchIds, batches, batchMetadata)
        val mockUploadStatus: UploadStatus = mock()
        whenever(mockUploadStatus.code).thenReturn(202)

        batches.forEachIndexed { index, batch ->
            whenever(
                mockDataUploader.upload(
                    fakeContext,
                    batch,
                    batchMetadata[index],
                    batchIds[index]
                )
            ) doReturn mockUploadStatus
        }

        // When
        testedRunnable.run()

        // Then
        argumentCaptor<UploadQualityEvent> {
            verify(mockRumFeature, times(1)).sendEvent(
                event = capture()
            )
            val firstEvent = firstValue as? UploadQualityEvent
            assertThat(firstEvent?.track).isEqualTo(fakeFeatureName)
            assertThat(firstEvent?.category).isEqualTo(UploadQualityCategories.COUNT)
            assertThat(firstEvent?.specificType).isNull()
        }
    }

    @Test
    fun `M send offline blocker upload quality event W run { is offline }`(forge: Forge) {
        // Given
        val mockRumFeature: FeatureScope = mock()
        whenever(mockFeatureSdkCore.getFeature(Feature.RUM_FEATURE_NAME))
            .thenReturn(mockRumFeature)
        val mockNetworkInfo: NetworkInfo = mock()
        whenever(mockNetworkInfo.connectivity).thenReturn(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()).thenReturn(mockNetworkInfo)
        testedRunnable = DataUploadRunnable(
            featureSdkCore = mockFeatureSdkCore,
            featureName = fakeFeatureName,
            threadPoolExecutor = mockThreadPoolExecutor,
            storage = mockStorage,
            dataUploader = mockDataUploader,
            contextProvider = mockContextProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            internalLogger = mockInternalLogger
        )
        val batches = forge.aList(size = 1) { aList { getForgery<RawBatchEvent>() } }
        val batchIds: List<BatchId> = batches.map { mock() }
        val batchMetadata = forge.aList(size = batches.size) { aNullable { aString().toByteArray() } }
        stubStorage(batchIds, batches, batchMetadata)
        val mockUploadStatus: UploadStatus = mock()
        whenever(mockUploadStatus.code).thenReturn(202)

        batches.forEachIndexed { index, batch ->
            whenever(
                mockDataUploader.upload(
                    fakeContext,
                    batch,
                    batchMetadata[index],
                    batchIds[index]
                )
            ) doReturn mockUploadStatus
        }

        // When
        testedRunnable.run()

        // Then
        val captor = argumentCaptor<UploadQualityEvent> {
            verify(mockRumFeature, times(2)).sendEvent(
                event = capture()
            )
        }

        val allValues = captor.allValues
        assertThat(allValues).hasSize(2)
        val expectedCount = UploadQualityEvent(
            track = fakeFeatureName,
            category = UploadQualityCategories.COUNT,
            specificType = null
        )
        val expectedBlocker = UploadQualityEvent(
            track = fakeFeatureName,
            category = UploadQualityCategories.BLOCKER,
            specificType = UploadQualityBlockers.OFFLINE.key
        )
        val fakeExpected = listOf(expectedCount, expectedBlocker)
        assertThat(allValues.containsAll(fakeExpected)).isTrue()
    }

    @Test
    fun `M send low power blocker upload quality event W run { is low power mode }`(forge: Forge) {
        // Given
        val mockRumFeature: FeatureScope = mock()
        whenever(mockFeatureSdkCore.getFeature(Feature.RUM_FEATURE_NAME))
            .thenReturn(mockRumFeature)
        val mockSystemInfo: SystemInfo = mock()
        whenever(mockSystemInfo.batteryLevel).thenReturn(1)
        whenever(mockSystemInfoProvider.getLatestSystemInfo()).thenReturn(mockSystemInfo)
        testedRunnable = DataUploadRunnable(
            featureSdkCore = mockFeatureSdkCore,
            featureName = fakeFeatureName,
            threadPoolExecutor = mockThreadPoolExecutor,
            storage = mockStorage,
            dataUploader = mockDataUploader,
            contextProvider = mockContextProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            internalLogger = mockInternalLogger
        )
        val batches = forge.aList(size = 1) { aList { getForgery<RawBatchEvent>() } }
        val batchIds: List<BatchId> = batches.map { mock() }
        val batchMetadata = forge.aList(size = batches.size) { aNullable { aString().toByteArray() } }
        stubStorage(batchIds, batches, batchMetadata)
        val mockUploadStatus: UploadStatus = mock()
        whenever(mockUploadStatus.code).thenReturn(202)

        batches.forEachIndexed { index, batch ->
            whenever(
                mockDataUploader.upload(
                    fakeContext,
                    batch,
                    batchMetadata[index],
                    batchIds[index]
                )
            ) doReturn mockUploadStatus
        }

        // When
        testedRunnable.run()

        // Then
        val captor = argumentCaptor<UploadQualityEvent> {
            verify(mockRumFeature, times(2)).sendEvent(
                event = capture()
            )
        }
        val allValues = captor.allValues
        assertThat(allValues).hasSize(2)
        val expectedCount = UploadQualityEvent(
            track = fakeFeatureName,
            category = UploadQualityCategories.COUNT,
            specificType = null
        )
        val expectedBlocker = UploadQualityEvent(
            track = fakeFeatureName,
            category = UploadQualityCategories.BLOCKER,
            specificType = UploadQualityBlockers.LOW_BATTERY.key
        )
        val fakeExpected = listOf(expectedCount, expectedBlocker)
        assertThat(allValues.containsAll(fakeExpected)).isTrue()
    }

    @Test
    fun `M send correct blockers upload quality event W run { low battery and power save mode }`(forge: Forge) {
        // Given
        val mockRumFeature: FeatureScope = mock()
        whenever(mockFeatureSdkCore.getFeature(Feature.RUM_FEATURE_NAME))
            .thenReturn(mockRumFeature)
        val mockSystemInfo: SystemInfo = mock()
        whenever(mockSystemInfo.batteryLevel).thenReturn(1)
        whenever(mockSystemInfo.powerSaveMode).thenReturn(true)
        whenever(mockSystemInfoProvider.getLatestSystemInfo()).thenReturn(mockSystemInfo)
        testedRunnable = DataUploadRunnable(
            featureSdkCore = mockFeatureSdkCore,
            featureName = fakeFeatureName,
            threadPoolExecutor = mockThreadPoolExecutor,
            storage = mockStorage,
            dataUploader = mockDataUploader,
            contextProvider = mockContextProvider,
            networkInfoProvider = mockNetworkInfoProvider,
            systemInfoProvider = mockSystemInfoProvider,
            uploadSchedulerStrategy = mockUploadSchedulerStrategy,
            maxBatchesPerJob = fakeMaxBatchesPerJob,
            internalLogger = mockInternalLogger
        )
        val batches = forge.aList(size = 1) { aList { getForgery<RawBatchEvent>() } }
        val batchIds: List<BatchId> = batches.map { mock() }
        val batchMetadata = forge.aList(size = batches.size) { aNullable { aString().toByteArray() } }
        stubStorage(batchIds, batches, batchMetadata)
        val mockUploadStatus: UploadStatus = mock()
        whenever(mockUploadStatus.code).thenReturn(202)

        batches.forEachIndexed { index, batch ->
            whenever(
                mockDataUploader.upload(
                    fakeContext,
                    batch,
                    batchMetadata[index],
                    batchIds[index]
                )
            ) doReturn mockUploadStatus
        }

        // When
        testedRunnable.run()

        // Then
        val captor = argumentCaptor<UploadQualityEvent> {
            verify(mockRumFeature, times(3)).sendEvent(
                event = capture()
            )
        }

        val allValues = captor.allValues
        assertThat(allValues).hasSize(3)

        val expectedCount = UploadQualityEvent(
            track = fakeFeatureName,
            category = UploadQualityCategories.COUNT,
            specificType = null
        )
        val expectedBlocker = UploadQualityEvent(
            track = fakeFeatureName,
            category = UploadQualityCategories.BLOCKER,
            specificType = UploadQualityBlockers.LOW_BATTERY.key
        )
        val expectedBlocker2 = UploadQualityEvent(
            track = fakeFeatureName,
            category = UploadQualityCategories.BLOCKER,
            specificType = UploadQualityBlockers.LOW_POWER_MODE.key
        )
        val fakeExpected = listOf(expectedCount, expectedBlocker, expectedBlocker2)
        assertThat(allValues.containsAll(fakeExpected)).isTrue()
    }

// endregion

    companion object {

        @JvmStatic
        fun retryBatchStatusValues(): List<UploadStatus> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            return listOf(
                forge.getForgery(UploadStatus.HttpClientRateLimiting::class.java),
                forge.getForgery(UploadStatus.HttpServerError::class.java),
                forge.getForgery(UploadStatus.NetworkError::class.java),
                forge.getForgery(UploadStatus.UnknownException::class.java)
            )
        }

        @JvmStatic
        fun dropBatchStatusValues(): List<UploadStatus> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            return listOf(
                forge.getForgery(UploadStatus.HttpClientError::class.java),
                forge.getForgery(UploadStatus.HttpRedirection::class.java),
                forge.getForgery(UploadStatus.UnknownHttpError::class.java),
                forge.getForgery(UploadStatus.UnknownStatus::class.java)
            )
        }
    }
}
