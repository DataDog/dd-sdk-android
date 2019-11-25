/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import com.datadog.android.log.assertj.LogAssert.Companion.assertThat
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogWriter
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.IOException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
internal class LoggerThrowableTest {

    lateinit var testedLogger: Logger

    @Mock(lenient = true)
    lateinit var mockContext: Context
    @Mock
    lateinit var mockLogStrategy: LogStrategy
    @Mock
    lateinit var mockLogWriter: LogWriter

    @BeforeEach
    fun `set up logger`(forge: Forge) {
        whenever(mockContext.applicationContext) doReturn mockContext
        whenever(mockLogStrategy.getLogWriter()) doReturn mockLogWriter

        testedLogger = Logger.Builder()
            .withLogStrategy(mockLogStrategy)
            .build()
    }

    @Test
    fun `log verbose with exception`(forge: Forge) {
        val message = forge.anAlphabeticalString()
        val exception = IOException(forge.anAlphabeticalString())

        testedLogger.v(message, exception)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasMessage(message)
                .hasThrowable(exception)
        }
    }

    @Test
    fun `log debug with exception`(forge: Forge) {
        val message = forge.anAlphabeticalString()
        val exception = IOException(forge.anAlphabeticalString())

        testedLogger.d(message, exception)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasMessage(message)
                .hasThrowable(exception)
        }
    }

    @Test
    fun `log info with exception`(forge: Forge) {
        val message = forge.anAlphabeticalString()
        val exception = IOException(forge.anAlphabeticalString())

        testedLogger.i(message, exception)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasMessage(message)
                .hasThrowable(exception)
        }
    }

    @Test
    fun `log warning with exception`(forge: Forge) {
        val message = forge.anAlphabeticalString()
        val exception = IOException(forge.anAlphabeticalString())

        testedLogger.w(message, exception)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasMessage(message)
                .hasThrowable(exception)
        }
    }

    @Test
    fun `log error with exception`(forge: Forge) {
        val message = forge.anAlphabeticalString()
        val exception = IOException(forge.anAlphabeticalString())

        testedLogger.e(message, exception)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasMessage(message)
                .hasThrowable(exception)
        }
    }

    @Test
    fun `log wtf with exception`(forge: Forge, @Forgery exception: Throwable) {
        val message = forge.anAlphabeticalString()

        testedLogger.wtf(message, exception)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasMessage(message)
                .hasThrowable(exception)
        }
    }
}
