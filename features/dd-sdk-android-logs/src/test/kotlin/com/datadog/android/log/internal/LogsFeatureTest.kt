/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.event.EventMapper
import com.datadog.android.event.MapperSerializer
import com.datadog.android.internal.utils.NULL_MAP_VALUE
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.domain.event.LogEventMapperWrapper
import com.datadog.android.log.internal.net.LogsRequestFactory
import com.datadog.android.log.internal.storage.LogsDataWriter
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.extension.toIsoFormattedTimestamp
import com.datadog.android.utils.forge.Configurator
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
    lateinit var mockEventWriteScope: EventWriteScope

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

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockLogsFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
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
    fun `M initialize data writer W initialize()`() {
        // When
        testedFeature.onInitialize(mockApplicationContext)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(LogsDataWriter::class.java)
    }

    @Test
    fun `M use the eventMapper W initialize()`() {
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
    fun `M initialize packageName W initialize()`() {
        // When
        testedFeature.onInitialize(mockApplicationContext)

        // Then
        assertThat(testedFeature.packageName).isEqualTo(fakePackageName)
    }

    @Test
    fun `M provide logs feature name W name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(Feature.LOGS_FEATURE_NAME)
    }

    @Test
    fun `M provide logs request factory W requestFactory()`() {
        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(LogsRequestFactory::class.java)
    }

    @Test
    fun `M provide default storage configuration W storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }

    @Test
    fun `M add attributes W addAttribute`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // When
        testedFeature.addAttribute(key, value)

        // Then
        val attributes = testedFeature.getAttributes()
        assertThat(attributes).containsEntry(key, value)
    }

    @Test
    fun `M remove attributes W removeAttribute`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        testedFeature.addAttribute(key, value)

        // When
        testedFeature.removeAttribute(key)

        // Then
        val attributes = testedFeature.getAttributes()
        assertThat(attributes).isEmpty()
    }

    @Test
    fun `M provide attribute snapshot W getAttributes`(
        @StringForgery key: String,
        @StringForgery value: String,
        @StringForgery secondValue: String
    ) {
        // Given
        testedFeature.addAttribute(key, value)
        val attributes = testedFeature.getAttributes()

        // When
        testedFeature.addAttribute(key, secondValue)

        // Then
        assertThat(attributes).containsEntry(key, value)
    }

    @Test
    fun `M add attributes replaces null W addAttribute { null value }`(
        @StringForgery key: String
    ) {
        testedFeature.addAttribute(key, null)

        // Then
        assertThat(testedFeature.getAttributes()).containsEntry(key, NULL_MAP_VALUE)
    }

    // region FeatureEventReceiver#onReceive + unknown

    @Test
    fun `M log warning and do nothing W onReceive() { unknown event type }`() {
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
    fun `M log warning and do nothing W onReceive() { unknown type property value }`(
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

    // region FeatureEventReceiver#onReceive + span log event

    @ParameterizedTest
    @EnumSource(ValueMissingType::class)
    fun `M log warning and do nothing W onReceive() { corrupted mandatory fields, span log }`(
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
    fun `M write span log event W onReceive() { span log }`(
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
        verify(mockLogsFeatureScope).withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        argumentCaptor<LogEvent> {
            verify(mockDataWriter).write(eq(mockEventBatchWriter), capture(), eq(EventType.DEFAULT))

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
                .hasBuildVersion(fakeDatadogContext.versionCode)
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
