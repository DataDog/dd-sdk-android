/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.UploadStatus
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.Batch
import com.datadog.android.core.internal.persistence.DataReader
import com.datadog.android.core.internal.system.SystemInfo
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
    lateinit var mockReader: DataReader

    @Mock
    lateinit var mockDataUploader: DataUploader

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider

    @Forgery
    lateinit var fakeUploadFrequency: UploadFrequency

    lateinit var testedRunnable: DataUploadRunnable

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
            batteryStatus = forge.aValueFrom(SystemInfo.BatteryStatus::class.java),
            batteryLevel = forge.anInt(20, 100)
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        testedRunnable = DataUploadRunnable(
            mockThreadPoolExecutor,
            mockReader,
            mockDataUploader,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            fakeUploadFrequency
        )
    }

    @Test
    fun `doesn't send batch when offline`(@Forgery batch: Batch) {
        val networkInfo =
            NetworkInfo(
                NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
            )
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn networkInfo

        testedRunnable.run()

        verify(mockReader, never()).drop(batch)
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader, never()).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @EnumSource(
        SystemInfo.BatteryStatus::class,
        names = ["DISCHARGING", "NOT_CHARGING"],
        mode = EnumSource.Mode.INCLUDE
    )
    fun `doesn't send batch when battery is low and unplugged`(
        batteryStatus: SystemInfo.BatteryStatus,
        @Forgery batch: Batch,
        forge: Forge
    ) {
        val systemInfo = SystemInfo(
            batteryStatus,
            forge.anInt(1, 10)
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn systemInfo
        whenever(mockReader.lockAndReadNext()) doReturn batch

        testedRunnable.run()

        verify(mockReader, never()).drop(anyOrNull())
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader, never()).upload(anyOrNull())
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @EnumSource(
        SystemInfo.BatteryStatus::class,
        names = ["DISCHARGING", "NOT_CHARGING"],
        mode = EnumSource.Mode.INCLUDE
    )
    fun `doesn't send batch when power save mode is enabled`(
        batteryStatus: SystemInfo.BatteryStatus,
        @Forgery batch: Batch,
        forge: Forge
    ) {
        val systemInfo = SystemInfo(
            batteryStatus = batteryStatus,
            batteryLevel = forge.anInt(50, 100),
            powerSaveMode = true
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn systemInfo
        whenever(mockReader.lockAndReadNext()) doReturn batch

        testedRunnable.run()

        verify(mockReader, never()).drop(anyOrNull())
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader, never()).upload(anyOrNull())
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch sent when battery is low and charging`(
        @Forgery batch: Batch,
        forge: Forge
    ) {
        val systemInfo = SystemInfo(
            SystemInfo.BatteryStatus.CHARGING,
            forge.anInt(1, 10)
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn systemInfo
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.SUCCESS

        testedRunnable.run()

        verify(mockReader).drop(batch)
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 no batch to send`() {
        whenever(mockReader.lockAndReadNext()) doReturn null

        testedRunnable.run()

        verify(mockReader, never()).drop(anyOrNull())
        verify(mockReader, never()).release(anyOrNull())
        verifyZeroInteractions(mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            eq(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch sent successfully`(@Forgery batch: Batch) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.SUCCESS

        testedRunnable.run()

        verify(mockReader).drop(batch)
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch kept on Network Error`(@Forgery batch: Batch) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.NETWORK_ERROR

        testedRunnable.run()

        verify(mockReader, never()).drop(batch)
        verify(mockReader).release(batch)
        verify(mockDataUploader).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch kept after n Network Error`(
        @Forgery batch: Batch,
        @IntForgery(min = 3, max = 42) runCount: Int
    ) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.NETWORK_ERROR

        for (i in 0 until runCount) {
            testedRunnable.run()
        }
        verify(mockDataUploader, times(runCount)).upload(batch.data)
        verify(mockReader, never()).drop(batch)
        verify(mockReader, times(runCount)).release(batch)
        verify(mockThreadPoolExecutor, times(runCount)).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch dropped on Redirection`(@Forgery batch: Batch) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.HTTP_REDIRECTION

        testedRunnable.run()

        verify(mockReader).drop(batch)
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch dropped on Client Error`(@Forgery batch: Batch) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.HTTP_CLIENT_ERROR

        testedRunnable.run()

        verify(mockReader).drop(batch)
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch dropped on Invalid Token Error`(@Forgery batch: Batch) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.INVALID_TOKEN_ERROR

        testedRunnable.run()

        verify(mockReader).drop(batch)
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch kept on Server Error`(@Forgery batch: Batch) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.HTTP_SERVER_ERROR

        testedRunnable.run()

        verify(mockReader, never()).drop(batch)
        verify(mockReader).release(batch)
        verify(mockDataUploader).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch kept after n Server Error`(
        @Forgery batch: Batch,
        @IntForgery(min = 3, max = 42) runCount: Int
    ) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.HTTP_SERVER_ERROR

        for (i in 0 until runCount) {
            testedRunnable.run()
        }

        verify(mockDataUploader, times(runCount)).upload(batch.data)
        verify(mockReader, never()).drop(batch)
        verify(mockReader, times(runCount)).release(batch)
        verify(mockThreadPoolExecutor, times(runCount)).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch dropped on Unknown error`(@Forgery batch: Batch) {
        whenever(mockReader.lockAndReadNext()) doReturn batch
        whenever(mockDataUploader.upload(batch.data)) doReturn UploadStatus.UNKNOWN_ERROR

        testedRunnable.run()

        verify(mockReader).drop(batch)
        verify(mockReader, never()).release(batch)
        verify(mockDataUploader).upload(batch.data)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `when has batches the upload frequency will increase`(
        @Forgery batch: Batch
    ) {
        whenever(mockDataUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockReader.lockAndReadNext()).doReturn(batch)

        repeat(5) {
            testedRunnable.run()
        }

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
        @Forgery batch: Batch,
        @IntForgery(16, 64) runCount: Int
    ) {
        // Given
        whenever(mockDataUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockReader.lockAndReadNext()).doReturn(batch)

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
        names = ["HTTP_REDIRECTION", "HTTP_CLIENT_ERROR", "UNKNOWN_ERROR"],
        mode = EnumSource.Mode.INCLUDE
    )
    fun `𝕄 reduce delay between runs 𝕎 batch fails and should be dropped`(
        uploadStatus: UploadStatus,
        @Forgery batch: Batch,
        @IntForgery(16, 64) runCount: Int
    ) {
        // Given
        whenever(mockDataUploader.upload(any())) doAnswer {
            uploadStatus
        }
        whenever(mockReader.lockAndReadNext()).doReturn(batch)

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
        // Given
        whenever(mockDataUploader.upload(any())) doReturn UploadStatus.SUCCESS
        whenever(mockReader.lockAndReadNext()) doReturn null

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

    @Test
    fun `𝕄 increase delay between runs 𝕎 batch fails and should be retried`(
        @IntForgery(16, 64) runCount: Int,
        forge: Forge
    ) {
        // Given
        whenever(mockDataUploader.upload(any())) doAnswer {
            forge.aValueFrom(
                UploadStatus::class.java,
                exclude = listOf(
                    UploadStatus.SUCCESS,
                    UploadStatus.HTTP_REDIRECTION,
                    UploadStatus.HTTP_CLIENT_ERROR,
                    UploadStatus.UNKNOWN_ERROR
                )
            )
        }
        whenever(mockReader.lockAndReadNext()) doReturn null

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
