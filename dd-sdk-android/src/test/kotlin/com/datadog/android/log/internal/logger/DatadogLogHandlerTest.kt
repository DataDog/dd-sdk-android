/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import android.content.Context
import android.util.Log as AndroidLog
import android.view.Choreographer
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.sampling.Sampler
import com.datadog.android.core.internal.system.AppVersionProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.LogEventAssert.Companion.assertThat
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.log.model.LogEvent
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.tracing.AndroidTracer
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.asLogStatus
import com.datadog.android.utils.extension.toIsoFormattedTimestamp
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.setFieldValue
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
    lateinit var mockWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockAppVersionProvider: AppVersionProvider

    @Mock
    lateinit var mockSampler: Sampler

    lateinit var fakeAppVersion: String

    lateinit var fakeEnvName: String

    lateinit var fakeSdkVersion: String

    lateinit var fakeVariant: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        // To avoid java.lang.NoClassDefFoundError: android/hardware/display/DisplayManagerGlobal.
        // This class is only available in a real android JVM at runtime and not in a JUnit env.
        Choreographer::class.java.setStaticValue(
            "sThreadInstance",
            object : ThreadLocal<Choreographer>() {
                override fun initialValue(): Choreographer {
                    return mock()
                }
            }
        )
        fakeAppVersion = forge.aStringMatching("^[0-9]\\.[0-9]\\.[0-9]")
        fakeEnvName = forge.aStringMatching("[a-zA-Z0-9_:./-]{0,195}[a-zA-Z0-9_./-]")
        fakeVariant = forge.anAlphabeticalString()
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()
        fakeSdkVersion = forge.anAlphabeticalString()

        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockAppVersionProvider.version) doReturn fakeAppVersion

        testedHandler = DatadogLogHandler(
            DatadogLogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                mockTimeProvider,
                fakeSdkVersion,
                fakeEnvName,
                fakeVariant,
                mockAppVersionProvider
            ),
            mockWriter
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalTracer.get().setFieldValue("isRegistered", false)
        GlobalTracer::class.java.setStaticValue("tracer", NoopTracerFactory.create())
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

        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
                .doesNotHaveError()
        }
    }

    @Test
    fun `M not forward log to LogWriter W level is below the min supported`(
        forge: Forge
    ) {
        // Given
        testedHandler = DatadogLogHandler(
            DatadogLogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                mockTimeProvider,
                fakeSdkVersion,
                fakeEnvName,
                fakeVariant,
                mockAppVersionProvider
            ),
            mockWriter,
            minLogPriority = forge.anInt(min = fakeLevel + 1)
        )

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            forge.aNullable { fakeThrowable },
            fakeAttributes,
            fakeTags
        )

        // Then
        verifyZeroInteractions(mockWriter, mockSampler)
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

        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
                .hasError(
                    LogEvent.Error(
                        kind = fakeThrowable.javaClass.canonicalName,
                        message = fakeThrowable.message,
                        stack = fakeThrowable.stackTraceToString()
                    )
                )
        }
    }

    @Test
    fun `doesn't forward low level log to RumMonitor`(forge: Forge) {
        fakeLevel = forge.anInt(AndroidLog.VERBOSE, AndroidLog.ERROR)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @ParameterizedTest
    @ValueSource(ints = [AndroidLog.ERROR, AndroidLog.ASSERT])
    fun `forward error log to RumMonitor`(logLevel: Int) {
        testedHandler.handleLog(
            logLevel,
            fakeMessage,
            null,
            fakeAttributes,
            fakeTags
        )

        verify(rumMonitor.mockInstance).addError(
            fakeMessage,
            RumErrorSource.LOGGER,
            null,
            fakeAttributes
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [AndroidLog.ERROR, AndroidLog.ASSERT])
    fun `forward error log to RumMonitor with throwable`(logLevel: Int) {
        testedHandler.handleLog(
            logLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        verify(rumMonitor.mockInstance).addError(
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

        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDate(customTimestamp.toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion",
                        "${LogAttributes.VARIANT}:$fakeVariant"
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

        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(threadName)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
        }
    }

    @Test
    fun `forward log to LogWriter without network info`() {
        val now = System.currentTimeMillis()
        testedHandler = DatadogLogHandler(
            DatadogLogGenerator(
                fakeServiceName,
                fakeLoggerName,
                null,
                mockUserInfoProvider,
                mockTimeProvider,
                fakeSdkVersion,
                fakeEnvName,
                fakeVariant,
                mockAppVersionProvider
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

        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .doesNotHaveNetworkInfo()
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
        }
    }

    @Test
    fun `forward minimal log to LogWriter`() {
        val now = System.currentTimeMillis()
        GlobalRum.isRegistered.set(false)
        testedHandler = DatadogLogHandler(
            DatadogLogGenerator(
                fakeServiceName,
                fakeLoggerName,
                null,
                mockUserInfoProvider,
                mockTimeProvider,
                fakeSdkVersion,
                fakeEnvName,
                fakeVariant,
                mockAppVersionProvider
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

        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .doesNotHaveNetworkInfo()
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(emptyMap())
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
                .doesNotHaveError()
        }
    }

    @Test
    fun `it will add the span id and trace id if we active an active tracer`(
        @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL) fakeServiceName: String,
        forge: Forge
    ) {
        // Given
        Datadog.initialize(
            appContext.mockInstance,
            Credentials(
                forge.anAlphabeticalString(),
                forge.anAlphabeticalString(),
                Credentials.NO_VARIANT,
                null
            ),
            Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true,
                sessionReplayEnabled = true
            ).build(),
            TrackingConsent.GRANTED
        )
        val tracer = AndroidTracer.Builder().setServiceName(fakeServiceName).build()
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
        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.additionalProperties)
                .containsEntry(LogAttributes.DD_TRACE_ID, tracer.traceId)
                .containsEntry(LogAttributes.DD_SPAN_ID, tracer.spanId)
        }
        Datadog.stop()
    }

    @Test
    fun `it will not add trace deps if we do not have active an active tracer`() {
        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        // Then
        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.additionalProperties)
                .doesNotContainKey(LogAttributes.DD_TRACE_ID)
                .doesNotContainKey(LogAttributes.DD_SPAN_ID)
        }
    }

    @Test
    fun `it will add the Rum context`(forge: Forge) {
        // Given
        Datadog.initialize(
            appContext.mockInstance,
            Credentials(
                forge.anAlphabeticalString(),
                forge.anAlphabeticalString(),
                Credentials.NO_VARIANT,
                null
            ),
            Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true,
                sessionReplayEnabled = true
            ).build(),
            TrackingConsent.GRANTED
        )

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            fakeAttributes,
            fakeTags
        )

        // Then
        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.additionalProperties)
                .containsEntry(
                    LogAttributes.RUM_APPLICATION_ID,
                    rumMonitor.context.applicationId
                )
                .containsEntry(LogAttributes.RUM_SESSION_ID, rumMonitor.context.sessionId)
                .containsEntry(LogAttributes.RUM_VIEW_ID, rumMonitor.context.viewId)
                .containsEntry(LogAttributes.RUM_ACTION_ID, rumMonitor.context.actionId)
        }
        Datadog.stop()
    }

    @Test
    fun `it will not add trace deps if the flag was set to false`() {
        // Given
        testedHandler = DatadogLogHandler(
            DatadogLogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                mockTimeProvider,
                fakeSdkVersion,
                fakeEnvName,
                fakeVariant,
                mockAppVersionProvider
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
        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue.additionalProperties)
                .doesNotContainKey(LogAttributes.DD_TRACE_ID)
                .doesNotContainKey(LogAttributes.DD_SPAN_ID)
        }
    }

    @Test
    fun `it will sample out the logs when required`() {
        // Given
        whenever(mockSampler.sample()).thenReturn(false)
        testedHandler = DatadogLogHandler(
            DatadogLogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                mockTimeProvider,
                fakeSdkVersion,
                fakeEnvName,
                fakeVariant,
                mockAppVersionProvider
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
            DatadogLogGenerator(
                fakeServiceName,
                fakeLoggerName,
                mockNetworkInfoProvider,
                mockUserInfoProvider,
                mockTimeProvider,
                fakeSdkVersion,
                fakeEnvName,
                fakeVariant,
                mockAppVersionProvider
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
        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to rumMonitor.context.applicationId,
                        LogAttributes.RUM_SESSION_ID to rumMonitor.context.sessionId,
                        LogAttributes.RUM_VIEW_ID to rumMonitor.context.viewId,
                        LogAttributes.RUM_ACTION_ID to rumMonitor.context.actionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:$fakeEnvName",
                        "${LogAttributes.APPLICATION_VERSION}:$fakeAppVersion",
                        "${LogAttributes.VARIANT}:$fakeVariant"
                    )
                )
        }
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)
        val rumMonitor = GlobalRumMonitorTestConfiguration()
        val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, rumMonitor, mainLooper)
        }
    }
}
