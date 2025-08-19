/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
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
@ForgeConfiguration(Configurator::class)
internal class DefaultAccessibilitySnapshotManagerTest {

    @Mock
    lateinit var mockAccessibilityReader: InfoProvider<AccessibilityInfo>

    private lateinit var testedManager: DefaultAccessibilitySnapshotManager

    @BeforeEach
    fun setup() {
        testedManager = DefaultAccessibilitySnapshotManager(mockAccessibilityReader)
    }

    @Test
    fun `M return empty accessibility W latestSnapshot() { no accessibility data }`() {
        // Given
        whenever(mockAccessibilityReader.getState()) doReturn AccessibilityInfo()

        // When
        val result = testedManager.latestSnapshot()

        // Then
        assertThat(result).isEqualTo(AccessibilityInfo())
    }

    @Test
    fun `M return all changes W latestSnapshot() { first call with complete data }`(
        @FloatForgery textSize: Float,
        @BoolForgery screenReader: Boolean,
        @BoolForgery colorInversion: Boolean,
        @BoolForgery closedCaptioning: Boolean,
        @BoolForgery reducedAnimations: Boolean,
        @BoolForgery screenPinning: Boolean,
        @BoolForgery rtlEnabled: Boolean
    ) {
        // Given
        val accessibilityState = AccessibilityInfo(
            textSize = textSize.toString(),
            isScreenReaderEnabled = screenReader,
            isColorInversionEnabled = colorInversion,
            isClosedCaptioningEnabled = closedCaptioning,
            isReducedAnimationsEnabled = reducedAnimations,
            isScreenPinningEnabled = screenPinning,
            isRtlEnabled = rtlEnabled
        )
        whenever(mockAccessibilityReader.getState()) doReturn accessibilityState

        // When
        val result = testedManager.latestSnapshot()

        // Then
        assertThat(result).isEqualTo(
            AccessibilityInfo(
                textSize = textSize.toString(),
                isScreenReaderEnabled = screenReader,
                isColorInversionEnabled = colorInversion,
                isClosedCaptioningEnabled = closedCaptioning,
                isReducedAnimationsEnabled = reducedAnimations,
                isScreenPinningEnabled = screenPinning,
                isRtlEnabled = rtlEnabled
            )
        )
    }

    @Test
    fun `M return empty accessibility W latestSnapshot() { second call with same data }`(
        @FloatForgery textSize: Float,
        @BoolForgery screenReader: Boolean
    ) {
        // Given
        val accessibilityState = AccessibilityInfo(
            textSize = textSize.toString(),
            isScreenReaderEnabled = screenReader
        )
        whenever(mockAccessibilityReader.getState()) doReturn accessibilityState

        // When - First call
        testedManager.latestSnapshot()

        // When - Second call with same data
        val result = testedManager.latestSnapshot()

        // Then
        assertThat(result).isEqualTo(AccessibilityInfo())
    }

    @Test
    fun `M return only changed values W latestSnapshot() { some values changed }`(
        forge: Forge,
        @FloatForgery initialTextSize: Float,
        @BoolForgery screenReader: Boolean,
        @BoolForgery colorInversion: Boolean
    ) {
        // Given
        val initialState = AccessibilityInfo(
            textSize = initialTextSize.toString(),
            isScreenReaderEnabled = screenReader,
            isColorInversionEnabled = colorInversion
        )

        val newTextSize = rerollFloat(initialTextSize, forge)

        val changedState = AccessibilityInfo(
            textSize = newTextSize.toString(), // Changed
            isScreenReaderEnabled = screenReader, // Same
            isColorInversionEnabled = colorInversion // Same
        )

        whenever(mockAccessibilityReader.getState())
            .doReturn(initialState)
            .doReturn(changedState)

        // When
        testedManager.latestSnapshot() // First call
        val result = testedManager.latestSnapshot() // Second call

        // Then - Only changed value should be returned
        assertThat(result).isEqualTo(
            AccessibilityInfo(textSize = newTextSize.toString())
        )
    }

