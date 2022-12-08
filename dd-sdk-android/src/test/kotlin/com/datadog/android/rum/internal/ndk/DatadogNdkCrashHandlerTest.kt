/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import android.util.Log
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.core.internal.persistence.file.batch.BatchFileReader
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.ErrorEventAssert
import com.datadog.android.rum.assertj.ViewEventAssert
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.scope.toErrorSchemaType
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.UserInfo
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.firstValue
import com.nhaarman.mockitokotlin2.times
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogNdkCrashHandlerTest {

    lateinit var testedHandler: DatadogNdkCrashHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockNdkCrashLogDeserializer: Deserializer<NdkCrashLog>

    @Mock
    lateinit var mockRumEventDeserializer: Deserializer<Any>

    @Mock
    lateinit var mockNetworkInfoDeserializer: Deserializer<NetworkInfo>

    @Mock
    lateinit var mockUserInfoDeserializer: Deserializer<UserInfo>

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockLogsFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockRumWriter: DataWriter<Any>

    @Mock
    lateinit var mockRumEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockLogHandler: LogHandler

    @Mock
    lateinit var mockRumFileReader: BatchFileReader

    @Mock
    lateinit var mockEnvFileReader: FileReader

    lateinit var fakeNdkCacheDir: File

    @Forgery
    lateinit var fakeAndroidInfoProvider: AndroidInfoProvider

    @Captor
    lateinit var captureRunnable: ArgumentCaptor<Runnable>

    @Mock
    lateinit var mockTimeProvider: TimeProvider

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
            mockSdkCore.getFeature(LogsFeature.LOGS_FEATURE_NAME)
        ) doReturn mockLogsFeatureScope

        whenever(
            mockSdkCore.getFeature(RumFeature.RUM_FEATURE_NAME)
        ) doReturn mockRumFeatureScope

        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockRumEventBatchWriter)
        }

        testedHandler = DatadogNdkCrashHandler(
            tempDir,
            mockExecutorService,
            mockNdkCrashLogDeserializer,
            mockRumEventDeserializer,
            mockNetworkInfoDeserializer,
            mockUserInfoDeserializer,
            internalLogger = Logger(mockLogHandler),
            mockTimeProvider,
            mockRumFileReader,
            mockEnvFileReader,
            fakeAndroidInfoProvider
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
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        verifyZeroInteractions(mockSdkCore, mockRumWriter)
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
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        verifyZeroInteractions(mockSdkCore, mockRumWriter, mockLogHandler)
    }

    @Test
    fun `𝕄 not send log 𝕎 handleNdkCrash() {logs feature is not registered}`(
        @StringForgery crashData: String,
        @Forgery ndkCrashLog: NdkCrashLog
    ) {
        // Given
        testedHandler.lastSerializedNdkCrashLog = crashData
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        whenever(mockSdkCore.getFeature(LogsFeature.LOGS_FEATURE_NAME)) doReturn null

        // When
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()

        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.INFO,
                DatadogNdkCrashHandler.INFO_LOGS_FEATURE_NOT_REGISTERED
            )

        verifyZeroInteractions(mockRumWriter, mockLogHandler, mockLogsFeatureScope)
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
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verifyZeroInteractions(mockRumWriter, mockLogHandler)
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
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verifyZeroInteractions(mockRumWriter, mockLogHandler)
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
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
        verifyZeroInteractions(mockRumWriter, mockLogHandler)
    }

    @Test
    fun `𝕄 send log + RUM view+error 𝕎 handleNdkCrash() {with RUM last view}`(
        @StringForgery crashData: String,
        @StringForgery viewEventStr: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -ndkCrashLog.timestamp, max = Long.MAX_VALUE - ndkCrashLog.timestamp)
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(fakeServerOffset)
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedRumViewEvent = viewEventStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogNdkCrashHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )

        whenever(mockRumEventDeserializer.deserialize(viewEventStr))
            .doReturn(fakeViewEvent)
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, fakeViewEvent)

        // When
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockRumEventBatchWriter), capture())

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasMessage(
                    DatadogNdkCrashHandler.LOG_CRASH_MSG.format(
                        Locale.US,
                        ndkCrashLog.signalName
                    )
                )
                .hasStackTrace(ndkCrashLog.stacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(ndkCrashLog.timestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(ndkCrashLog.signalName)
                .hasLiteSessionPlan()
                .hasDeviceInfo(
                    fakeAndroidInfoProvider.deviceName,
                    fakeAndroidInfoProvider.deviceModel,
                    fakeAndroidInfoProvider.deviceBrand,
                    fakeAndroidInfoProvider.deviceType.toErrorSchemaType(),
                    fakeAndroidInfoProvider.architecture
                )
                .hasOsInfo(
                    fakeAndroidInfoProvider.osName,
                    fakeAndroidInfoProvider.osVersion,
                    fakeAndroidInfoProvider.osMajorVersion
                )

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
    }

    @Test
    fun `𝕄 send log + RUM error 𝕎 handleNdkCrash() {with RUM last view, view without usr}`(
        @StringForgery crashData: String,
        @StringForgery viewEventStr: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -ndkCrashLog.timestamp, max = Long.MAX_VALUE - ndkCrashLog.timestamp)
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(fakeServerOffset)
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedRumViewEvent = viewEventStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogNdkCrashHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            ),
            usr = null
        )

        whenever(mockRumEventDeserializer.deserialize(viewEventStr))
            .doReturn(fakeViewEvent)
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, fakeViewEvent)

        // When
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        argumentCaptor<Any> {
            verify(mockRumWriter, times(2)).write(eq(mockRumEventBatchWriter), capture())

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasMessage(
                    DatadogNdkCrashHandler.LOG_CRASH_MSG.format(
                        Locale.US,
                        ndkCrashLog.signalName
                    )
                )
                .hasStackTrace(ndkCrashLog.stacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(ndkCrashLog.timestamp + fakeServerOffset)
                .hasNoUserInfo()
                .hasErrorType(ndkCrashLog.signalName)
                .hasLiteSessionPlan()
                .hasDeviceInfo(
                    fakeAndroidInfoProvider.deviceName,
                    fakeAndroidInfoProvider.deviceModel,
                    fakeAndroidInfoProvider.deviceBrand,
                    fakeAndroidInfoProvider.deviceType.toErrorSchemaType(),
                    fakeAndroidInfoProvider.architecture
                )
                .hasOsInfo(
                    fakeAndroidInfoProvider.osName,
                    fakeAndroidInfoProvider.osVersion,
                    fakeAndroidInfoProvider.osMajorVersion
                )

            ViewEventAssert.assertThat(secondValue as ViewEvent)
                .hasVersion(fakeViewEvent.dd.documentVersion + 1)
                .hasCrashCount((fakeViewEvent.view.crash?.count ?: 0) + 1)
                .isActive(false)
        }
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
    }

    @Test
    fun `𝕄 send log 𝕎 handleNdkCrash() {with RUM last view, view is too old}`(
        @StringForgery crashData: String,
        @StringForgery viewEventStr: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery viewEvent: ViewEvent,
        @Forgery fakeUserInfo: UserInfo,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -ndkCrashLog.timestamp, max = Long.MAX_VALUE - ndkCrashLog.timestamp)
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(fakeServerOffset)
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedRumViewEvent = viewEventStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = DatadogNdkCrashHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD + 1
            ),
            usr = ViewEvent.Usr(
                id = fakeUserInfo.id,
                name = fakeUserInfo.name,
                email = fakeUserInfo.email,
                additionalProperties = fakeUserInfo.additionalProperties.toMutableMap()
            )
        )
        whenever(mockRumEventDeserializer.deserialize(viewEventStr))
            .doReturn(fakeViewEvent)
        val expectedLogEvent = createLogEvent(ndkCrashLog, null, null, fakeViewEvent)
        val expectedErrorEventSource = with(fakeViewEvent.source) {
            if (this != null) {
                ErrorEvent.ErrorEventSource.fromJson(this.toJson().asString)
            } else {
                null
            }
        }

        // When
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        argumentCaptor<Any> {
            verify(mockRumWriter, times(1)).write(eq(mockRumEventBatchWriter), capture())

            ErrorEventAssert.assertThat(firstValue as ErrorEvent)
                .hasApplicationId(fakeViewEvent.application.id)
                .hasSessionId(fakeViewEvent.session.id)
                .hasView(
                    fakeViewEvent.view.id,
                    fakeViewEvent.view.name,
                    fakeViewEvent.view.url
                )
                .hasMessage(
                    DatadogNdkCrashHandler.LOG_CRASH_MSG.format(
                        Locale.US,
                        ndkCrashLog.signalName
                    )
                )
                .hasStackTrace(ndkCrashLog.stacktrace)
                .isCrash(true)
                .hasErrorSource(RumErrorSource.SOURCE)
                .hasErrorSourceType(ErrorEvent.SourceType.ANDROID)
                .hasTimestamp(ndkCrashLog.timestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        fakeViewEvent.usr?.id,
                        fakeViewEvent.usr?.name,
                        fakeViewEvent.usr?.email,
                        fakeViewEvent.usr?.additionalProperties.orEmpty()
                    )
                )
                .hasErrorType(ndkCrashLog.signalName)
                .hasLiteSessionPlan()
                .hasSource(expectedErrorEventSource)
                .hasDeviceInfo(
                    fakeAndroidInfoProvider.deviceName,
                    fakeAndroidInfoProvider.deviceModel,
                    fakeAndroidInfoProvider.deviceBrand,
                    fakeAndroidInfoProvider.deviceType.toErrorSchemaType(),
                    fakeAndroidInfoProvider.architecture
                )
                .hasOsInfo(
                    fakeAndroidInfoProvider.osName,
                    fakeAndroidInfoProvider.osVersion,
                    fakeAndroidInfoProvider.osMajorVersion
                )
        }
        verify(mockLogsFeatureScope).sendEvent(expectedLogEvent)
    }

    @Test
    fun `𝕄 not send RUM event 𝕎 handleNdkCrash() { RUM feature is not registered }`(
        @StringForgery crashData: String,
        @StringForgery viewEventStr: String,
        @Forgery ndkCrashLog: NdkCrashLog,
        @Forgery viewEvent: ViewEvent,
        forge: Forge
    ) {
        // Given
        val fakeServerOffset =
            forge.aLong(min = -ndkCrashLog.timestamp, max = Long.MAX_VALUE - ndkCrashLog.timestamp)
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(fakeServerOffset)
        testedHandler.lastSerializedNdkCrashLog = crashData
        testedHandler.lastSerializedRumViewEvent = viewEventStr
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        val fakeViewEvent = viewEvent.copy(
            date = System.currentTimeMillis() - forge.aLong(
                min = 0L,
                max = DatadogNdkCrashHandler.VIEW_EVENT_AVAILABILITY_TIME_THRESHOLD - 1000
            )
        )
        whenever(mockRumEventDeserializer.deserialize(viewEventStr))
            .doReturn(fakeViewEvent)
        whenever(mockSdkCore.getFeature(RumFeature.RUM_FEATURE_NAME)) doReturn null

        // When
        testedHandler.handleNdkCrash(mockSdkCore, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verifyZeroInteractions(mockRumWriter, mockRumEventBatchWriter)
        verify(logger.mockDevLogHandler)
            .handleLog(
                Log.INFO,
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
            "type" to "crash",
            "message" to DatadogNdkCrashHandler.LOG_CRASH_MSG.format(
                Locale.US,
                ndkCrashLog.signalName
            ),
            "attributes" to attributes,
            "timestamp" to ndkCrashLog.timestamp,
            "bundleWithTraces" to false,
            "bundleWithRum" to false,
            "networkInfo" to networkInfo,
            "userInfo" to userInfo
        )
    }

    // endregion

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
