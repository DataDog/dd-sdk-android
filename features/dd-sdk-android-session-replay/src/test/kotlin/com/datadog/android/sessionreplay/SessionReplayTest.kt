/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.remote.model.RemoteConfiguration
import com.datadog.android.sessionreplay.SessionReplay.IS_ALREADY_REGISTERED_WARNING
import com.datadog.android.sessionreplay.SessionReplay.UNEXPECTED_SDK_CORE_TYPE
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.SessionReplayFeature
import com.datadog.android.sessionreplay.internal.net.SegmentRequestFactory
import com.datadog.android.utils.verifyLog
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
    lateinit var mockSdkCore: InternalSdkCore

    @Mock
    lateinit var mockSystemRequirementsConfiguration: SystemRequirementsConfiguration

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
        whenever(mockSdkCore.remoteConfiguration) doReturn null
        SessionReplay.currentRegisteredCore = null
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
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME))
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
        whenever(mockSdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME))
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
        @Mock mockCore1: InternalSdkCore,
        @Mock mockCore2: InternalSdkCore,
        @Mock mockInternalLogger: InternalLogger
    ) {
        // Given
        whenever(mockCore1.isCoreActive()).thenReturn(true)
        whenever(mockCore1.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockCore1.remoteConfiguration) doReturn null
        whenever(mockCore2.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockCore2.remoteConfiguration) doReturn null
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
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.MAINTAINER),
            message = IS_ALREADY_REGISTERED_WARNING
        )

        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.DEBUG,
            targets = listOf(InternalLogger.Target.TELEMETRY),
            message = IS_ALREADY_REGISTERED_WARNING
        )
        assertThat(SessionReplay.currentRegisteredCore?.get()).isEqualTo(mockCore1)
    }

    @Test
    fun `M allow changing cores W enable { Session Replay already enabled but old core inactive }`(
        @Forgery fakeSessionReplayConfiguration: SessionReplayConfiguration,
        @Mock mockCore1: InternalSdkCore,
        @Mock mockCore2: InternalSdkCore,
        @Mock mockInternalLogger: InternalLogger
    ) {
        // Given
        whenever(mockCore1.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockCore1.remoteConfiguration) doReturn null
        whenever(mockCore2.internalLogger).thenReturn(mockInternalLogger)
        whenever(mockCore2.remoteConfiguration) doReturn null
        val fakeSessionReplayConfigurationWithMockRequirement = fakeSessionReplayConfiguration.copy(
            systemRequirementsConfiguration = mockSystemRequirementsConfiguration
        )
        whenever(
            mockSystemRequirementsConfiguration.runIfRequirementsMet(any(), any())
        ) doAnswer {
            it.getArgument<() -> Unit>(1).invoke()
        }
        whenever(mockCore1.isCoreActive()).thenReturn(true)
        SessionReplay.enable(
            sessionReplayConfiguration = fakeSessionReplayConfigurationWithMockRequirement,
            sdkCore = mockCore1
        )
        assertThat(SessionReplay.currentRegisteredCore?.get()).isEqualTo(mockCore1)

        // When
        whenever(mockCore1.isCoreActive()).thenReturn(false)
        SessionReplay.enable(
            sessionReplayConfiguration = fakeSessionReplayConfigurationWithMockRequirement,
            sdkCore = mockCore2
        )

        // Then
        assertThat(SessionReplay.currentRegisteredCore?.get()).isEqualTo(mockCore2)
    }

    // region Remote Configuration

    @Test
    fun `M log error and return W enable { sdkCore is not InternalSdkCore }`(
        @Forgery fakeSessionReplayConfiguration: SessionReplayConfiguration,
        @Mock mockNonInternalCore: FeatureSdkCore,
        @Mock mockInternalLogger: InternalLogger
    ) {
        // Given
        whenever(mockNonInternalCore.internalLogger) doReturn mockInternalLogger

        // When
        SessionReplay.enable(fakeSessionReplayConfiguration, mockNonInternalCore)

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER),
            message = UNEXPECTED_SDK_CORE_TYPE
        )
        assertThat(SessionReplay.currentRegisteredCore).isNull()
    }

    @Test
    fun `M register feature with merged configuration W enable { RC provides sessionReplay values }`(
        @Forgery fakeSessionReplayConfiguration: SessionReplayConfiguration,
        @Forgery fakeRemoteConfiguration: RemoteConfiguration
    ) {
        // Given
        val fakeConfigWithMockRequirements = fakeSessionReplayConfiguration.copy(
            systemRequirementsConfiguration = mockSystemRequirementsConfiguration
        )
        whenever(mockSdkCore.remoteConfiguration) doReturn fakeRemoteConfiguration
        whenever(
            mockSystemRequirementsConfiguration.runIfRequirementsMet(any(), any())
        ) doAnswer {
            it.getArgument<() -> Unit>(1).invoke()
        }

        // When
        SessionReplay.enable(fakeConfigWithMockRequirements, mockSdkCore)

        // Then — SessionReplayFeature is registered with the effective (potentially merged) config
        argumentCaptor<SessionReplayFeature> {
            verify(mockSdkCore).registerFeature(capture())
            val remoteSessionReplay = fakeRemoteConfiguration.sessionReplay
            // Privacy fields from RC override in-code values when present
            assertThat(lastValue.imagePrivacy).isEqualTo(
                remoteSessionReplay?.imagePrivacy?.toSdkImagePrivacy()
                    ?: fakeConfigWithMockRequirements.imagePrivacy
            )
            assertThat(lastValue.textAndInputPrivacy).isEqualTo(
                remoteSessionReplay?.textAndInputPrivacy?.toSdkTextAndInputPrivacy()
                    ?: fakeConfigWithMockRequirements.textAndInputPrivacy
            )
            assertThat(lastValue.touchPrivacy).isEqualTo(
                remoteSessionReplay?.touchPrivacy?.toSdkTouchPrivacy()
                    ?: fakeConfigWithMockRequirements.touchPrivacy
            )
        }
    }

    @Test
    fun `M register feature with in-code configuration W enable { null remote configuration }`(
        @Forgery fakeSessionReplayConfiguration: SessionReplayConfiguration
    ) {
        // Given
        val fakeConfigWithMockRequirements = fakeSessionReplayConfiguration.copy(
            systemRequirementsConfiguration = mockSystemRequirementsConfiguration
        )
        whenever(mockSdkCore.remoteConfiguration) doReturn null
        whenever(
            mockSystemRequirementsConfiguration.runIfRequirementsMet(any(), any())
        ) doAnswer {
            it.getArgument<() -> Unit>(1).invoke()
        }

        // When
        SessionReplay.enable(fakeConfigWithMockRequirements, mockSdkCore)

        // Then — SessionReplayFeature is registered with in-code values unchanged
        argumentCaptor<SessionReplayFeature> {
            verify(mockSdkCore).registerFeature(capture())
            assertThat(lastValue.imagePrivacy).isEqualTo(fakeConfigWithMockRequirements.imagePrivacy)
            assertThat(lastValue.textAndInputPrivacy)
                .isEqualTo(fakeConfigWithMockRequirements.textAndInputPrivacy)
            assertThat(lastValue.touchPrivacy).isEqualTo(fakeConfigWithMockRequirements.touchPrivacy)
        }
    }

    // endregion

    // region Helpers

    private fun RemoteConfiguration.ImagePrivacy.toSdkImagePrivacy(): ImagePrivacy = when (this) {
        RemoteConfiguration.ImagePrivacy.MASK_NONE -> ImagePrivacy.MASK_NONE
        RemoteConfiguration.ImagePrivacy.MASK_LARGE_ONLY -> ImagePrivacy.MASK_LARGE_ONLY
        RemoteConfiguration.ImagePrivacy.MASK_ALL -> ImagePrivacy.MASK_ALL
    }

    private fun RemoteConfiguration.TextAndInputPrivacy.toSdkTextAndInputPrivacy(): TextAndInputPrivacy =
        when (this) {
            RemoteConfiguration.TextAndInputPrivacy.MASK_SENSITIVE_INPUTS ->
                TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
            RemoteConfiguration.TextAndInputPrivacy.MASK_ALL_INPUTS ->
                TextAndInputPrivacy.MASK_ALL_INPUTS
            RemoteConfiguration.TextAndInputPrivacy.MASK_ALL -> TextAndInputPrivacy.MASK_ALL
        }

    private fun RemoteConfiguration.TouchPrivacy.toSdkTouchPrivacy(): TouchPrivacy = when (this) {
        RemoteConfiguration.TouchPrivacy.SHOW -> TouchPrivacy.SHOW
        RemoteConfiguration.TouchPrivacy.HIDE -> TouchPrivacy.HIDE
    }

    // endregion
}
