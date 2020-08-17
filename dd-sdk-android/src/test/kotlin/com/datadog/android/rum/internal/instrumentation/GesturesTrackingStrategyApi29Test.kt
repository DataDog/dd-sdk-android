/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import com.datadog.android.rum.ActivityLifecycleTrackingStrategyTest
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class GesturesTrackingStrategyApi29Test : ActivityLifecycleTrackingStrategyTest() {

    @Mock
    lateinit var mockGesturesTracker: GesturesTracker

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        testedStrategy = GesturesTrackingStrategyApi29(mockGesturesTracker)
    }

    @Test
    fun `when activity pre created it will start tracking gestures`(forge: Forge) {
        // When
        testedStrategy.onActivityPreCreated(mockActivity, mock())
        // Then
        verify(mockGesturesTracker).startTracking(mockWindow, mockActivity)
    }
}
