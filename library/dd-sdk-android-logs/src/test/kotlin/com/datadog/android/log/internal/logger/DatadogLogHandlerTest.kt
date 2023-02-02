/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.core.sampling.Sampler
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.assertj.LogEventAssert.Companion.assertThat
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.extension.asLogStatus
import com.datadog.android.utils.extension.toIsoFormattedTimestamp
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.storage.DataWriter
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.util.Log as AndroidLog

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
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeRumApplicationId: UUID

    @Forgery
    lateinit var fakeRumSessionId: UUID

    @Forgery
    lateinit var fakeRumViewId: UUID

    @Forgery
    lateinit var fakeRumActionId: UUID

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockSampler: Sampler

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        fakeLoggerName = forge.anAlphabeticalString()
        fakeMessage = forge.anAlphabeticalString()
        fakeLevel = forge.anInt(2, 8)
        fakeAttributes = forge.aMap { anAlphabeticalString() to anInt() }
        fakeTags = forge.aList { anAlphabeticalString() }.toSet()
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = 0L
            ),
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(
                    Feature.RUM_FEATURE_NAME,
                    mapOf(
                        "application_id" to fakeRumApplicationId,
                        "session_id" to fakeRumSessionId,
                        "view_id" to fakeRumViewId,
                        "action_id" to fakeRumActionId
                    )
                )
            }
        )

        whenever(
            mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        ) doReturn mockLogsFeatureScope
        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        testedHandler = DatadogLogHandler(
            loggerName = fakeLoggerName,
            logGenerator = DatadogLogGenerator(
                fakeServiceName
            ),
            sdkCore = mockSdkCore,
            writer = mockWriter,
            attachNetworkInfo = true
        )
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

        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
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
            loggerName = fakeLoggerName,
            logGenerator = DatadogLogGenerator(
                fakeServiceName
            ),
            sdkCore = mockSdkCore,
            writer = mockWriter,
            attachNetworkInfo = true,
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

        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
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
    fun `forward log to LogWriter with error strings`(
        @StringForgery errorKind: String,
        @StringForgery errorMessage: String,
        @StringForgery errorStack: String
    ) {
        val now = System.currentTimeMillis()

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            errorKind,
            errorMessage,
            errorStack,
            fakeAttributes,
            fakeTags
        )

        argumentCaptor<LogEvent>().apply {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
                .hasError(
                    LogEvent.Error(
                        kind = errorKind,
                        message = errorMessage,
                        stack = errorStack
                    )
                )
        }
    }

    @Disabled
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
// TODO RUMM-3005
//        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Disabled
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
        // TODO RUMM-3005
//        verify(rumMonitor.mockInstance).addError(
//            fakeMessage,
//            RumErrorSource.LOGGER,
//            null,
//            fakeAttributes
//        )
    }

    @Disabled
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

        // TODO RUMM-3005
//        verify(rumMonitor.mockInstance).addError(
//            fakeMessage,
//            RumErrorSource.LOGGER,
//            fakeThrowable,
//            fakeAttributes
//        )
    }

    @Disabled
    @Test
    fun `doesn't forward low level log with string errors to RumMonitor`(
        forge: Forge,
        @StringForgery errorKind: String,
        @StringForgery errorMessage: String,
        @StringForgery errorStack: String
    ) {
        fakeLevel = forge.anInt(AndroidLog.VERBOSE, AndroidLog.ERROR)

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            errorKind,
            errorMessage,
            errorStack,
            fakeAttributes,
            fakeTags
        )

