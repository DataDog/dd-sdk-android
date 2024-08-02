/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal

import androidx.compose.runtime.State
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumMonitor
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class TapActionTrackerTest {

    private lateinit var testedTracker: TapActionTracker

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @MapForgery(
        key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
        value = AdvancedForgery(string = [StringForgery(StringForgeryType.ASCII)])
    )
    lateinit var fakeAttributes: Map<String, String>

    @Mock
    lateinit var mockOnClickState: State<() -> Unit>

    @Mock
    lateinit var mockOnClick: () -> Unit

    @StringForgery
    lateinit var fakeTargetName: String

    @BeforeEach
    fun setUp() {
        whenever(mockOnClickState.value) doReturn mockOnClick

        testedTracker = TapActionTracker(fakeTargetName, fakeAttributes, mockOnClickState, mockRumMonitor)
    }

    @Test
    fun `M call addAction W invoke`() {
        // When
        testedTracker.invoke()

        // Then
        verify(mockRumMonitor).addAction(
            RumActionType.TAP,
            fakeTargetName,
            fakeAttributes + mapOf(RumAttributes.ACTION_TARGET_TITLE to fakeTargetName)
        )
    }
}
