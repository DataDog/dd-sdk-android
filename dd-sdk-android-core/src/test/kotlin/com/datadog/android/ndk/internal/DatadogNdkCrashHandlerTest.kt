/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.internal.persistence.file.FileReader
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonObject
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.lastValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale
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

    @Forgery
    lateinit var fakeNdkCrashLog: NdkCrashLog

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
            mockInternalLogger,
            mockLastRumViewEventProvider
        )
    }

    // region prepareData

    @Test
    fun `M read crash data W prepareData()`(
        @StringForgery crashDataStr: String
    ) {
        // Given
        fakeNdkCacheDir.mkdirs()
        File(fakeNdkCacheDir, DatadogNdkCrashHandler.CRASH_DATA_FILE_NAME).writeText(crashDataStr)
        whenever(mockNdkCrashLogDeserializer.deserialize(crashDataStr)) doReturn fakeNdkCrashLog

        // When
        testedHandler.prepareData()

        // Then
        assertThat(testedHandler.lastNdkCrashLog).isNull()
        verify(mockExecutorService).execute(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastNdkCrashLog)
            .isEqualTo(fakeNdkCrashLog)
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
        verify(mockExecutorService).execute(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastRumViewEvent)
            .isEqualTo(fakeViewEvent.toJson())
    }

    @Test
    fun `M do nothing W prepareData() {directory does not exist}`() {
        // When
        testedHandler.prepareData()
        whenever(mockNdkCrashLogDeserializer.deserialize(any())) doReturn mock()

        // Then
        verify(mockExecutorService).execute(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(testedHandler.lastRumViewEvent).isNull()
        assertThat(testedHandler.lastNdkCrashLog).isNull()
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
        verify(mockExecutorService).execute(captureRunnable.capture())
        captureRunnable.firstValue.run()
        assertThat(fakeNdkCacheDir).isEmptyDirectory
    }

    // endregion

    // region handleNdkCrash / Logs

    @Test
    fun `M do nothing W handleNdkCrash() {no crash data}`() {
        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).execute(captureRunnable.capture())
        captureRunnable.firstValue.run()
        verifyNoInteractions(mockSdkCore)
    }

    @Test
    fun `M not send log W handleNdkCrash()`() {
        // Given
        testedHandler.lastNdkCrashLog = fakeNdkCrashLog

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).execute(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verifyNoInteractions(mockLogsFeatureScope, mockInternalLogger)
    }

    // endregion

    // region handleNdkCrash / RUM

    @Test
    fun `M not send RUM event W handleNdkCrash() { RUM feature is not registered }`(
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = forge.aFakeViewEvent()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn null
        testedHandler.lastNdkCrashLog = fakeNdkCrashLog
        testedHandler.lastRumViewEvent = fakeViewEvent.toJson()

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).execute(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogNdkCrashHandler.INFO_RUM_FEATURE_NOT_REGISTERED
        )
    }

    @Test
    fun `M not send RUM event W handleNdkCrash() { missing last RUM view event }`() {
        // Given
        testedHandler.lastNdkCrashLog = fakeNdkCrashLog

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).execute(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verifyNoInteractions(mockInternalLogger, mockRumFeatureScope)
    }

    @Test
    fun `M send RUM event W handleNdkCrash()`(forge: Forge) {
        // Given
        val fakeViewEvent = forge.aFakeViewEvent()
        testedHandler.lastNdkCrashLog = fakeNdkCrashLog
        testedHandler.lastRumViewEvent = fakeViewEvent.toJson()

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).execute(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "ndk_crash",
                "sourceType" to "ndk",
                "timestamp" to fakeNdkCrashLog.timestamp,
                "timeSinceAppStartMs" to fakeNdkCrashLog.timeSinceAppStartMs,
                "signalName" to fakeNdkCrashLog.signalName,
                "stacktrace" to fakeNdkCrashLog.stacktrace,
                "message" to DatadogNdkCrashHandler.LOG_CRASH_MSG
                    .format(Locale.US, fakeNdkCrashLog.signalName),
                "lastViewEvent" to fakeViewEvent.toJson()
            )
        )
    }

    @Test
    fun `M send RUM event W handleNdkCrash() { override native source type } `(forge: Forge) {
        // Given
        val handler = DatadogNdkCrashHandler(
            tempDir,
            mockExecutorService,
            mockNdkCrashLogDeserializer,
            mockInternalLogger,
            lastRumViewEventProvider = { JsonObject() },
            nativeCrashSourceType = "ndk+il2cpp"
        )

        val fakeViewEvent = forge.aFakeViewEvent()
        handler.lastNdkCrashLog = fakeNdkCrashLog
        handler.lastRumViewEvent = fakeViewEvent.toJson()

        // When
        handler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).execute(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.firstValue.run()
        verify(mockRumFeatureScope).sendEvent(
            mapOf(
                "type" to "ndk_crash",
                "sourceType" to "ndk+il2cpp",
                "timestamp" to fakeNdkCrashLog.timestamp,
                "timeSinceAppStartMs" to fakeNdkCrashLog.timeSinceAppStartMs,
                "signalName" to fakeNdkCrashLog.signalName,
                "stacktrace" to fakeNdkCrashLog.stacktrace,
                "message" to DatadogNdkCrashHandler.LOG_CRASH_MSG
                    .format(Locale.US, fakeNdkCrashLog.signalName),
                "lastViewEvent" to fakeViewEvent.toJson()
            )
        )
    }

    @Test
    fun `M clear the references W handleNdkCrash()`(
        forge: Forge
    ) {
        // Given
        val fakeViewEvent = forge.aFakeViewEvent()
        testedHandler.lastNdkCrashLog = fakeNdkCrashLog
        testedHandler.lastRumViewEvent = fakeViewEvent.toJson()

        // When
        testedHandler.handleNdkCrash(mockSdkCore)

        // Then
        verify(mockExecutorService).execute(captureRunnable.capture())
        verifyNoInteractions(mockSdkCore)
        captureRunnable.lastValue.run()

        assertThat(testedHandler.lastNdkCrashLog).isNull()
        assertThat(testedHandler.lastRumViewEvent).isNull()
    }

    // endregion

    // region Internal

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
