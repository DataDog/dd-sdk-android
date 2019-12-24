/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.domain.Batch
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.net.NetworkInfo
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.android.log.internal.system.SystemInfo
import com.datadog.android.log.internal.system.SystemInfoProvider
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.extensions.SystemOutputExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
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
import java.io.ByteArrayOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogUploadRunnableTest {

    @Mock
    lateinit var mockHandler: Handler
    @Mock
    lateinit var mockReader: Reader
    @Mock
    lateinit var mockLogUploader: LogUploader
    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider
    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider

    lateinit var testedRunnable: LogUploadRunnable

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
            batteryStatus = forge.aValueFrom(SystemInfo.BatteryStatus::class.java),
            batteryLevel = forge.anInt(20, 100)
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        testedRunnable = LogUploadRunnable(
            mockHandler,
            mockReader,
            mockLogUploader,
            mockNetworkInfoProvider,
            mockSystemInfoProvider
        )
    }

    @Test
    fun `doesn't send batch when offline`(@Forgery batch: Batch) {
        val networkInfo = NetworkInfo(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn networkInfo

        testedRunnable.run()

        verify(mockReader, never()).dropBatch(batch.id)
        verify(mockLogUploader, never()).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `doesn't send batch when battery is low and unplugged`(
        @Forgery batch: Batch,
        forge: Forge
    ) {
        val systemInfo = SystemInfo(
            forge.anElementFrom(
                SystemInfo.BatteryStatus.DISCHARGING,
                SystemInfo.BatteryStatus.NOT_CHARGING
            ),
            forge.anInt(1, 10)
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn systemInfo
        whenever(mockReader.readNextBatch()) doReturn batch

        testedRunnable.run()

        verify(mockReader, never()).dropBatch(anyOrNull())
        verify(mockLogUploader, never()).uploadLogs(anyOrNull())
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `doesn't send batch when power save mode is enabled`(
        @Forgery batch: Batch,
        forge: Forge
    ) {
        val systemInfo = SystemInfo(
            batteryStatus = forge.anElementFrom(
                SystemInfo.BatteryStatus.DISCHARGING,
                SystemInfo.BatteryStatus.NOT_CHARGING
            ),
            batteryLevel = forge.anInt(50, 100),
            powerSaveMode = true
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn systemInfo
        whenever(mockReader.readNextBatch()) doReturn batch

        testedRunnable.run()

        verify(mockReader, never()).dropBatch(anyOrNull())
        verify(mockLogUploader, never()).uploadLogs(anyOrNull())
        verify(mockHandler).postDelayed(same(testedRunnable), any())
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
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.SUCCESS

        testedRunnable.run()

        verify(mockReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `no batch to send`(@SystemOutStream systemOutStream: ByteArrayOutputStream) {
        whenever(mockReader.readNextBatch()) doReturn null

        testedRunnable.run()

        verify(mockReader, never()).dropBatch(anyOrNull())
        verifyZeroInteractions(mockLogUploader)
        verify(mockHandler).postDelayed(testedRunnable, LogUploadRunnable.MAX_DELAY)
        if (BuildConfig.DEBUG) {
            assertThat(systemOutStream.toString().trim())
                .withFailMessage("We were expecting an info log message here")
                .matches("I/DD_LOG: LogUploadRunnable: .+")
        }
    }

    @Test
    fun `batch sent successfully`(@Forgery batch: Batch) {
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.SUCCESS

        testedRunnable.run()

        verify(mockReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch kept on Network Error`(@Forgery batch: Batch) {
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.NETWORK_ERROR

        testedRunnable.run()

        verify(mockReader, never()).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch kept after n Network Error`(
        @Forgery batch: Batch,
        @IntForgery(min = 3, max = 42) runCount: Int
    ) {
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.NETWORK_ERROR

        for (i in 0 until runCount) {
            testedRunnable.run()
        }
        verify(mockLogUploader, times(runCount)).uploadLogs(batch.logs)
        verify(mockReader, never()).dropBatch(batch.id)
        verify(mockHandler, times(runCount)).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Redirection`(@Forgery batch: Batch) {
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.HTTP_REDIRECTION

        testedRunnable.run()

        verify(mockReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Client Error`(@Forgery batch: Batch) {
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.HTTP_CLIENT_ERROR

        testedRunnable.run()

        verify(mockReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch kept on Server Error`(@Forgery batch: Batch) {
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.HTTP_SERVER_ERROR

        testedRunnable.run()

        verify(mockReader, never()).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch kept after n Server Error`(
        @Forgery batch: Batch,
        @IntForgery(min = 3, max = 42) runCount: Int
    ) {
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.HTTP_SERVER_ERROR

        for (i in 0 until runCount) {
            testedRunnable.run()
        }

        verify(mockLogUploader, times(runCount)).uploadLogs(batch.logs)
        verify(mockReader, never()).dropBatch(batch.id)
        verify(mockHandler, times(runCount)).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Unknown error`(@Forgery batch: Batch) {
        whenever(mockReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.UNKNOWN_ERROR

        testedRunnable.run()

        verify(mockReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `when has batches the upload frequency will increase`(@Forgery batch: Batch) {
        whenever(mockReader.readNextBatch()).doReturn(batch)

        repeat(5) {
            testedRunnable.run()
        }

        val captor = argumentCaptor<Long>()
        verify(mockHandler, times(5))
            .postDelayed(same(testedRunnable), captor.capture())
        captor.allValues.reduce { previous, next ->
            assertThat(next).isLessThan(previous)
            next
        }
    }

    @Test
    fun `when has batches will increase the frequency up to a specific max`(@Forgery batch: Batch) {
        whenever(mockReader.readNextBatch()).doReturn(batch)

        repeat(30) {
            testedRunnable.run()
        }

        val captor = argumentCaptor<Long>()
        verify(mockHandler, times(30))
            .postDelayed(same(testedRunnable), captor.capture())
        captor.allValues.reduce { previous, next ->
            assertThat(next).isGreaterThanOrEqualTo(LogUploadRunnable.MIN_DELAY_MS)
            assertThat(next).isLessThan(LogUploadRunnable.DEFAULT_DELAY)
            next
        }
    }

    @Test
    fun `when no more batches available the scheduler delay will be increased`(
        @Forgery batch: Batch
    ) {
        whenever(mockReader.readNextBatch())
            .doReturn(batch)
            .doReturn(null)

        repeat(2) {
            testedRunnable.run()
        }

        val captor = argumentCaptor<Long>()
        verify(mockHandler, times(2))
            .postDelayed(same(testedRunnable), captor.capture())
        verify(mockHandler).removeCallbacks(same(testedRunnable))
        assertThat(captor.lastValue).isEqualTo(LogUploadRunnable.MAX_DELAY)
    }
}
