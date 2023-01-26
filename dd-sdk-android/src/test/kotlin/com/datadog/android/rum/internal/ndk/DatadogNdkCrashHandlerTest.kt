/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.log.LogAttributes
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.UserInfo
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.firstValue
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogNdkCrashHandlerTest {

    lateinit var testedHandler: DatadogNdkCrashHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockNdkCrashLogDeserializer: Deserializer<String, NdkCrashLog>

    @Mock
    lateinit var mockRumEventDeserializer: Deserializer<String, JsonObject>

    @Mock
    lateinit var mockNetworkInfoDeserializer: Deserializer<String, NetworkInfo>

    @Mock
    lateinit var mockUserInfoDeserializer: Deserializer<String, UserInfo>

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockRumFileReader: BatchFileReader

    @Mock
    lateinit var mockEnvFileReader: FileReader

    lateinit var fakeNdkCacheDir: File

    @Captor
    lateinit var captureRunnable: ArgumentCaptor<Runnable>

    @TempDir
    lateinit var tempDir: File

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @BeforeEach
    fun `set up`() {
        fakeNdkCacheDir = File(tempDir, DatadogNdkCrashHandler.NDK_CRASH_REPORTS_FOLDER_NAME)
        whenever(mockRumFileReader.readData(any())) doAnswer {
            listOf(
                it.getArgument<File>(0).readBytes()
            )
        }
        whenever(mockEnvFileReader.readData(any())) doAnswer {
            it.getArgument<File>(0).readBytes()
        }

        whenever(
            mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)
        ) doReturn mockLogsFeatureScope

        whenever(
            mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope

        testedHandler = DatadogNdkCrashHandler(
            tempDir,
            mockExecutorService,
            mockNdkCrashLogDeserializer,
            mockRumEventDeserializer,
            mockNetworkInfoDeserializer,
            mockUserInfoDeserializer,
            mockInternalLogger,
            mockRumFileReader,
            mockEnvFileReader
        )
    }

    @Test
    fun `𝕄 read crash data 𝕎 prepareData()`(
        @StringForgery crashData: String
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.CRASH_DATA_FILE_NAME).writeText(crashData)

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastSerializedNdkCrashLog).isNull()
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastSerializedNdkCrashLog)
            .isEqualTo(crashData)
    }

    @Test
    fun `𝕄 read last RUM View event 𝕎 prepareData()`(
        @StringForgery viewEvent: String
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.RUM_VIEW_EVENT_FILE_NAME).writeText(viewEvent)

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastSerializedRumViewEvent).isNull()
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastSerializedRumViewEvent)
            .isEqualTo(viewEvent)
    }

    @Test
    fun `𝕄 read network info 𝕎 prepareData()`(
        @StringForgery networkInfo: String
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.NETWORK_INFO_FILE_NAME).writeText(networkInfo)

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastSerializedNetworkInformation).isNull()
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastSerializedNetworkInformation)
            .isEqualTo(networkInfo)
    }

    @Test
    fun `𝕄 read user info 𝕎 prepareData()`(
        @StringForgery userInfo: String
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.USER_INFO_FILE_NAME).writeText(userInfo)

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastSerializedUserInformation).isNull()
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastSerializedUserInformation)
            .isEqualTo(userInfo)
    }

    @Test
    fun `𝕄 do nothing 𝕎 prepareData {directory does not exist}`() {
        // When
        testedHandler.prepareData()

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastSerializedRumViewEvent).isNull()
        assertThat(testedHandler.lastSerializedNdkCrashLog).isNull()
        assertThat(testedHandler.lastSerializedUserInformation).isNull()
        assertThat(testedHandler.lastSerializedNetworkInformation).isNull()
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleNdkCrash() {no crash data}`(
        @StringForgery viewEvent: String,
        @StringForgery networkInfo: String,
        @StringForgery userInfo: String
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.RUM_VIEW_EVENT_FILE_NAME).writeText(viewEvent)
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.NETWORK_INFO_FILE_NAME).writeText(networkInfo)
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.USER_INFO_FILE_NAME).writeText(userInfo)

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        verifyZeroInteractions(mockSdkCore)
    }

    @Test
    fun `𝕄 do nothing 𝕎 handleNdkCrash() {crash data can't be deserialized}`(
        @StringForgery crashData: String,
        @StringForgery viewEvent: String,
        @StringForgery networkInfo: String,
        @StringForgery userInfo: String
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.RUM_VIEW_EVENT_FILE_NAME).writeText(viewEvent)
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.NETWORK_INFO_FILE_NAME).writeText(networkInfo)
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.USER_INFO_FILE_NAME).writeText(userInfo)
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.CRASH_DATA_FILE_NAME).writeText(crashData)
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn null

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        verifyZeroInteractions(mockSdkCore, mockInternalLogger)
    }

    @Test
    fun `𝕄 not send log 𝕎 handleNdkCrash() {logs feature is not registered}`(
        @StringForgery crashData: String,
        @Forgery ndkCrashLog: NdkCrashLog
    ) {
        // Given
        testedHandler.lastSerializedNdkCrashLog = crashData
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn null

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()

        verify(mockInternalLogger).log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogNdkCrashHandler.INFO_LOGS_FEATURE_NOT_REGISTERED
        )

        verifyZeroInteractions(mockInternalLogger, mockLogsFeatureScope)
    }

    @Test
    fun `𝕄 send log 𝕎 handleNdkCrash() {missing RUM last view, no info}`(
        @StringForgery crashData: String,
        @Forgery ndkCrashLog: NdkCrashLog
    ) {
        // Given
        testedHandler.lastSerializedNdkCrashLog = crashData
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, null)

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verifyZeroInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 send log 𝕎 handleNdkCrash() {missing RUM last view, with info}`(
        @StringForgery crashData: String,
        @StringForgery networkInfoStr: String,
        @StringForgery userInfoStr: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery networkInfo: NetworkInfo,
        @Forgery userInfo: UserInfo
    ) {
        // Given
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedNetworkInformation = networkInfoStr
        testedHandler.lastSerializedUserInformation = userInfoStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        whenever(mockNetworkInfoDeserializer.deserialize(networkInfoStr)) doReturn networkInfo
        whenever(mockUserInfoDeserializer.deserialize(userInfoStr)) doReturn userInfo
        val expectedLogEvent = createLogEvent(ndkCrashLog, networkInfo, userInfo, null)

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verifyZeroInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 send log 𝕎 handleNdkCrash() {missing RUM last view, corrupted info}`(
        @StringForgery crashData: String,
        @StringForgery networkInfoStr: String,
        @StringForgery userInfoStr: String,
        @Forgery ndkCrashLog: NdkCrashLog
    ) {
        // Given
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedNetworkInformation = networkInfoStr
        testedHandler.lastSerializedUserInformation = userInfoStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        whenever(mockNetworkInfoDeserializer.deserialize(networkInfoStr)) doReturn null
        whenever(mockUserInfoDeserializer.deserialize(userInfoStr)) doReturn null
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, null)

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verifyZeroInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 send log + RUM view+error 𝕎 handleNdkCrash() {with RUM last view}`(
        @StringForgery crashData: String,
        @StringForgery viewEventStr: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedRumViewEvent = viewEventStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog

        whenever(mockRumEventDeserializer.deserialize(viewEventStr))
            .doReturn(fakeViewEvent.toJson().asJsonObject)
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, fakeViewEvent)

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "missing_property",
            "wrong_type",
            "missing_id_property",
            "wrong_id_type"
        ]
    )
    fun `𝕄 send log + RUM view+error 𝕎 handleNdkCrash() {with corrupted RUM last view}`(
        corruptionType: String,
        @StringForgery crashData: String,
        @StringForgery viewEventStr: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedRumViewEvent = viewEventStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        val fakeViewJson = viewEvent.toJson().asJsonObject

        val corruptedProperty = forge.anElementFrom("application", "session", "view")
        when (corruptionType) {
            "missing_property" -> fakeViewJson.remove(corruptedProperty)
            "wrong_type" -> fakeViewJson.add(
                corruptedProperty,
                forge.anElementFrom(JsonPrimitive(forge.anAlphabeticalString()), JsonArray())
            )
            "missing_id_property" -> fakeViewJson.get(corruptedProperty)
                .asJsonObject
                .remove("id")
            "wrong_id_type" -> fakeViewJson.get(corruptedProperty)
                .asJsonObject.add(
                    "id",
                    forge.anElementFrom(JsonArray(), JsonObject())
                )
        }

        whenever(mockRumEventDeserializer.deserialize(viewEventStr))
            .doReturn(fakeViewJson)

        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, null)

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verify(mockInternalLogger)
            .log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.MAINTAINER),
                eq(DatadogNdkCrashHandler.WARN_CANNOT_READ_VIEW_INFO_DATA),
                throwable = any()
            )
    }

    @Test
    fun `𝕄 not send RUM event 𝕎 handleNdkCrash() { RUM feature is not registered }`(
        @StringForgery crashData: String,
        @StringForgery viewEventStr: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery fakeViewEvent: ViewEvent
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedRumViewEvent = viewEventStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        whenever(mockRumEventDeserializer.deserialize(viewEventStr))
            .doReturn(fakeViewEvent.toJson().asJsonObject)

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockInternalLogger).log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogNdkCrashHandler.INFO_RUM_FEATURE_NOT_REGISTERED
        )
    }

    // region Internal

    private fun createLogEvent(
        ndkCrashLog: NdkCrashLog,
        networkInfo: NetworkInfo?,
        userInfo: UserInfo?,
        rumViewEvent: ViewEvent? = null
    ): Map<String, Any?> {
        val attributes = if (rumViewEvent == null) {
            mapOf(
                LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace
            )
        } else {
            mapOf(
                LogAttributes.RUM_VIEW_ID to rumViewEvent.view.id,
                LogAttributes.RUM_SESSION_ID to rumViewEvent.session.id,
                LogAttributes.RUM_APPLICATION_ID to rumViewEvent.application.id,
                LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace
            )
        }
        return mapOf(
            "loggerName" to DatadogNdkCrashHandler.LOGGER_NAME,
            "type" to "ndk_crash",
            "message" to DatadogNdkCrashHandler.LOG_CRASH_MSG.format(
                Locale.US,
                ndkCrashLog.signalName
            ),
            "attributes" to attributes,
            "timestamp" to ndkCrashLog.timestamp,
            "networkInfo" to networkInfo,
            "userInfo" to userInfo
        )
    }

    // endregion
}
