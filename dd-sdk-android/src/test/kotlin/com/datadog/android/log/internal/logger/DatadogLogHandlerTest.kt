/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import android.util.Log as AndroidLog
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.LogAssert.Companion.assertThat
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.domain.RumContext
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
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockSampler: Sampler

    lateinit var fakeAppVersion: String

    lateinit var fakeEnvName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeAppVersion = forge.aStringMatching("^[0-9]\\.[0-9]\\.[0-9]")
        fakeEnvName = forge.aStringMatching("[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()
        CoreFeature.envName = fakeEnvName
        CoreFeature.packageVersion = fakeAppVersion
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo

        testedHandler = DatadogLogHandler(
            LogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                fakeEnvName,
                fakeAppVersion
            ),
            mockWriter
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
        val now = System.currentTimeMillis()

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
                .hasTimestampAround(now)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
                    )
                )
                .hasThrowable(null)
        }
    }

    @Test
    fun `forward log to LogWriter with throwable`() {
        val now = System.currentTimeMillis()

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
                .hasTimestampAround(now)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
                    )
                )
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
            RumErrorSource.LOGGER,
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
            RumErrorSource.LOGGER,
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
                .hasTimestampAround(customTimestamp)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
                    )
                )
        }
    }

    @Test
    fun `forward log to LogWriter on background thread`(forge: Forge) {
        val now = System.currentTimeMillis()
        val threadName = forge.anAlphabeticalString()
        val countDownLatch = CountDownLatch(1)
        val thread = Thread(
            {
                testedHandler.handleLog(
                    fakeLevel,
                    fakeMessage,
                    fakeThrowable,
                    fakeAttributes,
                    fakeTags
                )
                countDownLatch.countDown()
            },
            threadName
        )

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
                .hasTimestampAround(now)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
                    )
                )
        }
    }

    @Test
    fun `forward log to LogWriter without network info`() {
        val now = System.currentTimeMillis()
        testedHandler = DatadogLogHandler(
            LogGenerator(
                fakeServiceName,
                fakeLoggerName,
                null,
                mockUserInfoProvider,
                fakeEnvName,
                fakeAppVersion
            ),
            mockWriter
        )
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
                .hasTimestampAround(now)
                .hasNetworkInfo(null)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
                    )
                )
        }
    }

    @Test
    fun `forward minimal log to LogWriter`() {
        val now = System.currentTimeMillis()
        GlobalRum.isRegistered.set(false)
        testedHandler = DatadogLogHandler(
            LogGenerator(
                fakeServiceName,
                fakeLoggerName,
                null,
                mockUserInfoProvider,
                fakeEnvName,
                fakeAppVersion
            ),
            mockWriter
        )
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
                .hasTimestampAround(now)
                .hasNetworkInfo(null)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(emptyMap())
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
                    )
                )
                .hasThrowable(null)
        }
    }

    @Test
    fun `it will add the span id and trace id if we active an active tracer`(forge: Forge) {
        // Given
        val config =
            DatadogConfig.Builder(forge.anAlphabeticalString(), forge.anAlphabeticalString())
                .build()
        Datadog.initialize(mockContext(), config)
        val tracer = AndroidTracer.Builder().build()
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start()
        tracer.activateSpan(span)
        GlobalTracer.registerIfAbsent(tracer)

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
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
        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.attributes)
                .doesNotContainKey(LogAttributes.DD_TRACE_ID)
                .doesNotContainKey(LogAttributes.DD_SPAN_ID)
        }
    }

    @Test
    fun `it will add the Rum context`(forge: Forge) {
        // Given
        val config =
            DatadogConfig.Builder(forge.anAlphabeticalString(), forge.anAlphabeticalString())
                .build()
        Datadog.initialize(mockContext(), config)
        val rumContext = forge.getForgery<RumContext>()
        GlobalRum.updateRumContext(rumContext)
        GlobalRum.registerIfAbsent(mockRumMonitor)

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            fakeAttributes,
            fakeTags
        )

        // Then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.attributes)
                .containsEntry(
                    LogAttributes.RUM_APPLICATION_ID,
                    rumContext.applicationId
                )
                .containsEntry(LogAttributes.RUM_SESSION_ID, rumContext.sessionId)
                .containsEntry(LogAttributes.RUM_VIEW_ID, rumContext.viewId)
        }
        Datadog.invokeMethod("stop")
    }

    @Test
    fun `it will not add trace deps if the flag was set to false`(forge: Forge) {
        // Given
        testedHandler = DatadogLogHandler(
            LogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                fakeEnvName,
                fakeAppVersion
            ),
            mockWriter,
            bundleWithTraces = false
        )
        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.attributes)
                .doesNotContainKey(LogAttributes.DD_TRACE_ID)
                .doesNotContainKey(LogAttributes.DD_SPAN_ID)
        }
    }

    @Test
    fun `it will sample out the logs when required`() {
        // Given
        whenever(mockSampler.sample()).thenReturn(false)
        testedHandler = DatadogLogHandler(
            LogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                fakeEnvName,
                fakeAppVersion
            ),
            mockWriter,
            bundleWithTraces = false,
            sampler = mockSampler
        )

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
        verifyZeroInteractions(mockWriter)
    }

    @Test
    fun `it will sample in the logs when required`() {
        // Given
        val now = System.currentTimeMillis()
        whenever(mockSampler.sample()).thenReturn(true)
        testedHandler = DatadogLogHandler(
            LogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                fakeEnvName,
                fakeAppVersion
            ),
            mockWriter,
            bundleWithTraces = false,
            sampler = mockSampler
        )

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
        argumentCaptor<Log>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasLevel(fakeLevel)
                .hasMessage(fakeMessage)
                .hasTimestampAround(now)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
                    )
                )
        }
    }
}
