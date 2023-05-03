/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.ErrorEventAssert
import com.datadog.android.rum.assertj.ViewEventAssert
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.storage.DataWriter
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogNdkCrashEventHandlerTest {

    private lateinit var testedHandler: NdkCrashEventHandler

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumWriter: DataWriter<Any>

    @Mock
    lateinit var mockRumEventDeserializer: Deserializer<JsonObject, Any>

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }

        testedHandler = DatadogNdkCrashEventHandler(
            rumEventDeserializer = mockRumEventDeserializer,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `𝕄 send RUM view+error 𝕎 handleEvent()`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
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
                max = DatadogNdkCrashEventHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleEvent(fakeEvent, mockSdkCore, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture())

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(fakeSignalName)
                .hasLiteSessionPlan()
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
    fun `𝕄 send RUM view+error 𝕎 handleEvent() {view without usr}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
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
                max = DatadogNdkCrashEventHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = null
        )
        val fakeViewEventJson = fakeViewEvent.toJson().asJsonObject
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson))
            .doReturn(fakeViewEvent)

        // When
        testedHandler.handleEvent(fakeEvent, mockSdkCore, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockEventBatchWriter), capture())

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasNoUserInfo()
                .hasErrorType(fakeSignalName)
                .hasLiteSessionPlan()
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
    fun `𝕄 send only RUM error 𝕎 handleEvent() {view is too old}`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
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
                min = DatadogNdkCrashEventHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 1
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
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
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleEvent(fakeEvent, mockSdkCore, mockRumWriter)

        // Then
        argumentCaptor<Any> {
            verify(mockRumWriter, times(1)).write(eq(mockEventBatchWriter), capture())

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasMessage(crashMessage)
                .hasStackTrace(fakeStacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(fakeTimestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(fakeSignalName)
                .hasLiteSessionPlan()
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
    fun `𝕄 not send RUM event 𝕎 handleEvent() { RUM feature is not registered }`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val fakeViewEventJson = viewEvent.toJson().asJsonObject
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        val fakeEvent = mapOf(
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )

        // When
        testedHandler.handleEvent(fakeEvent, mockSdkCore, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
        verify(mockInternalLogger).log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogNdkCrashEventHandler.INFO_RUM_FEATURE_NOT_REGISTERED
        )
    }

    @Test
    fun `𝕄 not send RUM event 𝕎 handleEvent() { corrupted event, view json deserialization fails }`(
        @StringForgery crashMessage: String,
        @LongForgery(min = 1) fakeTimestamp: Long,
        @StringForgery fakeSignalName: String,
        @StringForgery fakeStacktrace: String,
        @Forgery fakeViewEventJson: JsonObject
    ) {
        // Given
        val fakeEvent = mutableMapOf(
            "timestamp" to fakeTimestamp,
            "signalName" to fakeSignalName,
            "stacktrace" to fakeStacktrace,
            "message" to crashMessage,
            "lastViewEvent" to fakeViewEventJson
        )
        whenever(mockRumEventDeserializer.deserialize(fakeViewEventJson)) doReturn null

        // When
        testedHandler.handleEvent(fakeEvent, mockSdkCore, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                DatadogNdkCrashEventHandler.NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS
            )
    }

    @ParameterizedTest
    @EnumSource(ValueMissingType::class)
    fun `𝕄 not send RUM event 𝕎 handleEvent() { corrupted event }`(
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
        testedHandler.handleEvent(fakeEvent, mockSdkCore, mockRumWriter)

        // Then
        verifyNoInteractions(mockRumWriter, mockEventBatchWriter)
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                DatadogNdkCrashEventHandler.NDK_CRASH_EVENT_MISSING_MANDATORY_FIELDS
            )
    }

    enum class ValueMissingType {
        MISSING,
        NULL,
        WRONG_TYPE
    }
}
