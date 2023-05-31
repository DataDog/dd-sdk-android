/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.app.Application
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.NoOpLifecycleCallback
import com.datadog.android.sessionreplay.internal.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRequestFactory
import com.datadog.android.sessionreplay.internal.storage.SessionReplayRecordWriter
import com.datadog.android.sessionreplay.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
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

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var sessionReplayEndpointUrl: String

    @Forgery
    lateinit var fakePrivacy: SessionReplayPrivacy

    @Mock
    lateinit var mockSessionReplayLifecycleCallback: SessionReplayLifecycleCallback

    @Mock
    lateinit var mockSdkCore: SdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockSdkCore._internalLogger) doReturn mockInternalLogger
        testedFeature = SessionReplayFeature(
            customEndpointUrl = forge.aNullable { sessionReplayEndpointUrl },
            privacy = fakePrivacy
        ) { _, _ -> mockSessionReplayLifecycleCallback }
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
    fun `𝕄 initialize session replay callback 𝕎 initialize()`(
        forge: Forge
    ) {
        // Given
        testedFeature = SessionReplayFeature(
            customEndpointUrl = forge.aNullable { sessionReplayEndpointUrl },
            privacy = fakePrivacy,
            customMappers = emptyList(),
            customOptionSelectorDetectors = emptyList()
        )

        // When
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // Then
        assertThat(testedFeature.sessionReplayCallback)
            .isInstanceOf(SessionReplayLifecycleCallback::class.java)
    }

    @Test
    fun `𝕄 set the feature event receiver 𝕎 initialize()`(
        forge: Forge
    ) {
        // Given
        testedFeature = SessionReplayFeature(
            customEndpointUrl = forge.aNullable { sessionReplayEndpointUrl },
            privacy = fakePrivacy,
            customMappers = emptyList(),
            customOptionSelectorDetectors = emptyList()
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
        verify(mockSessionReplayLifecycleCallback)
            .register(appContext.mockInstance)
    }

    @Test
    fun `M unregister the Session Replay lifecycle callback W onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        verify(mockSessionReplayLifecycleCallback)
            .unregisterAndStopRecorders(appContext.mockInstance)
    }

    @Test
    fun `M reset the Session Replay lifecycle callback W onStop()`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.onStop()

        // Then
        assertThat(testedFeature.sessionReplayCallback)
            .isInstanceOf(NoOpLifecycleCallback::class.java)
    }

    @Test
    fun `M unregister the SessionReplayCallback W stopRecording() { was recording }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.stopRecording()

        // Then
        verify(mockSessionReplayLifecycleCallback)
            .unregisterAndStopRecorders(appContext.mockInstance)
    }

    @Test
    fun `M unregister only once the SessionReplayCallback W stopRecording() { multi threads }`() {
        // Given
        val countDownLatch = CountDownLatch(3)
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        repeat(3) {
            Thread {
                testedFeature.stopRecording()
                countDownLatch.countDown()
            }.start()
        }

        // Then
        countDownLatch.await(5, TimeUnit.SECONDS)
        verify(mockSessionReplayLifecycleCallback)
            .register(appContext.mockInstance)
        verify(mockSessionReplayLifecycleCallback)
            .unregisterAndStopRecorders(appContext.mockInstance)
        verifyNoMoreInteractions(mockSessionReplayLifecycleCallback)
    }

    @Test
    fun `M do nothing W stopRecording() { was already stopped }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        testedFeature.stopRecording()

        // When
        testedFeature.stopRecording()

        // Then
        verify(mockSessionReplayLifecycleCallback)
            .register(appContext.mockInstance)
        verify(mockSessionReplayLifecycleCallback)
            .unregisterAndStopRecorders(appContext.mockInstance)
        verifyNoMoreInteractions(mockSessionReplayLifecycleCallback)
    }

    @Test
    fun `M do nothing W stopRecording() { initialize without Application context }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, mock())

        // When
        testedFeature.stopRecording()

        // Then
        verifyNoInteractions(mockSessionReplayLifecycleCallback)
    }

    @Test
    fun `M register the SessionReplayCallback W startRecording() { was stopped before }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        testedFeature.stopRecording()

        // When
        testedFeature.startRecording()

        // Then
        verify(mockSessionReplayLifecycleCallback, times(2))
            .register(appContext.mockInstance)
    }

    @Test
    fun `M register only once the SessionReplayCallback W startRecording() { multi threads }`() {
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
        verify(mockSessionReplayLifecycleCallback).register(appContext.mockInstance)
        verifyNoMoreInteractions(mockSessionReplayLifecycleCallback)
    }

    @Test
    fun `M do nothing W startRecording() { was already started before }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)

        // When
        testedFeature.startRecording()

        // Then
        verify(mockSessionReplayLifecycleCallback)
            .register(appContext.mockInstance)
    }

    @Test
    fun `M do nothing W startRecording() { initialize without Application context }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, mock())

        // When
        testedFeature.startRecording()

        // Then
        verifyNoInteractions(mockSessionReplayLifecycleCallback)
    }

    // TODO RUMM-0000 Mock InternalLogger.UNBOUND
    @Disabled("Needs mock of InternalLogger.UNBOUND")
    @Test
    fun `M log warning and do nothing W startRecording() { feature is not initialized }`() {
        // When
        testedFeature.startRecording()

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.CANNOT_START_RECORDING_NOT_INITIALIZED
            )
        verifyNoInteractions(mockSessionReplayLifecycleCallback)
    }

    @Test
    fun `M stopRecording W rum session updated { session not tracked }`() {
        // Given
        testedFeature.onInitialize(mockSdkCore, appContext.mockInstance)
        val rumSessionUpdateBusMessage = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE,
            SessionReplayFeature.RUM_KEEP_SESSION_BUS_MESSAGE_KEY to
                false
        )

        // When
        testedFeature.onReceive(rumSessionUpdateBusMessage)

        // Then
        inOrder(mockSessionReplayLifecycleCallback) {
            verify(mockSessionReplayLifecycleCallback).register(appContext.mockInstance)
            verify(mockSessionReplayLifecycleCallback)
                .unregisterAndStopRecorders(appContext.mockInstance)
        }
        verifyNoMoreInteractions(mockSessionReplayLifecycleCallback)
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
        inOrder(mockSessionReplayLifecycleCallback) {
            verify(mockSessionReplayLifecycleCallback).register(appContext.mockInstance)
            verify(mockSessionReplayLifecycleCallback)
                .unregisterAndStopRecorders(appContext.mockInstance)
            verify(mockSessionReplayLifecycleCallback).register(appContext.mockInstance)
        }
        verifyNoMoreInteractions(mockSessionReplayLifecycleCallback)
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { unknown event type }`() {
        // Given
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(Any())

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.UNSUPPORTED_EVENT_TYPE.format(
                    Locale.US,
                    Any()::class.java.canonicalName
                )
            )

        verifyNoInteractions(mockSessionReplayLifecycleCallback)
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
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        val expectedMessage = SessionReplayFeature.UNKNOWN_EVENT_TYPE_PROPERTY_VALUE
            .format(Locale.US, event[SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY])
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                expectedMessage
            )

        verifyNoInteractions(mockSessionReplayLifecycleCallback)
    }

    @Test
    fun `𝕄 log warning and do nothing 𝕎 onReceive() { missing mandatory fields }`() {
        // Given
        val event = mapOf(
            SessionReplayFeature.SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to
                SessionReplayFeature.RUM_SESSION_RENEWED_BUS_MESSAGE
        )
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyNoInteractions(mockSessionReplayLifecycleCallback)
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
        testedFeature.sdkCore = mockSdkCore

        // When
        testedFeature.onReceive(event)

        // Then
        verify(mockInternalLogger)
            .log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                SessionReplayFeature.EVENT_MISSING_MANDATORY_FIELDS
            )

        verifyNoInteractions(mockSessionReplayLifecycleCallback)
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

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
