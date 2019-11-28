/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.net.LogUploadStatus
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.utils.extension.SystemOutStream
import com.datadog.android.utils.extension.SystemOutputExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
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

    lateinit var testedRunnable: Runnable

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
        verify(mockHandler).postDelayed(same(testedRunnable), any())
        assertThat(systemOutStream.toString().trim())
            .withFailMessage("We were expecting an info log message here")
            .matches("I/android: LogUploadRunnable: .+")
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
}
