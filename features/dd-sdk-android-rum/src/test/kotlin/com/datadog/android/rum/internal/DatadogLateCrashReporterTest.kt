/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.ApplicationExitInfo
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.ErrorEventAssert
import com.datadog.android.rum.assertj.ViewEventAssert
import com.datadog.android.rum.internal.anr.ANRDetectorRunnable
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.anr.AndroidTraceParser
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogLateCrashReporterTest {

    private lateinit var testedHandler: LateCrashReporter

    @Mock
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumWriter: DataWriter<Any>

    @Mock
    lateinit var mockRumEventDeserializer: Deserializer<JsonObject, Any>

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockAndroidTraceParser: AndroidTraceParser

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(it.arguments.lastIndex)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }

        testedHandler = DatadogLateCrashReporter(
            sdkCore = mockSdkCore,
            rumEventDeserializer = mockRumEventDeserializer,
            androidTraceParser = mockAndroidTraceParser
        )
    }

    // region handleNdkCrashEvent

    @Test
    fun `M send RUM view+error W handleNdkCrashEvent()`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.NDK)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(fakeSignalName)
                .hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                .hasTimeSinceAppStart(fakeTimeSinceAppStartMs)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
    }

    @Test
    fun `M send RUM view+error W handleNdkCrashEvent() {source_type set}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        val fakeEvent = mapOf(
            "sourceType" to "ndk+il2cpp",
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.NDK_IL2CPP)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(fakeSignalName)
                .hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                .hasTimeSinceAppStart(fakeTimeSinceAppStartMs)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
    }

    @Test
    fun `M send RUM view+error W handleNdkCrashEvent() {invalid source_type set}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        val fakeEvent = mapOf(
            "sourceType" to "invalid",
            "timestamp" to fakeTimestamp,
            "fakeTimeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasErrorSourceType(ErrorEvent.SourceType.NDK)
        }
    }

    @Test
    fun `M send RUM view+error W handleNdkCrashEvent() {view without usr}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = null
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.NDK)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasNoUserInfo()
                .hasErrorType(fakeSignalName)
                .hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                .hasTimeSinceAppStart(fakeTimeSinceAppStartMs)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
    }

    @Test
    fun `M send only RUM error W handleNdkCrashEvent() {view is too old}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 1
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)
        val expectedErrorEventSource = with(fakeViewEvent.source) {
            if (this != null) {
                ErrorEvent.ErrorEventSource.fromJson(this.toJson().asString)
            } else {
                null
            }
        }
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(1)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.NDK)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(fakeSignalName)
                .hasErrorCategory(ErrorEvent.Category.EXCEPTION)
                .hasTimeSinceAppStart(fakeTimeSinceAppStartMs)
                .hasSource(expectedErrorEventSource)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
        }
    }

    @Test
    fun `M not send RUM event W handleNdkCrashEvent() { RUM feature is not registered }`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val fakeViewEventJson = viewEvent.toJson().asJsonObject
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogLateCrashReporter.INFO_RUM_FEATURE_NOT_REGISTERED
        )
    }

    @Test
    fun `M not send RUM event W handleNdkCrashEvent() { corrupted event, view json deserialization fails }`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @LongForgery(min = 1) fakeTimeSinceAppStartMs: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery fakeViewEventJson: JsonObject
    ) {
        // Given
        val fakeEvent = mutableMapOf(
            "timestamp" to fakeTimestamp,
            "timeSinceAppStartMs" to fakeTimeSinceAppStartMs,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn null

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogLateCrashReporter.NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS
        )
    }

    @ParameterizedTest
    @EnumSource(ValueMissingType::class)
    fun `M not send RUM event W handleNdkCrashEvent() { corrupted event }`(
        missingType: ValueMissingType,
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEventJson = viewEvent.toJson().asJsonObject
        val fakeEvent = mutableMapOf(
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        when (missingType) {
            ValueMissingType.MISSING -> fakeEvent.remove(forge.anElementFrom(fakeEvent.keys))
            ValueMissingType.NULL -> fakeEvent[forge.anElementFrom(fakeEvent.keys)] = null
            ValueMissingType.WRONG_TYPE -> fakeEvent[forge.anElementFrom(fakeEvent.keys)] = Any()
        }

        // When
        testedHandler.handleNdkCrashEvent(fakeEvent, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogLateCrashReporter.NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS
        )
    }

    //endregion

    // region handleAnrCrash

    @Test
    fun `M send RUM view+error W handleAnrCrash()`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeThreadsDump = forge.anrCrashThreadDump()
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verify(mockRumFeatureScope).withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(ANRDetectorRunnable.ANR_MESSAGE)
                .hasStackTrace(fakeThreadsDump.first { it.name == "main" }.stack)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(ANRException::class.java.canonicalName)
                .hasErrorCategory(ErrorEvent.Category.ANR)
                .hasTimeSinceAppStart(null)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                .hasThreads(fakeThreadsDump)

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }

        verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)
    }

    @Test
    fun `M send RUM view+error W handleAnrCrash() { view without user }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = null
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeThreadsDump = forge.anrCrashThreadDump()
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verify(mockRumFeatureScope).withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(ANRDetectorRunnable.ANR_MESSAGE)
                .hasStackTrace(fakeThreadsDump.first { it.name == "main" }.stack)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasNoUserInfo()
                .hasErrorType(ANRException::class.java.canonicalName)
                .hasErrorCategory(ErrorEvent.Category.ANR)
                .hasTimeSinceAppStart(null)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                .hasThreads(fakeThreadsDump)

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }

        verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)
    }

    @Test
    fun `M send only RUM error W handleAnrCrash() { view is too old }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 1
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                anonymousId = fakeUserInfo.anonymousId,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeThreadsDump = forge.anrCrashThreadDump()
        whenever(mockAndroidTraceParser.parse(any())) doReturn fakeThreadsDump
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verify(mockRumFeatureScope).withWriteContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        argumentCaptor<Any> {
            verify(mockRumWriter, times(1)).write(eq(mockEventBatchWriter), capture(), eq(EventType.CRASH))

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasErrorId()
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasBuildId(fakeDatadogContext.appBuildId)
                .hasMessage(ANRDetectorRunnable.ANR_MESSAGE)
                .hasStackTrace(fakeThreadsDump.first { it.name == "main" }.stack)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.anonymousId,
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(ANRException::class.java.canonicalName)
                .hasErrorCategory(ErrorEvent.Category.ANR)
                .hasTimeSinceAppStart(null)
                .hasDeviceInfo(
                    fakeDatadogContext.deviceInfo.deviceName,
                    fakeDatadogContext.deviceInfo.deviceModel,
                    fakeDatadogContext.deviceInfo.deviceBrand,
                    fakeDatadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    fakeDatadogContext.deviceInfo.architecture
                )
                .hasOsInfo(
                    fakeDatadogContext.deviceInfo.osName,
                    fakeDatadogContext.deviceInfo.osVersion,
                    fakeDatadogContext.deviceInfo.osMajorVersion
                )
                .hasThreads(fakeThreadsDump)
        }

        verify(mockSdkCore).writeLastFatalAnrSent(fakeTimestamp)
    }

    @Test
    fun `M log warning and not send anything W handleAnrCrash() { RUM feature not registered }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogLateCrashReporter.INFO_RUM_FEATURE_NOT_REGISTERED
        )
    }

    @Test
    fun `M not send anything W handleAnrCrash() { view deserialization fails }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val fakeViewEventJson = viewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn null

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockAndroidTraceParser)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { Crash timestamp is before last RUM view }`(
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeViewEvent.date - 1
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockInternalLogger)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { last view event belongs to the current session }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            ),
            featuresContext = mapOf(
                Feature.RUM_FEATURE_NAME to mapOf(RumContext.SESSION_ID to viewEvent.session.id)
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { ANR was already sent }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }
        whenever(mockSdkCore.lastFatalAnrSent) doReturn fakeTimestamp

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { empty threads dump }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn mock()
            whenever(timestamp) doReturn fakeTimestamp
        }
        whenever(mockAndroidTraceParser.parse(any())) doReturn emptyList()

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)
    }

    @Test
    fun `M not send anything W handleAnrCrash() { cannot open trace information, IOException }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val fakeException = IOException()
        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doThrow fakeException
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)

        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                level = eq(InternalLogger.Level.ERROR),
                target = eq(InternalLogger.Target.USER),
                messageBuilder = capture(),
                throwable = eq(fakeException),
                onlyOnce = eq(false),
                additionalProperties = isNull()
            )
            assertThat(firstValue()).isEqualTo(DatadogLateCrashReporter.OPEN_ANR_TRACE_ERROR)
        }
    }

    @Test
    fun `M not send anything W handleAnrCrash() { cannot open trace information, null }`(
        @LongForgery(min = 1) fakeTimestamp: Long,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -fakeTimestamp, max = Long.MAX_VALUE - fakeTimestamp)
        fakeDatadogContext = fakeDatadogContext.copy(
            time = fakeDatadogContext.time.copy(
                serverTimeOffsetMs = fakeServerOffset
            )
        )

        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogLateCrashReporter.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn fakeViewEvent

        val mockAnrExitInfo = mock<ApplicationExitInfo>().apply {
            whenever(traceInputStream) doReturn null
            whenever(timestamp) doReturn fakeTimestamp
        }

        // When
        testedHandler.handleAnrCrash(mockAnrExitInfo, fakeViewEventJson, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter)

        argumentCaptor<() -> String> {
            verify(mockInternalLogger).log(
                level = eq(InternalLogger.Level.WARN),
                target = eq(InternalLogger.Target.USER),
                messageBuilder = capture(),
                throwable = eq(null),
                onlyOnce = eq(false),
                additionalProperties = isNull()
            )
            assertThat(firstValue()).isEqualTo(DatadogLateCrashReporter.MISSING_ANR_TRACE)
        }
    }

    // endregion

    private fun Forge.anrCrashThreadDump(): List<ThreadDump> {
        val otherThreads = aList { getForgery<ThreadDump>() }.map { it.copy(crashed = false) }
        val mainThread = getForgery<ThreadDump>().copy(name = "main", crashed = true)
        return shuffle(otherThreads + mainThread)
    }

    enum class ValueMissingType {
        MISSING,
        NULL,
        WRONG_TYPE
    }
}
