/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
internal class LoggerNoLogcatTest {

    lateinit var testedLogger: Logger

    @Mock
    lateinit var mockContext: Context

    private lateinit var originalErrStream: PrintStream
    private lateinit var originalOutStream: PrintStream
    private lateinit var outStreamContent: ByteArrayOutputStream
    private lateinit var errStreamContent: ByteArrayOutputStream

    @BeforeEach
    fun `set up logger`() {
        whenever(mockContext.applicationContext) doReturn mockContext
        testedLogger = Logger.Builder(mockContext, "not-a-token")
            .setTimestampsEnabled(true)
            .setLogcatLogsEnabled(false)
            .setNetworkInfoEnabled(true)
            .setUserAgentEnabled(true)
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

    // TODO allow logging with an error !

    @Test
    fun `logger logs message with verbose level`(@Forgery forge: Forge) {
        val message = forge.anAlphabeticalString()

        testedLogger.v(message)

        assertThat(outStreamContent.toString()).isEmpty()
        assertThat(errStreamContent.toString()).isEmpty()

        // TODO assert log object is persisted somewhere
    }

    @Test
    fun `logger logs message with debug level`(@Forgery forge: Forge) {
        val message = forge.anAlphabeticalString()

        testedLogger.d(message)

        assertThat(outStreamContent.toString()).isEmpty()
        assertThat(errStreamContent.toString()).isEmpty()

        // TODO assert log object is persisted somewhere
    }

    @Test
    fun `logger logs message with info level`(@Forgery forge: Forge) {
        val message = forge.anAlphabeticalString()

        testedLogger.i(message)

        assertThat(outStreamContent.toString()).isEmpty()
        assertThat(errStreamContent.toString()).isEmpty()

        // TODO assert log object is persisted somewhere
    }

    @Test
    fun `logger logs message with warning level`(@Forgery forge: Forge) {
        val message = forge.anAlphabeticalString()

        testedLogger.w(message)

        assertThat(outStreamContent.toString()).isEmpty()
        assertThat(errStreamContent.toString()).isEmpty()

        // TODO assert log object is persisted somewhere
    }

    @Test
    fun `logger logs message with error level`(@Forgery forge: Forge) {
        val message = forge.anAlphabeticalString()

        testedLogger.e(message)

        assertThat(outStreamContent.toString()).isEmpty()
        assertThat(errStreamContent.toString()).isEmpty()

        // TODO assert log object is persisted somewhere
    }

    @Test
    fun `logger logs message with assert level`(@Forgery forge: Forge) {
        val message = forge.anAlphabeticalString()

        testedLogger.wtf(message)

        assertThat(outStreamContent.toString()).isEmpty()
        assertThat(errStreamContent.toString()).isEmpty()

        // TODO assert log object is persisted somewhere
    }

    // endregion
}
