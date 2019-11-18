/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import android.content.Context
import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogAssert.Companion.assertThat
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
import java.util.Date
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
internal class LoggerContextTest {

    lateinit var testedLogger: Logger

    @Mock
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
            .overrideLogStrategy(mockLogStrategy)
            .build()
    }

    @Test
    fun `add boolean field to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aBool()
        val message = forge.anAlphabeticalString()

        testedLogger.addField(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasFields(mapOf(key to value))
        }
    }

    @Test
    fun `add int field to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.anInt()
        val message = forge.anAlphabeticalString()

        testedLogger.addField(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasFields(mapOf(key to value))
        }
    }

    @Test
    fun `add long field to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aLong()
        val message = forge.anAlphabeticalString()

        testedLogger.addField(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasFields(mapOf(key to value))
        }
    }

    @Test
    fun `add float field to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aFloat()
        val message = forge.anAlphabeticalString()

        testedLogger.addField(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasFields(mapOf(key to value))
        }
    }

    @Test
    fun `add double field to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aDouble()
        val message = forge.anAlphabeticalString()

        testedLogger.addField(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasFields(mapOf(key to value))
        }
    }

    @Test
    fun `add String field to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addField(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasFields(mapOf(key to value))
        }
    }

    @Test
    fun `add Date field to logger`(forge: Forge, @Forgery value: Date) {
        val key = forge.anAlphabeticalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addField(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasFields(mapOf(key to value))
        }
    }
}
