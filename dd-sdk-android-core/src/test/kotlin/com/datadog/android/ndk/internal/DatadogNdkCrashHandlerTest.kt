/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.log.LogAttributes
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
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
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.lang.NullPointerException
import java.util.Locale
import java.util.Random
import java.util.UUID
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogNdkCrashHandlerTest {

    private lateinit var testedHandler: DatadogNdkCrashHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockNdkCrashLogDeserializer: Deserializer<String, NdkCrashLog>

    @Mock
    lateinit var mockNetworkInfoDeserializer: Deserializer<String, NetworkInfo>

    @Mock
    lateinit var mockUserInfoDeserializer: Deserializer<String, UserInfo>

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockEnvFileReader: FileReader<ByteArray>

    @Mock
    lateinit var mockLastRumViewEventProvider: () -> JsonObject?

    lateinit var fakeNdkCacheDir: File

    @Captor
    lateinit var captureRunnable: ArgumentCaptor<Runnable>

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`() {
        fakeNdkCacheDir = File(tempDir, DatadogNdkCrashHandler.NDK_CRASH_REPORTS_FOLDER_NAME)
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
            mockNetworkInfoDeserializer,
            mockUserInfoDeserializer,
            mockInternalLogger,
            mockEnvFileReader,
            mockLastRumViewEventProvider
        )
    }

    // region prepareData

    @Test
    fun `M read crash data W prepareData()`(
        @StringForgery crashDataStr: String,
        @Forgery fakeCrashData: NdkCrashLog
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.CRASH_DATA_FILE_NAME).writeText(crashDataStr)
        whenever(mockNdkCrashLogDeserializer.deserialize(crashDataStr)) doReturn fakeCrashData

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastNdkCrashLog).isNull()
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastNdkCrashLog)
            .isEqualTo(fakeCrashData)
    }

    @Test
    fun `M read last RUM View event W prepareData()`(
        forge: Forge
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        val fakeViewEvent = forge.aFakeViewEvent()
        whenever(mockLastRumViewEventProvider()) doReturn fakeViewEvent.toJson()

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastRumViewEvent).isNull()
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastRumViewEvent)
            .isEqualTo(fakeViewEvent.toJson())
    }

    @Test
    fun `M read network info W prepareData()`(
        @StringForgery networkInfoStr: String,
        @Forgery fakeNetworkInfo: NetworkInfo
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.NETWORK_INFO_FILE_NAME)
            .writeText(networkInfoStr)
        whenever(mockNetworkInfoDeserializer.deserialize(networkInfoStr)) doReturn fakeNetworkInfo

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastNetworkInfo).isNull()
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastNetworkInfo)
            .isEqualTo(fakeNetworkInfo)
    }

    @Test
    fun `M read user info W prepareData()`(
        @StringForgery userInfoStr: String,
        @Forgery fakeUserInfo: UserInfo
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.USER_INFO_FILE_NAME).writeText(userInfoStr)
        whenever(mockUserInfoDeserializer.deserialize(userInfoStr)) doReturn fakeUserInfo

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastUserInfo).isNull()
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastUserInfo)
            .isEqualTo(fakeUserInfo)
    }

    @Test
    fun `M do nothing W prepareData() {directory does not exist}`() {
        // When
        testedHandler.prepareData()
        whenever(mockNdkCrashLogDeserializer.deserialize(any())) doReturn mock()
        whenever(mockUserInfoDeserializer.deserialize(any())) doReturn mock()
        whenever(mockNetworkInfoDeserializer.deserialize(any())) doReturn mock()

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastRumViewEvent).isNull()
        assertThat(testedHandler.lastNdkCrashLog).isNull()
        assertThat(testedHandler.lastUserInfo).isNull()
        assertThat(testedHandler.lastNetworkInfo).isNull()
    }

    @Test
    fun `M clear crash data W prepareData()`(
        @StringForgery crashData: String,
        @StringForgery networkInfo: String,
        @StringForgery userInfo: String
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()

        File(fakeNdkCacheDir, DatadogNdkCrashHandler.CRASH_DATA_FILE_NAME).writeText(crashData)
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.NETWORK_INFO_FILE_NAME)
            .writeText(networkInfo)
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.USER_INFO_FILE_NAME).writeText(userInfo)

        // When
        testedHandler.prepareData()

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(fakeNdkCacheDir).isEmptyDirectory
    }

    // endregion

    // region handleNdkCrash

    @EnumSource(NdkCrashHandler.ReportTarget::class)
    @ParameterizedTest
    fun `M do nothing W handleNdkCrash() {no crash data}`(
        reportTarget: NdkCrashHandler.ReportTarget
    ) {
        // When
        testedHandler.handleNdkCrash(mockSdkCore, reportTarget)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        verifyNoInteractions(mockSdkCore)
    }

    @Test
    fun `M not send log W handleNdkCrash() {logs feature is not registered}`(
        @Forgery ndkCrashLog: NdkCrashLog
    ) {
        // Given
        testedHandler.lastNdkCrashLog = ndkCrashLog
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn null

        // When
        testedHandler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.LOGS)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()

        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogNdkCrashHandler.INFO_LOGS_FEATURE_NOT_REGISTERED
        )

        verifyNoMoreInteractions(mockInternalLogger)
        verifyNoInteractions(mockLogsFeatureScope)
    }

    @Test
    fun `M send log W handleNdkCrash() {missing RUM last view, no user and network info}`(
        @Forgery ndkCrashLog: NdkCrashLog
    ) {
        // Given
        testedHandler.lastNdkCrashLog = ndkCrashLog
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, null)

        // When
        testedHandler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.LOGS)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M send log W handleNdkCrash() {missing RUM last view, with user and network info}`(
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery networkInfo: NetworkInfo,
        @Forgery userInfo: UserInfo
    ) {
        // Given
        testedHandler.lastNdkCrashLog = ndkCrashLog
        testedHandler.lastNetworkInfo = networkInfo
        testedHandler.lastUserInfo = userInfo
        val expectedLogEvent = createLogEvent(ndkCrashLog, networkInfo, userInfo, null)

        // When
        testedHandler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.LOGS)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M send log + RUM view+error W handleNdkCrash() {with RUM last view}`(
        @Forgery ndkCrashLog: NdkCrashLog,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = forge.aFakeViewEvent()
        testedHandler.lastNdkCrashLog = ndkCrashLog
        testedHandler.lastRumViewEvent = fakeViewEvent.toJson()
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, fakeViewEvent)

        // When
        testedHandler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.LOGS)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
    }

    @Test
    fun `M send log + RUM view+error W handleNdkCrash() {with RUM last view, override native source type}`(
        @Forgery ndkCrashLog: NdkCrashLog,
        forge: Forge
    ) {
        // Given
        val handler = DatadogNdkCrashHandler(
            tempDir,
            mockExecutorService,
            mockNdkCrashLogDeserializer,
            mockNetworkInfoDeserializer,
            mockUserInfoDeserializer,
            mockInternalLogger,
            mockEnvFileReader,
            lastRumViewEventProvider = { JsonObject() },
            nativeCrashSourceType = "ndk+il2cpp"
        )

        val fakeViewEvent = forge.aFakeViewEvent()
        handler.lastNdkCrashLog = ndkCrashLog
        handler.lastRumViewEvent = fakeViewEvent.toJson()
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, fakeViewEvent, "ndk+il2cpp")

        // When
        handler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.LOGS)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
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
    fun `M send log + RUM view+error W handleNdkCrash() {with corrupted RUM last view}`(
        corruptionType: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        forge: Forge
    ) {
        // Given
        val fakeViewJson = forge.aFakeViewEvent().toJson()
        testedHandler.lastNdkCrashLog = ndkCrashLog
        testedHandler.lastRumViewEvent = fakeViewJson

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
        val expectedExceptionClass = when (corruptionType) {
            "missing_id_property",
            "missing_property" -> NullPointerException::class.java

            "wrong_id_type",
            "wrong_type" -> ClassCastException::class.java

            else -> Exception::class.java
        }

        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, null)

        // When
        testedHandler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.LOGS)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            DatadogNdkCrashHandler.WARN_CANNOT_READ_VIEW_INFO_DATA,
            expectedExceptionClass
        )
    }

    @Test
    fun `M not send RUM event W handleNdkCrash() { RUM feature is not registered }`(
        @Forgery ndkCrashLog: NdkCrashLog,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = forge.aFakeViewEvent()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        testedHandler.lastNdkCrashLog = ndkCrashLog
        testedHandler.lastRumViewEvent = fakeViewEvent.toJson()

        // When
        testedHandler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.RUM)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogNdkCrashHandler.INFO_RUM_FEATURE_NOT_REGISTERED
        )
    }

    @Test
    fun `M not send RUM event W handleNdkCrash() { missing last RUM view event }`(
        @Forgery ndkCrashLog: NdkCrashLog
    ) {
        // Given
        testedHandler.lastNdkCrashLog = ndkCrashLog

        // When
        testedHandler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.RUM)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verifyNoInteractions(mockInternalLogger, mockRumFeatureScope)
    }

    @Test
    fun `M send RUM event W handleNdkCrash()`(
        @Forgery ndkCrashLog: NdkCrashLog,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = forge.aFakeViewEvent()
        testedHandler.lastNdkCrashLog = ndkCrashLog
        testedHandler.lastRumViewEvent = fakeViewEvent.toJson()

        // When
        testedHandler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.RUM)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "ndk_crash",
                "sourceType" to "ndk",
                "timestamp" to ndkCrashLog.timestamp,
                "timeSinceAppStartMs" to ndkCrashLog.timeSinceAppStartMs,
                "signalName" to ndkCrashLog.signalName,
                "stacktrace" to ndkCrashLog.stacktrace,
                "message" to DatadogNdkCrashHandler.LOG_CRASH_MSG
                    .format(Locale.US, ndkCrashLog.signalName),
                "lastViewEvent" to fakeViewEvent.toJson()
            )
        )
    }

    @Test
    fun `M send RUM event W handleNdkCrash() { override native source type } `(
        @Forgery ndkCrashLog: NdkCrashLog,
        forge: Forge
    ) {
        // Given
        val handler = DatadogNdkCrashHandler(
            tempDir,
            mockExecutorService,
            mockNdkCrashLogDeserializer,
            mockNetworkInfoDeserializer,
            mockUserInfoDeserializer,
            mockInternalLogger,
            mockEnvFileReader,
            lastRumViewEventProvider = { JsonObject() },
            nativeCrashSourceType = "ndk+il2cpp"
        )

        val fakeViewEvent = forge.aFakeViewEvent()
        handler.lastNdkCrashLog = ndkCrashLog
        handler.lastRumViewEvent = fakeViewEvent.toJson()

        // When
        handler.handleNdkCrash(mockSdkCore, NdkCrashHandler.ReportTarget.RUM)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "ndk_crash",
                "sourceType" to "ndk+il2cpp",
                "timestamp" to ndkCrashLog.timestamp,
                "timeSinceAppStartMs" to ndkCrashLog.timeSinceAppStartMs,
                "signalName" to ndkCrashLog.signalName,
                "stacktrace" to ndkCrashLog.stacktrace,
                "message" to DatadogNdkCrashHandler.LOG_CRASH_MSG
                    .format(Locale.US, ndkCrashLog.signalName),
                "lastViewEvent" to fakeViewEvent.toJson()
            )
        )
    }

    @Test
    fun `M clear the references W handleNdkCrash() { both Logs and RUM are handled }`(
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery fakeUserInfo: UserInfo,
        @Forgery fakeNetworkInfo: NetworkInfo,
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = forge.aFakeViewEvent()
        testedHandler.lastNdkCrashLog = ndkCrashLog
        testedHandler.lastRumViewEvent = fakeViewEvent.toJson()
        testedHandler.lastUserInfo = fakeUserInfo
        testedHandler.lastNetworkInfo = fakeNetworkInfo

        // When
        listOf(
            NdkCrashHandler.ReportTarget.RUM,
            NdkCrashHandler.ReportTarget.LOGS
        )
            .shuffled(Random(forge.seed))
            .forEach {
                testedHandler.handleNdkCrash(mockSdkCore, it)
            }

        // Then
        verify(mockExecutorService, times(2)).submit(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.allValues.forEach { it.run() }

        assertThat(testedHandler.lastNdkCrashLog).isNull()
        assertThat(testedHandler.lastRumViewEvent).isNull()
        assertThat(testedHandler.lastUserInfo).isNull()
        assertThat(testedHandler.lastNetworkInfo).isNull()

        assertThat(testedHandler.processedForRum).isTrue
        assertThat(testedHandler.processedForLogs).isTrue
    }

    // endregion

    // region Internal

    private fun createLogEvent(
        ndkCrashLog: NdkCrashLog,
        networkInfo: NetworkInfo?,
        userInfo: UserInfo?,
        rumViewEvent: ViewEvent? = null,
        errorSourceType: String = "ndk"
    ): Map<String, Any?> {
        val attributes = if (rumViewEvent == null) {
            mapOf(
                LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace,
                LogAttributes.ERROR_SOURCE_TYPE to errorSourceType
            )
        } else {
            mapOf(
                LogAttributes.RUM_VIEW_ID to rumViewEvent.viewId,
                LogAttributes.RUM_SESSION_ID to rumViewEvent.sessionId,
                LogAttributes.RUM_APPLICATION_ID to rumViewEvent.applicationId,
                LogAttributes.ERROR_STACK to ndkCrashLog.stacktrace,
                LogAttributes.ERROR_SOURCE_TYPE to errorSourceType
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

    // TODO RUMM-0000 We don't have an access to RUM models from core. So we fake the class.
    // Ideally it would be nice to have the real model (maybe generate the one for the test
    // runtime classpath only somehow)
    private class ViewEvent(val applicationId: String, val sessionId: String, val viewId: String) {
        fun toJson(): JsonObject {
            return JsonObject().apply {
                add(
                    "application",
                    JsonObject().apply { this.addProperty("id", applicationId) }
                )
                add(
                    "session",
                    JsonObject().apply { this.addProperty("id", sessionId) }
                )
                add(
                    "view",
                    JsonObject().apply { this.addProperty("id", viewId) }
                )
            }
        }
    }

    private fun Forge.aFakeViewEvent(): ViewEvent = ViewEvent(
        applicationId = getForgery<UUID>().toString(),
        sessionId = getForgery<UUID>().toString(),
        viewId = getForgery<UUID>().toString()
    )

    // endregion
}
