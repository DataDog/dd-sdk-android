/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import android.app.Application
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.sessionreplay.SessionReplayLifecycleCallback
import com.datadog.android.sessionreplay.internal.domain.SessionReplayRecordPersistenceStrategy
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SessionReplayFeatureTest {

    private lateinit var testedFeature: SessionReplayFeature

    @Forgery
    lateinit var fakeConfigurationFeature: Configuration.Feature.SessionReplay

    @Mock
    lateinit var mockSessionReplayLifecycleCallback: SessionReplayLifecycleCallback

    @Mock
    lateinit var mockSDKCore: SdkCore

    @Mock
    lateinit var mockStorage: Storage

    @BeforeEach
    fun `set up`() {
        testedFeature = SessionReplayFeature(
            coreFeature.mockInstance,
            mockStorage,
            mockSDKCore
        ) { _, _ -> mockSessionReplayLifecycleCallback }
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(SessionReplayRecordPersistenceStrategy::class.java)
    }

    @Test
    fun `𝕄 initialize session replay callback 𝕎 initialize()`() {
        // Given
        testedFeature = SessionReplayFeature(
            coreFeature.mockInstance,
            mockStorage,
            mockSDKCore
        )

        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.sessionReplayCallback)
            .isInstanceOf(SessionReplayLifecycleCallback::class.java)
    }

    @Test
    fun `M register the Session Replay lifecycle callback W initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        verify(mockSessionReplayLifecycleCallback)
            .register(appContext.mockInstance)
    }

    @Test
    fun `M unregister the Session Replay lifecycle callback W stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        verify(mockSessionReplayLifecycleCallback)
            .unregisterAndStopRecorders(appContext.mockInstance)
    }

    @Test
    fun `M reset the Session Replay lifecycle callback W stop()`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.sessionReplayCallback)
            .isInstanceOf(NoOpLifecycleCallback::class.java)
    }

    @Test
    fun `M unregister the SessionReplayCallback W stopRecording() { was recording }`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

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
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

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
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
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
        testedFeature.initialize(mock(), fakeConfigurationFeature)

        // When
        testedFeature.stopRecording()

        // Then
        verifyZeroInteractions(mockSessionReplayLifecycleCallback)
    }

    @Test
    fun `M register the SessionReplayCallback W startRecording() { was stopped before }`() {
        // Given
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
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
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

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
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // When
        testedFeature.startRecording()

        // Then
        verify(mockSessionReplayLifecycleCallback)
            .register(appContext.mockInstance)
    }

    @Test
    fun `M do nothing W startRecording() { initialize without Application context }`() {
        // Given
        testedFeature.initialize(mock(), fakeConfigurationFeature)

        // When
        testedFeature.startRecording()

        // Then
        verifyZeroInteractions(mockSessionReplayLifecycleCallback)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
