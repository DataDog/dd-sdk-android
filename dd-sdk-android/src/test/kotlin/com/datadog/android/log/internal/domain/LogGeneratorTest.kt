/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.LogAssert.Companion.assertThat
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpanContext
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.setStaticValue
import com.datadog.trace.api.interceptor.MutableSpan
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Span
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import java.util.UUID
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
internal class LogGeneratorTest {

    lateinit var testedLogGenerator: LogGenerator

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockSpanContext: DDSpanContext

    @Mock(extraInterfaces = [MutableSpan::class])
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider
    lateinit var fakeServiceName: String
    lateinit var fakeLoggerName: String
    lateinit var fakeAttributes: Map<String, Any?>
    lateinit var fakeTags: Set<String>
    lateinit var fakeAppVersion: String
    lateinit var fakeEnvName: String
    lateinit var fakeLogMessage: String
    lateinit var fakeThrowable: Throwable

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @Forgery
    lateinit var fakeUserInfo: UserInfo
    var fakeTimestamp = 0L
    var fakeLevel: Int = 0
    lateinit var fakeAppId: String
    lateinit var fakeSessionId: String
    lateinit var fakeViewId: String
    lateinit var fakeThreadName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeLogMessage = forge.anAlphabeticalString()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()
        fakeAppVersion = forge.aStringMatching("^[0-9]\\.[0-9]\\.[0-9]")
        fakeEnvName = forge.aStringMatching("[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
        fakeThrowable = forge.aThrowable()
        fakeTimestamp = System.currentTimeMillis()
        fakeAppId = UUID.randomUUID().toString()
        fakeSessionId = UUID.randomUUID().toString()
        fakeViewId = forge.anAlphabeticalString()
        fakeThreadName = forge.anAlphabeticalString()

        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockTracer.activeSpan()).thenReturn(mockSpan)
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId
        GlobalRum.updateRumContext(RumContext(fakeAppId, fakeSessionId, fakeViewId))
        GlobalRum.registerIfAbsent(mockRumMonitor)
        GlobalTracer.registerIfAbsent(mockTracer)
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            fakeEnvName,
            fakeAppVersion
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
    }

    @Test
    fun `M add log message W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasMessage(fakeLogMessage)
    }

    @Test
    fun `M add the log level W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasLevel(fakeLevel)
    }

    @Test
    fun `M add the service name W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasServiceName(fakeServiceName)
    }

    @Test
    fun `M add the logger name W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasLoggerName(fakeLoggerName)
    }

    @Test
    fun `M add the thread name W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            fakeThreadName
        )

        // THEN
        assertThat(log).hasThreadName(fakeThreadName)
    }

    @Test
    fun `M add the thread name as current thread W creating the Log {threadName not provided}`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasThreadName(Thread.currentThread().name)
    }

    @Test
    fun `M add the log timestamp W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasTimestamp(fakeTimestamp)
    }

    @Test
    fun `M add the throwable W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasThrowable(fakeThrowable)
    }

    @Test
    fun `M add the userNetworkInfo W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasUserInfo(fakeUserInfo)
    }

    @Test
    fun `M add the networkInfo W creating the Log`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasNetworkInfo(fakeNetworkInfo)
    }

    @Test
    fun `M not add the networkInfo W creating Log {networkInfoProvider is null}`() {
        // GIVEN
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            null,
            mockUserInfoProvider,
            fakeEnvName,
            fakeAppVersion
        )
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).doesNotHaveNetworkInfo()
    }

    @Test
    fun `M add the envNameTag W not empty`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).containsTags(listOf("${LogAttributes.ENV}:$fakeEnvName"))
    }

    @Test
    fun `M not add the envNameTag W empty`() {
        // GIVEN
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            "",
            fakeAppVersion
        )

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        val expectedTags = fakeTags + "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
        assertThat(log).hasExactlyTags(expectedTags)
    }

    @Test
    fun `M add the appVersionTag W not empty`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).containsTags(listOf("${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"))
    }

    @Test
    fun `M not add the appVersionTag W not empty`() {
        // GIVEN
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            fakeEnvName,
            ""
        )

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        val expectedTags = fakeTags + "${LogAttributes.ENV}:$fakeEnvName"
        assertThat(log).hasExactlyTags(expectedTags)
    }

    @Test
    fun `M bundle the trace information W required`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).containsAttributes(
            mapOf(
                LogAttributes.DD_TRACE_ID to fakeTraceId,
                LogAttributes.DD_SPAN_ID to fakeSpanId
            )
        )
    }

    @Test
    fun `M do nothing W required to bundle the trace information {no active Span}`() {
        // GIVEN
        whenever(mockTracer.activeSpan()).doReturn(null)
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.RUM_APPLICATION_ID to fakeAppId,
            LogAttributes.RUM_SESSION_ID to fakeSessionId,
            LogAttributes.RUM_VIEW_ID to fakeViewId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M do nothing W required to bundle the trace information {AndroidTracer not registered}`() {
        // GIVEN
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.RUM_APPLICATION_ID to fakeAppId,
            LogAttributes.RUM_SESSION_ID to fakeSessionId,
            LogAttributes.RUM_VIEW_ID to fakeViewId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M do nothing W not required to bundle the trace information`() {
        // GIVEN
        GlobalTracer::class.java.setStaticValue("isRegistered", false)
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            bundleWithTraces = false
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.RUM_APPLICATION_ID to fakeAppId,
            LogAttributes.RUM_SESSION_ID to fakeSessionId,
            LogAttributes.RUM_VIEW_ID to fakeViewId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M bundle the RUM information W required`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).containsAttributes(
            mapOf(
                LogAttributes.RUM_APPLICATION_ID to fakeAppId,
                LogAttributes.RUM_SESSION_ID to fakeSessionId,
                LogAttributes.RUM_VIEW_ID to fakeViewId
            )
        )
    }

    @Test
    fun `M do nothing W required to bundle the rum information {RumMonitor not registered}`() {
        // GIVEN
        GlobalRum.isRegistered.set(false)

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.DD_TRACE_ID to fakeTraceId,
            LogAttributes.DD_SPAN_ID to fakeSpanId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }

    @Test
    fun `M do nothing W not required to bundle the rum information`() {
        // GIVEN
        GlobalRum.isRegistered.set(false)

        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            bundleWithRum = false
        )

        // THEN
        val expectedAttributes = fakeAttributes + mapOf(
            LogAttributes.DD_TRACE_ID to fakeTraceId,
            LogAttributes.DD_SPAN_ID to fakeSpanId
        )
        assertThat(log).hasExactlyAttributes(expectedAttributes)
    }
}
