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
internal class UserActionTrackingStrategyApi29Test :
    ActivityLifecycleTrackingStrategyTest<UserActionTrackingStrategyApi29>() {

    @Mock
    lateinit var mockGesturesTracker: GesturesTracker

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        testedStrategy = UserActionTrackingStrategyApi29(mockGesturesTracker)
    }

    @Test
    fun `when activity pre created it will start tracking gestures`() {
        // Given
        testedStrategy.register(rumMonitor.mockSdkCore, mockAppContext)

        // When
        testedStrategy.onActivityPreCreated(mockActivity, mock())

        // Then
        verify(mockGesturesTracker).startTracking(mockWindow, mockActivity, rumMonitor.mockSdkCore)
    }

    override fun createInstance(forge: Forge): UserActionTrackingStrategyApi29 {
        return UserActionTrackingStrategyApi29(mockGesturesTracker)
    }

    override fun createEqualInstance(
        source: UserActionTrackingStrategyApi29,
        forge: Forge
    ): UserActionTrackingStrategyApi29 {
        return UserActionTrackingStrategyApi29(source.gesturesTracker)
    }

    override fun createUnequalInstance(
        source: UserActionTrackingStrategyApi29,
        forge: Forge
    ): UserActionTrackingStrategyApi29? {
        return UserActionTrackingStrategyApi29(mock())
    }
}
