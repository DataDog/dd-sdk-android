/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.callback

import android.view.MotionEvent
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MotionEventUtilsTest {

    @Mock
    lateinit var mockMotionEvent: MotionEvent

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    var pointerXExpectedValues = mutableListOf<Float>()
    var pointerYExpectedValues = mutableListOf<Float>()

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockMotionEvent.pointerCount).thenReturn(forge.anInt(min = 10, max = 20))
        for (pointerIndex in 0 until mockMotionEvent.pointerCount) {
            val x = forge.aFloat()
            val y = forge.aFloat()
            pointerXExpectedValues.add(x)
            pointerYExpectedValues.add(y)
            if (pointerIndex == 0) {
                whenever(mockMotionEvent.rawX).thenReturn(x)
                whenever(mockMotionEvent.rawY).thenReturn(y)
            }
            whenever(mockMotionEvent.getRawX(pointerIndex)).thenReturn(x)
            whenever(mockMotionEvent.getRawY(pointerIndex)).thenReturn(y)
        }
    }

    @Test
    fun `M return the absoluteX pointer position W getPointerAbsoluteX{ from Q above }`(
        forge: Forge
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastQ) doReturn true
        val randomPointerIndex = forge.anInt(min = 0, max = mockMotionEvent.pointerCount)

        // When
        val absolutePointerX = MotionEventUtils.getPointerAbsoluteX(
            mockMotionEvent,
            randomPointerIndex,
            mockBuildSdkVersionProvider
        )

        // Then
        assertThat(absolutePointerX).isEqualTo(pointerXExpectedValues[randomPointerIndex])
    }

    @Test
    fun `M return the absoluteY pointer position W getPointerAbsoluteY{ from Q above }`(
        forge: Forge
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastQ) doReturn true
        val randomPointerIndex = forge.anInt(min = 0, max = mockMotionEvent.pointerCount)

        // When
        val absolutePointerY = MotionEventUtils.getPointerAbsoluteY(
            mockMotionEvent,
            randomPointerIndex,
            mockBuildSdkVersionProvider
        )

        // Then
        assertThat(absolutePointerY).isEqualTo(pointerYExpectedValues[randomPointerIndex])
    }

    @Test
    fun `M return the absoluteX pointer 0 position W getPointerAbsoluteX{ from Q below }`(
        forge: Forge
    ) {
        // Given
        val randomPointerIndex = forge.anInt(min = 0, max = mockMotionEvent.pointerCount)

        // When
        val absolutePointerX = MotionEventUtils.getPointerAbsoluteX(
            mockMotionEvent,
            randomPointerIndex
        )

        // Then
        assertThat(absolutePointerX).isEqualTo(pointerXExpectedValues[0])
    }

    @Test
    fun `M return the absoluteY pointer 0 position W getPointerAbsoluteY{ from Q below }`(
        forge: Forge
    ) {
        // Given
        val randomPointerIndex = forge.anInt(min = 0, max = mockMotionEvent.pointerCount)

        // When
        val absolutePointerY = MotionEventUtils.getPointerAbsoluteY(
            mockMotionEvent,
            randomPointerIndex
        )

        // Then
        assertThat(absolutePointerY).isEqualTo(pointerYExpectedValues[0])
    }
}
