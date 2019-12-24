/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.assertj.LogAssert.Companion.assertThat
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.NetworkInfo
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
internal class DatadogLogHandlerTest {

    lateinit var testedHandler: LogHandler

    lateinit var fakeServiceName: String
    lateinit var fakeLoggerName: String
    lateinit var fakeMessage: String
    lateinit var fakeTags: Set<String>
    lateinit var fakeAttributes: Map<String, Any?>
    var fakeServerDate: Long = 0L
    var fakeLevel: Int = 0

    @Forgery
    lateinit var fakeThrowable: Throwable
    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @Mock
    lateinit var mockWriter: Writer
    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider
    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()
        fakeServerDate = forge.aTimestamp()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()

        whenever(mockTimeProvider.getServerTimestamp()) doReturn fakeServerDate
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo

        testedHandler = DatadogLogHandler(
            fakeServiceName,
            fakeLoggerName,
            mockWriter,
            mockNetworkInfoProvider,
            mockTimeProvider
        )
    }

    @Test
    fun `forward log to LogWriter`() {

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        argumentCaptor<Log>().apply {
            verify(mockWriter).writeLog(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasAttributes(fakeAttributes)
                .hasTags(fakeTags)
        }
    }

    @Test
    fun `forward log to LogWriter on background thread`(forge: Forge) {
        val threadName = forge.anAlphabeticalString()
        val countDownLatch = CountDownLatch(1)
        val thread = Thread({
            testedHandler.handleLog(
                fakeLevel,
                fakeMessage,
                fakeThrowable,
                fakeAttributes,
                fakeTags
            )
            countDownLatch.countDown()
        }, threadName)

        thread.start()
        countDownLatch.await(1, TimeUnit.SECONDS)

        argumentCaptor<Log>().apply {
            verify(mockWriter).writeLog(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(threadName)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasAttributes(fakeAttributes)
                .hasTags(fakeTags)
        }
    }

    @Test
    fun `forward log to LogWriter without network info`() {
        testedHandler.setFieldValue("networkInfoProvider", null as NetworkInfoProvider?)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        argumentCaptor<Log>().apply {
            verify(mockWriter).writeLog(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(null)
                .hasAttributes(fakeAttributes)
                .hasTags(fakeTags)
        }
    }

    @Test
    fun `forward minimal log to LogWriter`() {
        testedHandler.setFieldValue("networkInfoProvider", null as NetworkInfoProvider?)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        argumentCaptor<Log>().apply {
            verify(mockWriter).writeLog(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(null)
                .hasAttributes(emptyMap())
                .hasTags(emptyList())
        }
    }
}
