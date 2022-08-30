/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.LogEventAssert.Companion.assertThat
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.extension.asLogStatus
import com.datadog.android.utils.extension.toIsoFormattedTimestamp
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpanContext
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
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
import org.assertj.core.api.Assertions
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogGeneratorTest {

    lateinit var testedLogGenerator: LogGenerator

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockTracer: Tracer

    @Mock
    lateinit var mockSpanContext: DDSpanContext

    @Mock(extraInterfaces = [MutableSpan::class])
    lateinit var mockSpan: Span

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider
    lateinit var fakeServiceName: String
    lateinit var fakeLoggerName: String
    lateinit var fakeAttributes: Map<String, Any?>
    lateinit var fakeTags: Set<String>
    lateinit var fakeAppVersion: String
    lateinit var fakeEnvName: String
    lateinit var fakeVariant: String
    lateinit var fakeLogMessage: String
    lateinit var fakeThrowable: Throwable

    @StringForgery
    lateinit var fakeSdkVersion: String

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
    lateinit var fakeThreadName: String
    private var fakeTimeOffset: Long = 0L

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
        fakeVariant = forge.anAlphabeticalString()
        fakeThrowable = forge.aThrowable()
        fakeTimestamp = System.currentTimeMillis()
        fakeThreadName = forge.anAlphabeticalString()
        fakeTimeOffset = forge.aLong(
            min = -fakeTimestamp,
            max = Long.MAX_VALUE - fakeTimestamp
        )

        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockTracer.activeSpan()).thenReturn(mockSpan)
        whenever(mockSpan.context()) doReturn mockSpanContext
        whenever(mockSpanContext.toSpanId()) doReturn fakeSpanId
        whenever(mockSpanContext.toTraceId()) doReturn fakeTraceId
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(fakeTimeOffset)
        GlobalTracer.registerIfAbsent(mockTracer)
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            fakeSdkVersion,
            fakeEnvName,
            fakeAppVersion,
            fakeVariant
        )
    }

    @AfterEach
    fun `tear down`() {
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
        assertThat(log).hasStatus(fakeLevel.asLogStatus())
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
    fun `M add the logger version W creating the Log`() {
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
        assertThat(log).hasLoggerVersion(fakeSdkVersion)
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
    fun `M add the log timestamp and correct the server offset W creating the Log`() {
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
        val expected = (fakeTimestamp + fakeTimeOffset).toIsoFormattedTimestamp()
        assertThat(log).hasDate(expected)
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
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeThrowable.javaClass.canonicalName,
                stack = fakeThrowable.stackTraceToString(),
                message = fakeThrowable.message
            )
        )
    }

    @Test
    fun `M add use the Throwable class simple name W creating the Log { Throwable anonymous }`() {
        // WHEN
        val fakeAnonymousThrowable = object : Throwable(cause = fakeThrowable) {}
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeAnonymousThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasError(
            LogEvent.Error(
                kind = fakeAnonymousThrowable.javaClass.simpleName,
                stack = fakeAnonymousThrowable.stackTraceToString(),
                message = fakeAnonymousThrowable.message
            )
        )
    }

    @Test
    fun `M use custom userInfo W creating the Log { userInfo provided }`(
        @Forgery fakeCustomUserInfo: UserInfo
    ) {
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            userInfo = fakeCustomUserInfo
        )

        // THEN
        assertThat(log).hasUserInfo(fakeCustomUserInfo)
    }

    @Test
    fun `M add the userInfo W creating the Log`() {
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
            mockTimeProvider,
            fakeSdkVersion,
            fakeEnvName,
            fakeAppVersion,
            fakeVariant
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
    fun `M use custom networkInfo W creating Log { networkInfo provided }`(
        @Forgery fakeCustomNetworkInfo: NetworkInfo
    ) {
        // GIVEN
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            null,
            mockUserInfoProvider,
            mockTimeProvider,
            fakeSdkVersion,
            fakeEnvName,
            fakeAppVersion,
            fakeVariant
        )
        // WHEN
        val log = testedLogGenerator.generateLog(
            fakeLevel,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp,
            networkInfo = fakeCustomNetworkInfo
        )

        // THEN
        assertThat(log).hasNetworkInfo(fakeCustomNetworkInfo)
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
        val deserializedTags = log.ddtags.split(",")
        Assertions.assertThat(deserializedTags).contains("${LogAttributes.ENV}:$fakeEnvName")
    }

    @Test
    fun `M not add the envNameTag W empty`() {
        // GIVEN
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            fakeSdkVersion,
            "",
            fakeAppVersion,
            fakeVariant
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
        val expectedTags = fakeTags +
            "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion" +
            "${LogAttributes.VARIANT}:$fakeVariant"
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
        val deserializedTags = log.ddtags.split(",")
        Assertions.assertThat(deserializedTags)
            .contains("${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion")
    }

    @Test
    fun `M not add the appVersionTag W empty`() {
        // GIVEN
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            fakeSdkVersion,
            fakeEnvName,
            "",
            fakeVariant
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
        val expectedTags = fakeTags +
            "${LogAttributes.ENV}:$fakeEnvName" +
            "${LogAttributes.VARIANT}:$fakeVariant"
        assertThat(log).hasExactlyTags(expectedTags)
    }

    @Test
    fun `M add the variantTag W not empty`() {
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
        val deserializedTags = log.ddtags.split(",")
        Assertions.assertThat(deserializedTags)
            .contains("${LogAttributes.VARIANT}:$fakeVariant")
    }

    @Test
    fun `M not add the variantTag W empty`() {
        // GIVEN
        testedLogGenerator = LogGenerator(
            fakeServiceName,
            fakeLoggerName,
            mockNetworkInfoProvider,
            mockUserInfoProvider,
            mockTimeProvider,
            fakeSdkVersion,
            fakeEnvName,
            fakeAppVersion,
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
        val expectedTags = fakeTags +
            "${LogAttributes.ENV}:$fakeEnvName" +
            "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion"
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
        Assertions.assertThat(log.additionalProperties).containsAllEntriesOf(
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
            LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
            LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
            LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
            LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
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
            LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
            LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
            LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
            LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
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
            LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
            LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
            LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
            LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
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
        Assertions.assertThat(log.additionalProperties).containsAllEntriesOf(
            mapOf(
                LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
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

    @Test
    fun `M use status CRITICAL W creating the Log { level ASSERT }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.ASSERT,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.CRITICAL)
    }

    @Test
    fun `M use status ERROR W creating the Log { level ERROR }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.ERROR,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.ERROR)
    }

    @Test
    fun `M use status EMERGENCY W creating the Log { level CRASH }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            LogGenerator.CRASH,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.EMERGENCY)
    }

    @Test
    fun `M use status WARN W creating the Log { level WARN }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.WARN,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.WARN)
    }

    @Test
    fun `M use status INFO W creating the Log { level INFO }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.INFO,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.INFO)
    }

    @Test
    fun `M use status DEBUG W creating the Log { level DEBUG }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.DEBUG,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.DEBUG)
    }

    @Test
    fun `M use status TRACE W creating the Log { level VERBOSE }`() {
        // WHEN
        val log = testedLogGenerator.generateLog(
            android.util.Log.VERBOSE,
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.TRACE)
    }

    @Test
    fun `M use status DEBUG W creating the Log { other level }`(forge: Forge) {
        // WHEN
        val log = testedLogGenerator.generateLog(
            forge.anInt(min = LogGenerator.CRASH + 1),
            fakeLogMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            fakeTimestamp
        )

        // THEN
        assertThat(log).hasStatus(LogEvent.Status.DEBUG)
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
