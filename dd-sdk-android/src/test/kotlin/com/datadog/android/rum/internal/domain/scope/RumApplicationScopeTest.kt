/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumApplicationScopeTest {

    lateinit var testedScope: RumApplicationScope

    @Mock
    lateinit var mockChildScope: RumScope

    @Mock
    lateinit var mockEvent: RumRawEvent

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockDetector: FirstPartyHostDetector

    @Mock
    lateinit var mockCpuVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockMemoryVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockFrameRateVitalMonitor: VitalMonitor

    @Mock
    lateinit var mockSessionListener: RumSessionListener

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockSdkCore: SdkCore

    @StringForgery(regex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    lateinit var fakeApplicationId: String

    @FloatForgery(min = 0f, max = 100f)
    var fakeSamplingRate: Float = 0f

    @BoolForgery
    var fakeBackgroundTrackingEnabled: Boolean = false

    @BoolForgery
    var fakeTrackFrustrations: Boolean = true

    @BeforeEach
    fun `set up`() {
        testedScope = RumApplicationScope(
            fakeApplicationId,
            mockSdkCore,
            fakeSamplingRate,
            fakeBackgroundTrackingEnabled,
            fakeTrackFrustrations,
            mockDetector,
            mockCpuVitalMonitor,
            mockMemoryVitalMonitor,
            mockFrameRateVitalMonitor,
            mockSessionListener,
            mockContextProvider
        )
    }

    @Test
    fun `create child session scope with sampling rate`() {
        val childScope = testedScope.childScope

        check(childScope is RumSessionScope)
        assertThat(childScope.samplingRate).isEqualTo(fakeSamplingRate)
        assertThat(childScope.backgroundTrackingEnabled).isEqualTo(fakeBackgroundTrackingEnabled)
        assertThat(childScope.firstPartyHostDetector).isSameAs(mockDetector)
    }

    @Test
    fun `always returns the same applicationId`() {
        val context = testedScope.getRumContext()

        assertThat(context.applicationId).isEqualTo(fakeApplicationId)
    }

    @Test
    fun `M return true W isActive()`() {
        val isActive = testedScope.isActive()

        assertThat(isActive).isTrue()
    }

    @Test
    fun `delegates all events to child scope`() {
        testedScope.setFieldValue("childScope", mockChildScope)

        testedScope.handleEvent(mockEvent, mockWriter)

        verify(mockChildScope).handleEvent(mockEvent, mockWriter)
        verifyZeroInteractions(mockWriter)
    }
}
