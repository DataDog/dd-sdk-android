/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.domain.event.LogEventMapperWrapper
import com.datadog.android.log.internal.net.LogsRequestFactory
import com.datadog.android.log.internal.storage.LogsDataWriter
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.extension.toIsoFormattedTimestamp
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import com.datadog.android.log.assertj.LogEventAssert.Companion.assertThat as assertThatLog

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsFeatureTest {

    private lateinit var testedFeature: LogsFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockDataWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockEventMapper: EventMapper<LogEvent>

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockInternalLogger: InternalLogger

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

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpointUrl: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeSpanId: String

    @StringForgery(StringForgeryType.HEXADECIMAL)
    lateinit var fakeTraceId: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeThreadName: String

    @StringForgery(regex = "[a-z]{2,4}(\\.[a-z]{3,8}){2,4}")
    lateinit var fakePackageName: String

    private var fakeServerTimeOffset: Long = 0L

    @BeforeEach
    fun `set up`(
        forge: Forge
    ) {
        val now = System.currentTimeMillis()
        fakeServerTimeOffset = forge.aLong(min = -now, max = Long.MAX_VALUE - now)

        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        whenever(mockApplicationContext.packageName) doReturn fakePackageName
        whenever(
            mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        ) doReturn mockLogsFeatureScope

        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerTimeOffset
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
                put(
                    Feature.TRACING_FEATURE_NAME,
                    mapOf(
                        "context@$fakeThreadName" to mapOf(
                            "span_id" to fakeSpanId,
                            "trace_id" to fakeTraceId
                        )
                    )
                )
            }
        )

        testedFeature = LogsFeature(mockSdkCore, fakeEndpointUrl, mockEventMapper)
    }

    @Test
    fun `𝕄 initialize data writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockApplicationContext)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(LogsDataWriter::class.java)
    }

    @Test
    fun `𝕄 use the eventMapper 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockApplicationContext)

        // Then
        val dataWriter = testedFeature.dataWriter as? LogsDataWriter
        val logMapperSerializer = dataWriter?.serializer as? MapperSerializer<LogEvent>
        val logEventMapperWrapper = logMapperSerializer
            ?.getFieldValue<LogEventMapperWrapper, MapperSerializer<LogEvent>>("eventMapper")
        val logEventMapper = logEventMapperWrapper?.wrappedEventMapper
        assertThat(logEventMapper).isSameAs(mockEventMapper)
    }

    @Test
    fun `𝕄 initialize packageName 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockApplicationContext)

        // Then
        assertThat(testedFeature.packageName).isEqualTo(fakePackageName)
    }

    @Test
    fun `𝕄 provide logs feature name 𝕎 name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(Feature.LOGS_FEATURE_NAME)
    }

    @Test
    fun `𝕄 provide logs request factory 𝕎 requestFactory()`() {
        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(LogsRequestFactory::class.java)
    }

    @Test
    fun `𝕄 provide default storage configuration 𝕎 storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }

    // region FeatureEventReceiver#onReceive

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { unknown event type }`() {
        // Given
        testedFeature.dataWriter = mockDataWriter

        // When
        testedFeature.onReceive(Any())

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                LogsFeature.UNSUPPORTED_EVENT_TYPE.format(
                    Locale.US,
                    Any()::class.java.canonicalName
                )
            )
        }
        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { unknown type property value }`(
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val event = mapOf(
            "type" to forge.anAlphabeticalString()
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                LogsFeature.UNKNOWN_EVENT_TYPE_PROPERTY_VALUE.format(Locale.US, event["type"])
            )
        }

        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockDataWriter)
    }

    // endregion

    // region FeatureEventReceiver#onReceive

    @ParameterizedTest
    @EnumSource
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { corrupted mandatory fields, JVM crash }`(
        missingType: ValueMissingType,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeThrowable = forge.aThrowable()
        val event = mutableMapOf<String, Any?>(
            "type" to "jvm_crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "throwable" to fakeThrowable
        )

        when (missingType) {
            ValueMissingType.MISSING -> event.remove(
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            )

            ValueMissingType.NULL -> event[
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            ] = null

            ValueMissingType.WRONG_TYPE -> event[
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            ] = Any()
        }

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                LogsFeature.JVM_CRASH_EVENT_MISSING_MANDATORY_FIELDS_WARNING
            )
        }

        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { JVM crash }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeThrowable = forge.aThrowable()
        val event = mapOf(
            "type" to "jvm_crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "throwable" to fakeThrowable
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasError(
                    LogEvent.Error(
                        kind = fakeThrowable.javaClass.canonicalName,
                        stack = fakeThrowable.stackTraceToString(),
                        message = fakeThrowable.message
                    )
                )
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId,
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event and wait 𝕎 onReceive() { JVM crash }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                Thread.sleep(300)
                callback.invoke(fakeDatadogContext, mockEventBatchWriter)
            }
        }
        testedFeature.dataWriter = mockDataWriter
        val fakeThrowable = forge.aThrowable()
        val event = mapOf(
            "type" to "jvm_crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "throwable" to fakeThrowable
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(fakeThreadName)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasExactlyAttributes(
                    mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId,
                        LogAttributes.DD_TRACE_ID to fakeTraceId,
                        LogAttributes.DD_SPAN_ID to fakeSpanId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 not wait forever for crash log write 𝕎 onReceive() { JVM crash, timeout }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                Thread.sleep(LogsFeature.MAX_WRITE_WAIT_TIMEOUT_MS + 200)
                callback.invoke(fakeDatadogContext, mockEventBatchWriter)
            }
        }
        testedFeature.dataWriter = mockDataWriter
        val fakeThrowable = forge.aThrowable()
        val event = mapOf(
            "type" to "jvm_crash",
            "threadName" to fakeThreadName,
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "throwable" to fakeThrowable
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockLogsFeatureScope).withWriteContext(any(), any())
        verifyNoInteractions(mockDataWriter)
    }

    // endregion

    // region FeatureEventReceiver#onReceive + ndk crash event

    @ParameterizedTest
    @EnumSource(ValueMissingType::class)
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { corrupted mandatory fields, NDK crash }`(
        missingType: ValueMissingType,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mutableMapOf<String, Any?>(
            "type" to "ndk_crash",
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes
        )

        when (missingType) {
            ValueMissingType.MISSING -> event.remove(
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            )

            ValueMissingType.NULL -> event[
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            ] = null

            ValueMissingType.WRONG_TYPE -> event[
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            ] = Any()
        }

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                LogsFeature.NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS_WARNING
            )
        }

        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { NDK crash }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mutableMapOf<String, Any?>(
            "type" to "ndk_crash",
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(Thread.currentThread().name)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { NDK crash, explicit network info }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        @Forgery fakeNetworkInfo: NetworkInfo,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mutableMapOf<String, Any?>(
            "type" to "ndk_crash",
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes,
            "networkInfo" to fakeNetworkInfo
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(Thread.currentThread().name)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    @Test
    fun `𝕄 write crash log event 𝕎 onReceive() { NDK crash, explicit user info }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mutableMapOf<String, Any?>(
            "type" to "ndk_crash",
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes,
            "userInfo" to fakeUserInfo
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.EMERGENCY)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(Thread.currentThread().name)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasUserInfo(fakeUserInfo)
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasExactlyAttributes(fakeAttributes)
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    // endregion

    // region FeatureEventReceiver#onReceive + span log event

    @ParameterizedTest
    @EnumSource(ValueMissingType::class)
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { corrupted mandatory fields, span log }`(
        missingType: ValueMissingType,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mutableMapOf<String, Any?>(
            "type" to "span_log",
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes
        )

        when (missingType) {
            ValueMissingType.MISSING -> event.remove(
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            )

            ValueMissingType.NULL -> event[
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            ] = null

            ValueMissingType.WRONG_TYPE -> event[
                forge.anElementFrom(event.keys.filterNot { it == "type" })
            ] = Any()
        }

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(
                LogsFeature.SPAN_LOG_EVENT_MISSING_MANDATORY_FIELDS_WARNING
            )
        }
        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockDataWriter)
    }

    @Test
    fun `𝕄 write span log event 𝕎 onReceive() { span log }`(
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeMessage: String,
        @StringForgery fakeLoggerName: String,
        forge: Forge
    ) {
        // Given
        testedFeature.dataWriter = mockDataWriter
        val fakeAttributes = forge.exhaustiveAttributes()
        val event = mutableMapOf<String, Any?>(
            "type" to "span_log",
            "timestamp" to fakeTimestamp,
            "message" to fakeMessage,
            "loggerName" to fakeLoggerName,
            "attributes" to fakeAttributes
        )

        // When
        testedFeature.onReceive(event)

        // Then
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture())

            val log = lastValue

            assertThatLog(log)
                .hasStatus(LogEvent.Status.TRACE)
                .hasLoggerName(fakeLoggerName)
                .hasServiceName(fakeDatadogContext.service)
                .hasMessage(fakeMessage)
                .hasThreadName(Thread.currentThread().name)
                .hasDate((fakeTimestamp + fakeServerTimeOffset).toIsoFormattedTimestamp())
                .hasNetworkInfo(fakeDatadogContext.networkInfo)
                .hasUserInfo(fakeDatadogContext.userInfo)
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasExactlyAttributes(
                    fakeAttributes + mapOf(
                        LogAttributes.RUM_APPLICATION_ID to fakeRumApplicationId,
                        LogAttributes.RUM_SESSION_ID to fakeRumSessionId,
                        LogAttributes.RUM_VIEW_ID to fakeRumViewId,
                        LogAttributes.RUM_ACTION_ID to fakeRumActionId
                    )
                )
                .hasExactlyTags(
                    setOf(
                        "${LogAttributes.ENV}:${fakeDatadogContext.env}",
                        "${LogAttributes.APPLICATION_VERSION}:${fakeDatadogContext.version}",
                        "${LogAttributes.VARIANT}:${fakeDatadogContext.variant}"
                    )
                )
        }
    }

    // endregion

    enum class ValueMissingType {
        MISSING,
        NULL,
        WRONG_TYPE
    }

    inline fun <reified T, R : Any> R?.getFieldValue(
        fieldName: String,
        enclosingClass: Class<R>? = this?.javaClass
    ): T? {
        if (this == null || enclosingClass == null) return null
        val field = enclosingClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(this) as T
    }
}
