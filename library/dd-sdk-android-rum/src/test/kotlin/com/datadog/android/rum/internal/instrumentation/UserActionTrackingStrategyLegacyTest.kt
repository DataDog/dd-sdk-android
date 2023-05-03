/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import com.datadog.android.rum.ActivityLifecycleTrackingStrategyTest
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UserActionTrackingStrategyLegacyTest :
    ActivityLifecycleTrackingStrategyTest<UserActionTrackingStrategyLegacy>() {

    @Mock
    lateinit var mockGesturesTracker: GesturesTracker

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        testedStrategy = UserActionTrackingStrategyLegacy(mockGesturesTracker)
    }

    @Test
    fun `when activity resumed it will start tracking gestures`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        // When
        testedStrategy.onActivityResumed(mockActivity)

        // Then
        verify(mockGesturesTracker).startTracking(mockWindow, mockActivity, rumMonitor.mockSdkCore)
    }

    @Test
    fun `when activity paused it will stop tracking gestures`() {
        // When
        testedStrategy.onActivityPaused(mockActivity)
        // Then
        verify(mockGesturesTracker).stopTracking(mockWindow, mockActivity)
    }

    override fun createInstance(forge: Forge): UserActionTrackingStrategyLegacy {
        return UserActionTrackingStrategyLegacy(mockGesturesTracker)
    }

    override fun createEqualInstance(
        source: UserActionTrackingStrategyLegacy,
        forge: Forge
    ): UserActionTrackingStrategyLegacy {
        return UserActionTrackingStrategyLegacy(source.gesturesTracker)
    }

    override fun createUnequalInstance(
        source: UserActionTrackingStrategyLegacy,
        forge: Forge
    ): UserActionTrackingStrategyLegacy? {
        return UserActionTrackingStrategyLegacy(mock())
    }
}
