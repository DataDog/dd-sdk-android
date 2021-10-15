/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.ndk

import android.content.Context
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.assertj.ErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeast
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.firstValue
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogNdkCrashHandlerTest {

    lateinit var testedHandler: DatadogNdkCrashHandler

    @TempDir
    lateinit var fakeCacheDir: File

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockLogGenerator: LogGenerator

    @Mock
    lateinit var mockNdkCrashLogDeserializer: Deserializer<NdkCrashLog>

    @Mock
    lateinit var mockRumEventDeserializer: Deserializer<Any>

    @Mock
    lateinit var mockNetworkInfoDeserializer: Deserializer<NetworkInfo>

    @Mock
    lateinit var mockUserInfoDeserializer: Deserializer<UserInfo>

    @Mock
    lateinit var mockLogWriter: DataWriter<LogEvent>

    @Mock
    lateinit var mockRumWriter: DataWriter<Any>

    @Mock
    lateinit var mockLogHandler: LogHandler

    lateinit var fakeNdkCacheDir: File

    @Forgery
    lateinit var fakeLog: LogEvent

    @Captor
    lateinit var captureRunnable: ArgumentCaptor<Runnable>

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.cacheDir) doReturn fakeCacheDir
        fakeNdkCacheDir = File(fakeCacheDir, DatadogNdkCrashHandler.NDK_CRASH_REPORTS_FOLDER_NAME)

        testedHandler = DatadogNdkCrashHandler(
            mockContext,
            mockExecutorService,
            mockLogGenerator,
            mockNdkCrashLogDeserializer,
            mockRumEventDeserializer,
            mockNetworkInfoDeserializer,
            mockUserInfoDeserializer,
            Logger(mockLogHandler),
            mockTimeProvider
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
        testedHandler.handleNdkCrash(mockLogWriter, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        verifyZeroInteractions(mockLogWriter, mockRumWriter)
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
        testedHandler.handleNdkCrash(mockLogWriter, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        captureRunnable.firstValue.run()
        verifyZeroInteractions(mockLogWriter, mockRumWriter, mockLogHandler)
    }

    @Test
    fun `𝕄 send log 𝕎 handleNdkCrash() {missing RUM last view, no info}`(
        @StringForgery crashData: String,
        @Forgery ndkCrashLog: NdkCrashLog
    ) {
        // Given
        testedHandler.lastSerializedNdkCrashLog = crashData
        whenever(mockNdkCrashLogDeserializer.deserialize(crashData)) doReturn ndkCrashLog
        stubLogGenerator(ndkCrashLog, null, null, null)

        // When
        testedHandler.handleNdkCrash(mockLogWriter, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockLogWriter)
        captureRunnable.firstValue.run()
        verify(mockLogWriter).write(fakeLog)
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
        stubLogGenerator(ndkCrashLog, networkInfo, userInfo, null)

        // When
        testedHandler.handleNdkCrash(mockLogWriter, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockLogWriter)
        captureRunnable.firstValue.run()
        verify(mockLogWriter).write(fakeLog)
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
        stubLogGenerator(ndkCrashLog, null, null, null)

        // When
        testedHandler.handleNdkCrash(mockLogWriter, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockLogWriter)
        captureRunnable.firstValue.run()
        verify(mockLogWriter).write(fakeLog)
        verifyZeroInteractions(mockRumWriter, mockLogHandler)
    }

    @Test
    fun `𝕄 send log 𝕎 handleNdkCrash() {with RUM last view}`(
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
        whenever(mockRumEventDeserializer.deserialize(viewEventStr))
            .doReturn(viewEvent)
        stubLogGenerator(ndkCrashLog, null, null, viewEvent)

        // When
        testedHandler.handleNdkCrash(mockLogWriter, mockRumWriter)

        // Then
        verify(mockExecutorService).submit(captureRunnable.capture())
        verifyZeroInteractions(mockLogWriter)
        captureRunnable.firstValue.run()
        argumentCaptor<Any> {
            verify(mockRumWriter, atLeast(1)).write(capture())

            assertThat(firstValue as ErrorEvent)
                .hasApplicationId(viewEvent.application.id)
                .hasSessionId(viewEvent.session.id)
                .hasView(
                    viewEvent.view.id,
                    viewEvent.view.name,
                    viewEvent.view.url
                )
                .hasMessage(
                    DatadogNdkCrashHandler.LOG_CRASH_MSG.format(
                        Locale.US,
                        ndkCrashLog.signalName
                    )
                )
                .hasStackTrace(ndkCrashLog.stacktrace)
                .isCrash(true)
                .hasSource(RumErrorSource.SOURCE)
                .hasTimestamp(ndkCrashLog.timestamp + fakeServerOffset)
                .hasUserInfo(
                    UserInfo(
                        viewEvent.usr?.id,
                        viewEvent.usr?.name,
                        viewEvent.usr?.email,
                        viewEvent.usr?.additionalProperties ?: emptyMap()
                    )
                )
                .hasErrorType(ndkCrashLog.signalName)
                .hasLiteSessionPlan()
        }
        verify(mockLogWriter).write(fakeLog)
    }

    // region Internal

    private fun stubLogGenerator(
        ndkCrashLog: NdkCrashLog,
        networkInfo: NetworkInfo?,
        userInfo: UserInfo?,
        rumViewEvent: ViewEvent? = null
    ) {
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
        whenever(
            mockLogGenerator.generateLog(
                LogGenerator.CRASH,
                DatadogNdkCrashHandler.LOG_CRASH_MSG.format(Locale.US, ndkCrashLog.signalName),
                throwable = null,
                attributes = attributes,
                tags = emptySet(),
                timestamp = ndkCrashLog.timestamp,
                threadName = null,
                bundleWithTraces = false,
                bundleWithRum = false,
                networkInfo = networkInfo,
                userInfo = userInfo

            )
        ) doReturn fakeLog
    }

    // endregion
}
