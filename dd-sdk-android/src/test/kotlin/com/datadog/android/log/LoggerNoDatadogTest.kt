/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogWriter
import com.datadog.android.log.internal.file.DummyLogWriter
import com.datadog.android.utils.getFieldValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
internal class LoggerNoDatadogTest {

    lateinit var testedLogger: Logger

    lateinit var fakeServiceName: String
    lateinit var fakeMessage: String

    @Mock
    lateinit var mockContext: Context
    @Mock
    lateinit var mockLogStrategy: LogStrategy
    private lateinit var originalErrStream: PrintStream
    private lateinit var originalOutStream: PrintStream
    private lateinit var outStreamContent: ByteArrayOutputStream
    private lateinit var errStreamContent: ByteArrayOutputStream

    @BeforeEach
    fun `set up logger`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()

        testedLogger = Logger.Builder()
            .setServiceName(fakeServiceName)
            .setTimestampsEnabled(true)
            .setLogcatLogsEnabled(true)
            .setDatadogLogsEnabled(false) // <<<<
            .setNetworkInfoEnabled(true)
            .withLogStrategy(mockLogStrategy)
            .build()
    }

    @BeforeEach
    fun `capture output and error stream`() {
        originalOutStream = System.out
        originalErrStream = System.err

        outStreamContent = ByteArrayOutputStream()
        errStreamContent = ByteArrayOutputStream()

        System.setOut(PrintStream(outStreamContent))
        System.setErr(PrintStream(errStreamContent))
    }

    @AfterEach
    fun `restore output and error stream`() {
        System.setOut(originalOutStream)
        System.setErr(originalErrStream)
    }

    // region Log

    @Test
    fun `logger logs message with verbose level`() {
        testedLogger.v(fakeMessage)

        verifyLogSideEffects("V")
    }

    @Test
    fun `logger logs message with debug level`() {
        testedLogger.d(fakeMessage)

        verifyLogSideEffects("D")
    }

    @Test
    fun `logger logs message with info level`() {
        testedLogger.i(fakeMessage)

        verifyLogSideEffects("I")
    }

    @Test
    fun `logger logs message with warning level`() {
        testedLogger.w(fakeMessage)

        verifyLogSideEffects("W")
    }

    @Test
    fun `logger logs message with error level`() {
        testedLogger.e(fakeMessage)

        verifyLogSideEffects("E")
    }

    @Test
    fun `logger logs message with assert level`() {
        testedLogger.wtf(fakeMessage)

        verifyLogSideEffects("A")
    }

    @Test
    fun `logger has a dummy file writer`() {
        val logWriter: LogWriter = testedLogger.getFieldValue("logWriter")
        assertThat(logWriter).isInstanceOf(DummyLogWriter::class.java)
    }

    // endregion

    // region Internal

    private fun verifyLogSideEffects(logCatPrefix: String) {
        assertThat(outStreamContent.toString())
            .isEqualTo("$logCatPrefix/$fakeServiceName: $fakeMessage\n")
        assertThat(errStreamContent.toString()).isEmpty()
    }

    // endregion
}
