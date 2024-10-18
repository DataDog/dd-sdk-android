/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature.Companion.SESSION_REPLAY_FEATURE_NAME
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.SessionReplay.FEATURE_ALREADY_REGISTERED_WARNING
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.sessionreplay.internal.net.SegmentRequestFactory
import com.datadog.android.sessionreplay.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockSystemRequirementsConfiguration: SystemRequirementsConfiguration

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
        SessionReplay.sdkCoreEnabledOn = null
    }

    @Test
    fun `M register session replay feature W enable()`(
        @StringForgery fakePackageName: String,
        @Forgery fakeSessionReplayConfiguration: SessionReplayConfiguration
    ) {
        // When
        val fakeSessionReplayConfigurationWithMockRequirement = fakeSessionReplayConfiguration.copy(
            systemRequirementsConfiguration = mockSystemRequirementsConfiguration
        )
        whenever(
            mockSystemRequirementsConfiguration.runIfRequirementsMet(any(), any())
        ) doAnswer {
            it.getArgument<() -> Unit>(1).invoke()
        }
        SessionReplay.enable(
            fakeSessionReplayConfigurationWithMockRequirement,
            mockSdkCore
        )

        // Then
        argumentCaptor<SessionReplayFeature> {
            verify(mockSdkCore).registerFeature(capture())

            lastValue.onInitialize(
                appContext = mock { whenever(it.packageName) doReturn fakePackageName }
            )
            assertThat(lastValue.privacy).isEqualTo(fakeSessionReplayConfiguration.privacy)
            assertThat((lastValue.requestFactory as SegmentRequestFactory).customEndpointUrl)
                .isEqualTo(fakeSessionReplayConfiguration.customEndpointUrl)
        }
    }

    @Test
    fun `M call manuallyStartRecording on feature W startRecording`(
        @Mock mockFeatureScope: FeatureScope,
        @Mock mockSessionReplayFeature: SessionReplayFeature
    ) {
        // Given
        whenever(mockSdkCore.getFeature(SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(mockFeatureScope)

        whenever(mockFeatureScope.unwrap<SessionReplayFeature>()) doReturn mockSessionReplayFeature

        // When
        SessionReplay.startRecording(mockSdkCore)

        // Then
        verify(mockSessionReplayFeature).manuallyStartRecording()
    }

    @Test
    fun `M call manuallyStopRecording on feature W stopRecording`(
        @Mock mockFeatureScope: FeatureScope,
        @Mock mockSessionReplayFeature: SessionReplayFeature
    ) {
        // Given
        whenever(mockSdkCore.getFeature(SESSION_REPLAY_FEATURE_NAME))
            .thenReturn(mockFeatureScope)

        whenever(mockFeatureScope.unwrap<SessionReplayFeature>()) doReturn mockSessionReplayFeature

        // When
        SessionReplay.stopRecording(mockSdkCore)

        // Then
        verify(mockSessionReplayFeature).manuallyStopRecording()
    }

    @Test
    fun `M warn and send telemetry W enable { session replay feature already registered with another core }`(
        @Forgery fakeSessionReplayConfiguration: SessionReplayConfiguration,
        @Mock mockCore1: FeatureSdkCore,
        @Mock mockCore2: FeatureSdkCore,
        @Mock mockInternalLogger: InternalLogger
    ) {
        // Given
        whenever(mockCore1.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockCore2.internalLogger).thenReturn(mockInternalLogger)
        val fakeSessionReplayConfigurationWithMockRequirement = fakeSessionReplayConfiguration.copy(
            systemRequirementsConfiguration = mockSystemRequirementsConfiguration
        )
        whenever(
            mockSystemRequirementsConfiguration.runIfRequirementsMet(any(), any())
        ) doAnswer {
            it.getArgument<() -> Unit>(1).invoke()
        }
        SessionReplay.enable(
            sessionReplayConfiguration = fakeSessionReplayConfigurationWithMockRequirement,
            sdkCore = mockCore1
        )

        // When
        SessionReplay.enable(
            sessionReplayConfiguration = fakeSessionReplayConfigurationWithMockRequirement,
            sdkCore = mockCore2
        )

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            message = FEATURE_ALREADY_REGISTERED_WARNING
        )
    }
}