// TODO RUMM-3005
//        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(ints = [AndroidLog.ERROR, AndroidLog.ASSERT])
    fun `forward error log with error strings to RumMonitor`(
        logLevel: Int,
        @StringForgery errorKind: String,
        @StringForgery errorMessage: String,
        @StringForgery errorStack: String
    ) {
        testedHandler.handleLog(
            logLevel,
            fakeMessage,
            errorKind,
            errorMessage,
            errorStack,
            fakeAttributes,
            fakeTags
        )

        // TODO RUMM-3005
//        verify(rumMonitor.mockInstance).addErrorWithStacktrace(
//            fakeMessage,
//            RumErrorSource.LOGGER,
//            errorStack,
//            fakeAttributes
//        )
    }

    @Test
    fun `forward log with custom timestamp to LogWriter`(forge: Forge) {
        val customTimestamp = forge.aPositiveLong()
        val serverTimeOffsetMs = forge.aLong(min = -10000L, max = 10000L)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = serverTimeOffsetMs
            )
        )

        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags,
            customTimestamp
        )

        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDate((customTimestamp + serverTimeOffsetMs).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
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

        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(threadName)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `forward log to LogWriter without network info`() {
        val now = System.currentTimeMillis()
        testedHandler = DatadogLogHandler(
            loggerName = fakeLoggerName,
            logGenerator = DatadogLogGenerator(
                fakeServiceName
            ),
            sdkCore = mockSdkCore,
            writer = mockWriter,
            attachNetworkInfo = false
        )
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            fakeThrowable,
            fakeAttributes,
            fakeTags
        )

        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .doesNotHaveNetworkInfo()
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `forward minimal log to LogWriter`() {
        // Given
        val now = System.currentTimeMillis()
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                remove(Feature.RUM_FEATURE_NAME)
            }
        )
        testedHandler = DatadogLogHandler(
            loggerName = fakeLoggerName,
            logGenerator = DatadogLogGenerator(
                fakeServiceName
            ),
            sdkCore = mockSdkCore,
            writer = mockWriter,
            attachNetworkInfo = false
        )

        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            emptyMap(),
            emptySet()
        )

        // Then
        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasThreadName(Thread.currentThread().name)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .doesNotHaveNetworkInfo()
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(emptyMap())
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
                .doesNotHaveError()
        }
    }

    @Test
    fun `it will add the span id and trace id if we active an active tracer`(
        @StringForgery(type = StringForgeryType.HEXADECIMAL) fakeSpanId: String,
        @StringForgery(type = StringForgeryType.HEXADECIMAL) fakeTraceId: String
    ) {
        // Given
        val threadName = Thread.currentThread().name

        val tracingContext = mapOf(
            "context@$threadName" to mapOf(
                "span_id" to fakeSpanId,
                "trace_id" to fakeTraceId
            )
        )
        fakeDatadogContext = fakeDatadogContext.copy(
            featuresContext = fakeDatadogContext.featuresContext.toMutableMap().apply {
                put(Feature.TRACING_FEATURE_NAME, tracingContext)
            }
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
        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue.additionalProperties)
                .containsEntry(LogAttributes.DD_TRACE_ID, fakeTraceId)
                .containsEntry(LogAttributes.DD_SPAN_ID, fakeSpanId)
        }
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
        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue.additionalProperties)
                .doesNotContainKey(LogAttributes.DD_TRACE_ID)
                .doesNotContainKey(LogAttributes.DD_SPAN_ID)
        }
    }

    @Test
    fun `it will add the Rum context`() {
        // When
        testedHandler.handleLog(
            fakeLevel,
            fakeMessage,
            null,
            fakeAttributes,
            fakeTags
        )

        // Then
        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue.additionalProperties)
                .containsEntry(
                    LogAttributes.RUM_APPLICATION_ID,
                    fakeRumApplicationId
                )
                .containsEntry(LogAttributes.RUM_SESSION_ID, fakeRumSessionId)
                .containsEntry(LogAttributes.RUM_VIEW_ID, fakeRumViewId)
                .containsEntry(LogAttributes.RUM_ACTION_ID, fakeRumActionId)
        }
    }

    @Test
    fun `it will not add trace deps if the flag was set to false`() {
        // Given
        testedHandler = DatadogLogHandler(
            loggerName = fakeLoggerName,
            logGenerator = DatadogLogGenerator(
                fakeServiceName
            ),
            sdkCore = mockSdkCore,
            writer = mockWriter,
            attachNetworkInfo = true,
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
        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

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
            loggerName = fakeLoggerName,
            logGenerator = DatadogLogGenerator(
                fakeServiceName
            ),
            sdkCore = mockSdkCore,
            writer = mockWriter,
            attachNetworkInfo = true,
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
            loggerName = fakeLoggerName,
            logGenerator = DatadogLogGenerator(
                fakeServiceName
            ),
            sdkCore = mockSdkCore,
            writer = mockWriter,
            attachNetworkInfo = true,
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
        argumentCaptor<LogEvent> {
            verify(mockWriter).write(eq(mockEventBatchWriter), capture())

            assertThat(lastValue)
                .hasServiceName(fakeServiceName)
                .hasLoggerName(fakeLoggerName)
                .hasStatus(fakeLevel.asLogStatus())
                .hasMessage(fakeMessage)
                .hasDateAround(now)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    fakeTags + setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    // TODO RUMM-3005
    /*
    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
    */
}
