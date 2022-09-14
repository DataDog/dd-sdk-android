/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.data.upload

import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfo
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.BatchConfirmation
import com.datadog.android.v2.core.internal.storage.BatchId
import com.datadog.android.v2.core.internal.storage.BatchReader
import com.datadog.android.v2.core.internal.storage.Storage
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

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

    @Forgery
    lateinit var fakeContext: DatadogContext

    @Forgery
    lateinit var fakeUploadFrequency: UploadFrequency

    private lateinit var testedRunnable: DataUploadRunnable

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeNetworkInfo =
            NetworkInfo(
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

        whenever(mockContextProvider.context) doReturn fakeContext

        testedRunnable = DataUploadRunnable(
            mockThreadPoolExecutor,
            mockStorage,
            mockDataUploader,
            mockContextProvider,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            fakeUploadFrequency
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
        verifyZeroInteractions(mockDataUploader, mockStorage)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M send batch W run() { batteryFullOrCharging }`(
        @StringForgery batch: List<String>,
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
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M send batch W run() { battery level high }`(
        @StringForgery batch: List<String>,
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
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M send batch W run() { onExternalPower }`(
        @StringForgery batch: List<String>,
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
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
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
        verifyZeroInteractions(mockStorage, mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not send batch W run() { batteryFullOrCharging, powerSaveMode}`(
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
        verifyZeroInteractions(mockStorage, mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not send batch W run() { batteryLeveHigh, powerSaveMode}`(
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
        verifyZeroInteractions(mockStorage, mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not send batch W run() { onExternalPower, powerSaveMode}`(
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
        verifyZeroInteractions(mockStorage, mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 no batch to send`() {
        // Given
        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            it.getArgument<() -> Unit>(0)()
        }

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage).readNextBatch(any(), any())
        verifyNoMoreInteractions(mockStorage)
        verifyZeroInteractions(mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            eq(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch sent successfully`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @EnumSource(
        UploadStatus::class,
        names = ["NETWORK_ERROR", "HTTP_SERVER_ERROR", "HTTP_CLIENT_RATE_LIMITING"],
        mode = EnumSource.Mode.INCLUDE
    )
    fun `batch kept on error`(
        uploadStatus: UploadStatus,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn uploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(false)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @EnumSource(
        UploadStatus::class,
        names = ["NETWORK_ERROR", "HTTP_SERVER_ERROR", "HTTP_CLIENT_RATE_LIMITING"],
        mode = EnumSource.Mode.INCLUDE
    )
    fun `batch kept after n errors`(
        uploadStatus: UploadStatus,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(min = 3, max = 42) runCount: Int,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn uploadStatus

        // WHen
        for (i in 0 until runCount) {
            testedRunnable.run()
        }

        // Then
        verify(batchConfirmation, times(runCount)).markAsRead(false)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader, times(runCount)).read()
        verify(mockDataUploader, times(runCount)).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor, times(runCount)).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @EnumSource(
        UploadStatus::class,
        names = ["INVALID_TOKEN_ERROR", "HTTP_REDIRECTION", "HTTP_CLIENT_ERROR", "UNKNOWN_ERROR"],
        mode = EnumSource.Mode.INCLUDE
    )
    fun `batch dropped on error`(
        uploadStatus: UploadStatus,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn uploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `when has batches the upload frequency will increase`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        repeat(5) {
            testedRunnable.run()
        }

        // Then
        val captor = argumentCaptor<Long>()
        verify(mockThreadPoolExecutor, times(5))
            .schedule(same(testedRunnable), captor.capture(), eq(TimeUnit.MILLISECONDS))
        captor.allValues.reduce { previous, next ->
            assertThat(next).isLessThan(previous)
            next
        }
    }

    @Test
    fun `𝕄 reduce delay between runs 𝕎 upload is successful`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(16, 64) runCount: Int,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn UploadStatus.SUCCESS

        // When
        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        argumentCaptor<Long> {
            verify(mockThreadPoolExecutor, times(runCount))
                .schedule(
                    same(testedRunnable),
                    capture(),
                    eq(TimeUnit.MILLISECONDS)
                )

            allValues.reduce { previous, next ->
                assertThat(next)
                    .isLessThanOrEqualTo(previous)
                    .isBetween(testedRunnable.minDelayMs, testedRunnable.maxDelayMs)
                next
            }
        }
    }

    @ParameterizedTest
    @EnumSource(
        UploadStatus::class,
        names = ["HTTP_REDIRECTION", "HTTP_CLIENT_ERROR", "UNKNOWN_ERROR", "INVALID_TOKEN_ERROR"],
        mode = EnumSource.Mode.INCLUDE
    )
    fun `𝕄 reduce delay between runs 𝕎 batch fails and should be dropped`(
        uploadStatus: UploadStatus,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(16, 64) runCount: Int,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn uploadStatus

        // When
        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        argumentCaptor<Long> {
            verify(mockThreadPoolExecutor, times(runCount))
                .schedule(
                    same(testedRunnable),
                    capture(),
                    eq(TimeUnit.MILLISECONDS)
                )

            allValues.reduce { previous, next ->
                assertThat(next)
                    .isLessThanOrEqualTo(previous)
                    .isBetween(testedRunnable.minDelayMs, testedRunnable.maxDelayMs)
                next
            }
        }
    }

    @Test
    fun `𝕄 increase delay between runs 𝕎 no batch available`(
        @IntForgery(16, 64) runCount: Int
    ) {
        // When
        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            it.getArgument<() -> Unit>(0).invoke()
        }

        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        argumentCaptor<Long> {
            verify(mockThreadPoolExecutor, times(runCount))
                .schedule(
                    same(testedRunnable),
                    capture(),
                    eq(TimeUnit.MILLISECONDS)
                )

            allValues.reduce { previous, next ->
                assertThat(next)
                    .isGreaterThanOrEqualTo(previous)
                    .isBetween(testedRunnable.minDelayMs, testedRunnable.maxDelayMs)
                next
            }
        }
    }

    @Test
    fun `𝕄 increase delay between runs 𝕎 batch fails and should be retried`(
        @IntForgery(16, 64) runCount: Int,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(1).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doAnswer {
            forge.aValueFrom(
                UploadStatus::class.java,
                exclude = listOf(
                    UploadStatus.HTTP_REDIRECTION,
                    UploadStatus.HTTP_CLIENT_ERROR,
                    UploadStatus.UNKNOWN_ERROR,
                    UploadStatus.INVALID_TOKEN_ERROR,
                    UploadStatus.SUCCESS
                )
            )
        }

        // When
        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        argumentCaptor<Long> {
            verify(mockThreadPoolExecutor, times(runCount))
                .schedule(
                    same(testedRunnable),
                    capture(),
                    eq(TimeUnit.MILLISECONDS)
                )

            allValues.reduce { previous, next ->
                assertThat(next)
                    .isGreaterThanOrEqualTo(previous)
                    .isBetween(testedRunnable.minDelayMs, testedRunnable.maxDelayMs)
                next
            }
        }
    }
}
