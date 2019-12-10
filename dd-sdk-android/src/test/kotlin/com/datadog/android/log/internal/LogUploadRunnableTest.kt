/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import com.datadog.android.BuildConfig
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.utils.extension.SystemOutStream
import com.datadog.android.utils.extension.SystemOutputExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
internal class LogUploadRunnableTest {

    @Mock
    lateinit var mockHandler: Handler
    @Mock
    lateinit var mockLogReader: LogReader
    @Mock
    lateinit var mockLogUploader: LogUploader

    lateinit var testedRunnable: LogUploadRunnable

    @BeforeEach
    fun `set up`() {
        testedRunnable = LogUploadRunnable(mockHandler, mockLogReader, mockLogUploader)
    }

    @Test
    fun `no batch to send`(@SystemOutStream systemOutStream: ByteArrayOutputStream) {
        whenever(mockLogReader.readNextBatch()) doReturn null

        testedRunnable.run()

        verify(mockLogReader, never()).dropBatch(anyOrNull())
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
        whenever(mockLogReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.SUCCESS

        testedRunnable.run()

        verify(mockLogReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch kept on Network Error`(@Forgery batch: Batch) {
        whenever(mockLogReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.NETWORK_ERROR

        testedRunnable.run()

        verify(mockLogReader, never()).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on third Network Error`(@Forgery batch: Batch) {
        whenever(mockLogReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.NETWORK_ERROR

        testedRunnable.run()
        testedRunnable.run()
        testedRunnable.run()

        verify(mockLogUploader, times(3)).uploadLogs(batch.logs)
        verify(mockLogReader).dropBatch(batch.id)
        verify(mockHandler, times(3)).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Redirection`(@Forgery batch: Batch) {
        whenever(mockLogReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.HTTP_REDIRECTION

        testedRunnable.run()

        verify(mockLogReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Client Error`(@Forgery batch: Batch) {
        whenever(mockLogReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.HTTP_CLIENT_ERROR

        testedRunnable.run()

        verify(mockLogReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch kept on Server Error`(@Forgery batch: Batch) {
        whenever(mockLogReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.HTTP_SERVER_ERROR

        testedRunnable.run()

        verify(mockLogReader, never()).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on third Server Error`(@Forgery batch: Batch) {
        whenever(mockLogReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.HTTP_SERVER_ERROR

        testedRunnable.run()
        testedRunnable.run()
        testedRunnable.run()

        verify(mockLogUploader, times(3)).uploadLogs(batch.logs)
        verify(mockLogReader).dropBatch(batch.id)
        verify(mockHandler, times(3)).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Unknown error`(@Forgery batch: Batch) {
        whenever(mockLogReader.readNextBatch()) doReturn batch
        whenever(mockLogUploader.uploadLogs(batch.logs)) doReturn LogUploadStatus.UNKNOWN_ERROR

        testedRunnable.run()

        verify(mockLogReader).dropBatch(batch.id)
        verify(mockLogUploader).uploadLogs(batch.logs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `when has batches the upload frequency will increase`(@Forgery batch: Batch) {
        whenever(mockLogReader.readNextBatch()).doReturn(batch)

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
        whenever(mockLogReader.readNextBatch()).doReturn(batch)

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
        whenever(mockLogReader.readNextBatch())
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

    @Test
    fun `it will be re - scheduled with a default delay when new data available`() {
        whenever(mockLogReader.readNextBatch())
            .doReturn(null)
        val countDownLatch = CountDownLatch(2)
        Thread {
            testedRunnable.run()
            countDownLatch.countDown()
            Thread {
                testedRunnable.onDataAdded()
                countDownLatch.countDown()
            }.start()
        }.start()
        countDownLatch.await()

        val captor = argumentCaptor<Long>()
        val inOrder = inOrder(mockHandler)
        inOrder.verify(mockHandler).removeCallbacks(same(testedRunnable))
        inOrder.verify(mockHandler)
            .postDelayed(same(testedRunnable), captor.capture())
        inOrder.verify(mockHandler).removeCallbacks(same(testedRunnable))
        inOrder.verify(mockHandler)
            .postDelayed(same(testedRunnable), captor.capture())
        assertThat(captor.firstValue).isEqualTo(LogUploadRunnable.MAX_DELAY)
        assertThat(captor.lastValue).isEqualTo(LogUploadRunnable.DEFAULT_DELAY)
    }

    @Test
    fun `if resumed from multiple threads it will only resume once`() {
        whenever(mockLogReader.readNextBatch())
            .doReturn(null)
        val countDownLatch = CountDownLatch(6)
        Thread {
            testedRunnable.run()
            countDownLatch.countDown()
            repeat(5) {
                Thread {
                    testedRunnable.onDataAdded()
                    countDownLatch.countDown()
                }.start()
            }
        }.start()
        countDownLatch.await()

        val captor = argumentCaptor<Long>()
        val inOrder = inOrder(mockHandler)
        inOrder.verify(mockHandler).removeCallbacks(same(testedRunnable))
        inOrder.verify(mockHandler)
            .postDelayed(same(testedRunnable), captor.capture())
        inOrder.verify(mockHandler).removeCallbacks(same(testedRunnable))
        inOrder.verify(mockHandler)
            .postDelayed(same(testedRunnable), captor.capture())
        assertThat(captor.firstValue).isEqualTo(LogUploadRunnable.MAX_DELAY)
        assertThat(captor.lastValue).isEqualTo(LogUploadRunnable.DEFAULT_DELAY)
    }

    @Test
    fun `if notified while already resuming it will only schedule and run once`(
        @Forgery batch: Batch
    ) {
        whenever(mockLogReader.readNextBatch())
            .doReturn(null)
            .doReturn(batch)
        val countDownLatch = CountDownLatch(2)
        // put it in max delay
        testedRunnable.run()

        Thread {
            testedRunnable.run()
            countDownLatch.countDown()
        }.start()
        Thread {
            Thread.sleep(50)
            testedRunnable.onDataAdded()
            countDownLatch.countDown()
        }.start()
        countDownLatch.await()

        val captor = argumentCaptor<Long>()
        val inOrder = inOrder(mockHandler)
        inOrder.verify(mockHandler).removeCallbacks(same(testedRunnable))
        inOrder.verify(mockHandler, times(2))
            .postDelayed(same(testedRunnable), captor.capture())
        inOrder.verifyNoMoreInteractions()
        assertThat(captor.firstValue).isEqualTo(LogUploadRunnable.MAX_DELAY)
        assertThat(captor.lastValue).isLessThan(LogUploadRunnable.DEFAULT_DELAY)
    }

    @Test
    fun `if not paused resume will do nothing`() {
        val countDownLatch = CountDownLatch(5)
        repeat(5) {
            Thread {
                testedRunnable.onDataAdded()
                countDownLatch.countDown()
            }.start()
        }
        countDownLatch.await()

        verifyZeroInteractions(mockHandler)
    }
}