    @Test
    fun `M return only new values W latestSnapshot() { new accessibility settings added }`(
        @FloatForgery textSize: Float,
        @BoolForgery screenReader: Boolean,
        @BoolForgery colorInversion: Boolean
    ) {
        // Given
        val initialState = AccessibilityInfo(
            textSize = textSize.toString()
        )
        val expandedState = AccessibilityInfo(
            textSize = textSize.toString(),
            isScreenReaderEnabled = screenReader,
            isColorInversionEnabled = colorInversion
        )

        whenever(mockAccessibilityReader.getState())
            .doReturn(initialState)
            .doReturn(expandedState)

        // When
        testedManager.latestSnapshot() // First call
        val result = testedManager.latestSnapshot() // Second call

        // Then - Only new values should be returned
        assertThat(result).isEqualTo(
            AccessibilityInfo(
                isScreenReaderEnabled = screenReader,
                isColorInversionEnabled = colorInversion
            )
        )
    }

    @Test
    fun `M not report change W latestSnapshot() { value changes from non-null to null }`(
        @FloatForgery textSize: Float,
        @BoolForgery screenReader: Boolean
    ) {
        // Given
        val initialState = AccessibilityInfo(
            textSize = textSize.toString(),
            isScreenReaderEnabled = screenReader,
            isColorInversionEnabled = true
        )
        val stateWithNull = AccessibilityInfo(
            textSize = textSize.toString(),
            isScreenReaderEnabled = screenReader
        )

        whenever(mockAccessibilityReader.getState())
            .doReturn(initialState)
            .doReturn(stateWithNull)

        // When
        testedManager.latestSnapshot() // First call
        val result = testedManager.latestSnapshot() // Second call

        // Then - No changes should be reported (null values are filtered)
        assertThat(result).isEqualTo(AccessibilityInfo())
    }

    @Test
    fun `M not report null keys W latestSnapshot() { key disappears from state }`(
        @FloatForgery textSize: Float,
        @BoolForgery screenReader: Boolean,
        @BoolForgery colorInversion: Boolean
    ) {
        // Given
        val completeState = AccessibilityInfo(
            textSize = textSize.toString(),
            isScreenReaderEnabled = screenReader,
            isColorInversionEnabled = colorInversion
        )
        val incompleteState = AccessibilityInfo(
            textSize = textSize.toString(),
            isScreenReaderEnabled = screenReader
            // COLOR_INVERSION_ENABLED_KEY missing
        )

        whenever(mockAccessibilityReader.getState())
            .doReturn(completeState)
            .doReturn(incompleteState)

        // When
        testedManager.latestSnapshot() // First call
        val result = testedManager.latestSnapshot() // Second call

        // Then - No changes should be reported for missing keys (they become null)
        assertThat(result).isEqualTo(AccessibilityInfo())
    }

    @Test
    fun `M maintain state consistency W latestSnapshot() { multiple sequential calls }`(
        forge: Forge,
        @FloatForgery textSize1: Float
    ) {
        // Given
        val textSize2 = rerollFloat(textSize1, forge)
        val textSize3 = rerollFloat(textSize2, forge)

        val state1 = AccessibilityInfo(textSize = textSize1.toString())
        val state2 = AccessibilityInfo(textSize = textSize2.toString())
        val state3 = AccessibilityInfo(textSize = textSize2.toString()) // Same as state2
        val state4 = AccessibilityInfo(textSize = textSize3.toString())

        whenever(mockAccessibilityReader.getState())
            .doReturn(state1)
            .doReturn(state2)
            .doReturn(state3)
            .doReturn(state4)

        // When & Then
        val result1 = testedManager.latestSnapshot()
        assertThat(result1.textSize).isEqualTo(textSize1.toString())

        val result2 = testedManager.latestSnapshot()
        assertThat(result2.textSize).isEqualTo(textSize2.toString())

        val result3 = testedManager.latestSnapshot()
        assertThat(result3).isEqualTo(AccessibilityInfo()) // No change

        val result4 = testedManager.latestSnapshot()
        assertThat(result4.textSize).isEqualTo(textSize3.toString())
    }

    /**
     * Ensure the float value is not the same as the original
     * this avoids some flakiness that we could have in the tests due to values being randomly the same
     */
    private fun rerollFloat(originalValue: Float, forge: Forge): Float {
        var newValue = originalValue
        while (newValue == originalValue) {
            newValue = forge.aFloat()
        }
        return newValue
    }
}
