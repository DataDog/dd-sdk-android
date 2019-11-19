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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    fun `no batch to send`() {
        whenever(mockLogReader.readNextBatch()) doReturn null

        testedRunnable.run()

        verify(mockLogReader, never()).dropBatch(anyOrNull())
        verifyZeroInteractions(mockLogUploader)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch sent successfully`(forge: Forge) {
        val fakeId = forge.anHexadecimalString()
        val fakeLogs = forge.aList { anAlphabeticalString() }
        whenever(mockLogReader.readNextBatch()) doReturn (fakeId to fakeLogs)
        whenever(mockLogUploader.uploadLogs(fakeLogs)) doReturn LogUploadStatus.SUCCESS

        testedRunnable.run()

        verify(mockLogReader).dropBatch(fakeId)
        verify(mockLogUploader).uploadLogs(fakeLogs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch kept on Network Error`(forge: Forge) {
        val fakeId = forge.anHexadecimalString()
        val fakeLogs = forge.aList { anAlphabeticalString() }
        whenever(mockLogReader.readNextBatch()) doReturn (fakeId to fakeLogs)
        whenever(mockLogUploader.uploadLogs(fakeLogs)) doReturn LogUploadStatus.NETWORK_ERROR

        testedRunnable.run()

        verify(mockLogReader, never()).dropBatch(fakeId)
        verify(mockLogUploader).uploadLogs(fakeLogs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on third Network Error`(forge: Forge) {
        val fakeId = forge.anHexadecimalString()
        val fakeLogs = forge.aList { anAlphabeticalString() }
        whenever(mockLogReader.readNextBatch()) doReturn (fakeId to fakeLogs)
        whenever(mockLogUploader.uploadLogs(fakeLogs)) doReturn LogUploadStatus.NETWORK_ERROR

        testedRunnable.run()
        testedRunnable.run()
        testedRunnable.run()

        verify(mockLogUploader, times(3)).uploadLogs(fakeLogs)
        verify(mockLogReader).dropBatch(fakeId)
        verify(mockHandler, times(3)).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Redirection`(forge: Forge) {
        val fakeId = forge.anHexadecimalString()
        val fakeLogs = forge.aList { anAlphabeticalString() }
        whenever(mockLogReader.readNextBatch()) doReturn (fakeId to fakeLogs)
        whenever(mockLogUploader.uploadLogs(fakeLogs)) doReturn LogUploadStatus.HTTP_REDIRECTION

        testedRunnable.run()

        verify(mockLogReader).dropBatch(fakeId)
        verify(mockLogUploader).uploadLogs(fakeLogs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Client Error`(forge: Forge) {
        val fakeId = forge.anHexadecimalString()
        val fakeLogs = forge.aList { anAlphabeticalString() }
        whenever(mockLogReader.readNextBatch()) doReturn (fakeId to fakeLogs)
        whenever(mockLogUploader.uploadLogs(fakeLogs)) doReturn LogUploadStatus.HTTP_CLIENT_ERROR

        testedRunnable.run()

        verify(mockLogReader).dropBatch(fakeId)
        verify(mockLogUploader).uploadLogs(fakeLogs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch kept on Server Error`(forge: Forge) {
        val fakeId = forge.anHexadecimalString()
        val fakeLogs = forge.aList { anAlphabeticalString() }
        whenever(mockLogReader.readNextBatch()) doReturn (fakeId to fakeLogs)
        whenever(mockLogUploader.uploadLogs(fakeLogs)) doReturn LogUploadStatus.HTTP_SERVER_ERROR

        testedRunnable.run()

        verify(mockLogReader, never()).dropBatch(fakeId)
        verify(mockLogUploader).uploadLogs(fakeLogs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on third Server Error`(forge: Forge) {
        val fakeId = forge.anHexadecimalString()
        val fakeLogs = forge.aList { anAlphabeticalString() }
        whenever(mockLogReader.readNextBatch()) doReturn (fakeId to fakeLogs)
        whenever(mockLogUploader.uploadLogs(fakeLogs)) doReturn LogUploadStatus.HTTP_SERVER_ERROR

        testedRunnable.run()
        testedRunnable.run()
        testedRunnable.run()

        verify(mockLogUploader, times(3)).uploadLogs(fakeLogs)
        verify(mockLogReader).dropBatch(fakeId)
        verify(mockHandler, times(3)).postDelayed(same(testedRunnable), any())
    }

    @Test
    fun `batch dropped on Unknown error`(forge: Forge) {
        val fakeId = forge.anHexadecimalString()
        val fakeLogs = forge.aList { anAlphabeticalString() }
        whenever(mockLogReader.readNextBatch()) doReturn (fakeId to fakeLogs)
        whenever(mockLogUploader.uploadLogs(fakeLogs)) doReturn LogUploadStatus.UNKNOWN_ERROR

        testedRunnable.run()

        verify(mockLogReader).dropBatch(fakeId)
        verify(mockLogUploader).uploadLogs(fakeLogs)
        verify(mockHandler).postDelayed(same(testedRunnable), any())
    }
}
