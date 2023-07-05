/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.ViewGroup
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ComposedOptionSelectorDetectorTest {

    lateinit var testedComposedOptionSelectorDetector: ComposedOptionSelectorDetector

    lateinit var mockBundledDetectors: List<OptionSelectorDetector>

    @Mock
    lateinit var mockViewGroup: ViewGroup

    @BoolForgery
    var fakeBundledDetectorValue: Boolean = false

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockBundledDetectors = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            mock {
                whenever(it.isOptionSelector(mockViewGroup))
                    .thenReturn(fakeBundledDetectorValue)
            }
        }
        testedComposedOptionSelectorDetector = ComposedOptionSelectorDetector(mockBundledDetectors)
    }

    @Test
    fun `M delegate to bundled detectors W isOptionSelector`() {
        assertThat(testedComposedOptionSelectorDetector.isOptionSelector(mockViewGroup))
            .isEqualTo(fakeBundledDetectorValue)
    }

    @Test
    fun `M return true W isOptionSelector { at least one bundled returns true }`(forge: Forge) {
        // Given
        mockBundledDetectors.forEach {
            whenever(it.isOptionSelector(mockViewGroup)).thenReturn(false)
        }
        val fakeRandomIndex = if (mockBundledDetectors.size > 1) {
            forge.anInt(min = 0, max = mockBundledDetectors.size - 1)
        } else {
            0
        }
        whenever(mockBundledDetectors[fakeRandomIndex].isOptionSelector(mockViewGroup))
            .thenReturn(true)

        // Then
        assertThat(testedComposedOptionSelectorDetector.isOptionSelector(mockViewGroup)).isTrue
    }

    @Test
    fun `M return false W isOptionSelector { bundled detectors are empty }`() {
        // Given
        testedComposedOptionSelectorDetector = ComposedOptionSelectorDetector(emptyList())

        // Then
        assertThat(testedComposedOptionSelectorDetector.isOptionSelector(mockViewGroup)).isFalse
    }
}
