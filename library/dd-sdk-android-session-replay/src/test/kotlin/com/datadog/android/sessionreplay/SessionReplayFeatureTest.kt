/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Application
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRequestFactory
import com.datadog.android.sessionreplay.internal.storage.NoOpRecordWriter
import com.datadog.android.sessionreplay.internal.storage.SessionReplayRecordWriter
import com.datadog.android.sessionreplay.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.sessionreplay.utils.config.InternalLoggerTestConfiguration
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayFeatureTest {

    private lateinit var testedFeature: SessionReplayFeature

    @Forgery
    lateinit var fakeConfigurationFeature: SessionReplayConfiguration

    @Mock
    lateinit var mockRecorder: Recorder

    @Mock
    lateinit var mockSdkCore: SdkCore

    @BeforeEach
    fun `set up`() {
        testedFeature = SessionReplayFeature(
            fakeConfigurationFeature
        ) { _, _, _ -> mockRecorder }
    }

    @Test
    fun `𝕄 initialize writer 𝕎 initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.dataWriter)
            .isInstanceOf(SessionReplayRecordWriter::class.java)
    }

    @Test
    fun `𝕄 initialize session replay recorder 𝕎 initialize()`() {
        // Given
        testedFeature = SessionReplayFeature(
            fakeConfigurationFeature
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.sessionReplayRecorder)
            .isInstanceOf(SessionReplayRecorder::class.java)
    }

    @Test
    fun `𝕄 set the feature event receiver 𝕎 initialize()`() {
        // Given
        testedFeature = SessionReplayFeature(
            fakeConfigurationFeature
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        verify(mockSdkCore).setEventReceiver(
            SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME,
            testedFeature
        )
    }

    @Test
    fun `M register the Session Replay lifecycle callback W initialize()`() {
        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        verify(mockRecorder).registerCallbacks()
    }

    @Test
    fun `M unregister the Session Replay lifecycle callback W onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        verify(mockRecorder).unregisterCallbacks()
    }

    @Test
    fun `M invalidate the feature components W onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.dataWriter).isInstanceOf(NoOpRecordWriter::class.java)
        assertThat(testedFeature.sessionReplayRecorder).isInstanceOf(NoOpRecorder::class.java)
        assertThat(testedFeature.initialized.get()).isFalse
    }

    @Test
    fun `M do nothing W stopRecording() { was already stopped }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        testedFeature.startRecording()
        testedFeature.stopRecording()

        // When
        testedFeature.stopRecording()

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M do nothing W stopRecording() { initialize without Application context }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, mock())

        // When
        testedFeature.stopRecording()

        // Then
        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `M resume recorders W startRecording() { was stopped before }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        testedFeature.startRecording()
        testedFeature.stopRecording()

        // When
        testedFeature.startRecording()

        // Then
        verify(mockRecorder, times(2))
            .resumeRecorders()
    }

    @Test
    fun `M resume recorders only once W startRecording() { multi threads }`() {
        // Given
        val countDownLatch = CountDownLatch(3)
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        repeat(3) {
            Thread {
                testedFeature.startRecording()
                countDownLatch.countDown()
            }.start()
        }

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M do nothing W startRecording() { was already started before }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        testedFeature.startRecording()

        // When
        testedFeature.startRecording()

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
        }
        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `M do nothing W startRecording() { initialize without Application context }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, mock())

        // When
        testedFeature.startRecording()

        // Then
        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `M log warning and do nothing W startRecording() { feature is not initialized }`() {
        // When
        testedFeature.startRecording()

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.CANNOT_START_RECORDING_NOT_INITIALIZED
            )
        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `M log warning and do nothing W onInitialize() { context is not Application }`() {
        // When
        testedFeature.onInitialize(mockSdkCore, mock())

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.REQUIRES_APPLICATION_CONTEXT_WARN_MESSAGE
            )
        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `M stopRecording W rum session updated { session not tracked }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val rumSessionUpdateBusMessage1 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true
        )
        val rumSessionUpdateBusMessage2 = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage1)
        testedFeature.onReceive(rumSessionUpdateBusMessage2)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
            verify(mockRecorder).stopRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `M startRecording W rum session updated { session tracked }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        testedFeature.stopRecording()
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                true
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        inOrder(mockRecorder) {
            verify(mockRecorder).registerCallbacks()
            verify(mockRecorder).resumeRecorders()
        }
        verifyNoMoreInteractions(mockRecorder)
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { unknown event type }`() {
        // When
        testedFeature.onReceive(Any())

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.UNSUPPORTED_EVENT_TYPE.format(
                    Locale.US,
                    Any()::class.java.canonicalName
                )
            )

        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { unknown type property value }`(
        forge: Forge
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                forge.anAlphabeticalString()
        )

        // When
        testedFeature.onReceive(event)

        // Then
        val expectedMessage = SessionReplayFeature.UNKNOWN_EVENT_TYPE_PROPERTY_VALUE
            .format(Locale.US, event[SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY])
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                expectedMessage
            )

        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { missing mandatory fields }`() {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { mandatory fields have wrong format }`(
        forge: Forge
    ) {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                forge.anAlphabeticalString()
        )

        // When
        testedFeature.onReceive(event)

        // Then
        verify(logger.mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyZeroInteractions(mockRecorder)
    }

    @Test
    fun `𝕄 provide session replay feature name 𝕎 name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME)
    }

    @Test
    fun `𝕄 provide Session Replay request factory 𝕎 requestFactory()`() {
        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(SessionReplayRequestFactory::class.java)
    }

    @Test
    fun `𝕄 provide default storage configuration 𝕎 storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, logger)
        }
    }
}
