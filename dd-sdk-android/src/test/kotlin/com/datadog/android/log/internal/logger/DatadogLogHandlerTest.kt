/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import android.util.Log as AndroidLog
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.LogAssert.Companion.assertThat
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.monitor.NoOpRumMonitor
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setFieldValue
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import io.opentracing.noop.NoopTracerFactory
import io.opentracing.util.GlobalTracer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Mock
    lateinit var mockWriter: Writer<Log>

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockSampler: Sampler

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
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo

        testedHandler = DatadogLogHandler(
            fakeServiceName,
            fakeLoggerName,
            mockWriter,
            mockNetworkInfoProvider,
            mockTimeProvider,
            mockUserInfoProvider
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer.get().setFieldValue("isRegistered", false)
        GlobalTracer::class.java.setStaticValue("tracer", NoopTracerFactory.create())
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
    }

    @Test
    fun `forward log to LogWriter`() {

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            fakeAttributes,
            fakeTags
        )

        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasAttributes(fakeAttributes)
                .hasTags(fakeTags)
                .hasThrowable(null)
        }
    }

    @Test
    fun `forward log to LogWriter with throwable`() {

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasAttributes(fakeAttributes)
                .hasTags(fakeTags)
                .hasThrowable(fakeThrowable)
        }
    }

    @Test
    fun `doesn't forward low level log to RumMonitor`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        fakeLevel = forge.anInt(AndroidLog.VERBOSE, AndroidLog.ERROR)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `forward error log to RumMonitor`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        fakeLevel = forge.anElementFrom(AndroidLog.ERROR, AndroidLog.ASSERT)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            fakeAttributes,
            fakeTags
        )

        verify(mockRumMonitor).addError(
            fakeMessage,
            fakeLoggerName,
            null,
            fakeAttributes
        )
    }

    @Test
    fun `forward error log to RumMonitor with throwable`(forge: Forge) {
        GlobalRum.registerIfAbsent(mockRumMonitor)
        fakeLevel = forge.anElementFrom(AndroidLog.ERROR, AndroidLog.ASSERT)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        verify(mockRumMonitor).addError(
            fakeMessage,
            fakeLoggerName,
            fakeThrowable,
            fakeAttributes
        )
    }

    @Test
    fun `forward log with custom timestamp to LogWriter`(forge: Forge) {
        val customTimestamp = forge.aLong()

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            customTimestamp
        )

        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(customTimestamp)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
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
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(threadName)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
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
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(null)
                .hasUserInfo(fakeUserInfo)
                .hasAttributes(fakeAttributes)
                .hasTags(fakeTags)
        }
    }

    @Test
    fun `forward minimal log to LogWriter`() {
        GlobalRum.isRegistered.set(false)
        testedHandler.setFieldValue("networkInfoProvider", null as NetworkInfoProvider?)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(null)
                .hasUserInfo(fakeUserInfo)
                .hasAttributes(emptyMap())
                .hasTags(emptyList())
                .hasThrowable(null)
        }
    }

    @Test
    fun `it will add the span id and trace id if we active an active tracer`(forge: Forge) {
        // given
        val config = DatadogConfig.Builder(forge.anAlphabeticalString()).build()
        Datadog.initialize(mockContext(), config)
        val tracer = AndroidTracer.Builder().build()
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start()
        tracer.activateSpan(span)
        GlobalTracer.registerIfAbsent(tracer)

        // when
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.attributes)
                .containsEntry(LogAttributes.DD_TRACE_ID, tracer.traceId)
                .containsEntry(LogAttributes.DD_SPAN_ID, tracer.spanId)
        }
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `it will not add trace deps if we do not have active an active tracer`(forge: Forge) {
        // when
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.attributes)
                .doesNotContainKey(LogAttributes.DD_TRACE_ID)
                .doesNotContainKey(LogAttributes.DD_SPAN_ID)
        }
    }

    @Test
    fun `it will add the Rum context`(forge: Forge) {
        // given
        val config = DatadogConfig.Builder(forge.anAlphabeticalString()).build()
        Datadog.initialize(mockContext(), config)
        val rumContext = forge.getForgery<RumContext>()
        GlobalRum.updateContext(rumContext)
        GlobalRum.registerIfAbsent(mockRumMonitor)

        // when
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            fakeAttributes,
            fakeTags
        )

        // then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.attributes)
                .containsEntry(
                    LogAttributes.RUM_APPLICATION_ID,
                    rumContext.applicationId.toString()
                )
                .containsEntry(LogAttributes.RUM_SESSION_ID, rumContext.sessionId.toString())
                .containsEntry(LogAttributes.RUM_VIEW_ID, rumContext.viewId?.toString())
        }
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `it will not add trace deps if the flag was set to false`(forge: Forge) {
        // given
        testedHandler = DatadogLogHandler(
            fakeServiceName,
            fakeLoggerName,
            mockWriter,
            mockNetworkInfoProvider,
            mockTimeProvider,
            mockUserInfoProvider,
            bundleWithTraces = false
        )
        // when
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.attributes)
                .doesNotContainKey(LogAttributes.DD_TRACE_ID)
                .doesNotContainKey(LogAttributes.DD_SPAN_ID)
        }
    }

    @Test
    fun `it will sample out the logs when required`() {
        // given
        whenever(mockSampler.sample()).thenReturn(false)
        testedHandler = DatadogLogHandler(
            fakeServiceName,
            fakeLoggerName,
            mockWriter,
            mockNetworkInfoProvider,
            mockTimeProvider,
            mockUserInfoProvider,
            bundleWithTraces = false,
            sampler = mockSampler
        )

        // when
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // then
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `it will sample in the logs when required`() {
        // given
        whenever(mockSampler.sample()).thenReturn(true)
        testedHandler = DatadogLogHandler(
            fakeServiceName,
            fakeLoggerName,
            mockWriter,
            mockNetworkInfoProvider,
            mockTimeProvider,
            mockUserInfoProvider,
            bundleWithTraces = false,
            sampler = mockSampler
        )

        // when
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestamp(fakeServerDate)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasAttributes(fakeAttributes)
                .hasTags(fakeTags)
        }
    }
}
