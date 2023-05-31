/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.view.ViewGroup
import com.datadog.android.sessionreplay.material.forge.ForgeConfigurator
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class MaterialOptionSelectorDetectorTest {

    lateinit var testedMaterialOptionSelectorDetector: MaterialOptionSelectorDetector

    @Mock
    lateinit var mockViewGroup: ViewGroup

    @BeforeEach
    fun `set up`() {
        testedMaterialOptionSelectorDetector = MaterialOptionSelectorDetector()
    }

    @Test
    fun `M return false W isOptionSelector { container is not a option selector holder }`(
        forge: Forge
    ) {
        // Given
        whenever(mockViewGroup.id).thenReturn(forge.anInt())

        // When
        assertThat(testedMaterialOptionSelectorDetector.isOptionSelector(mockViewGroup)).isFalse
    }

    @Test
    fun `M return true W isOptionSelector { container is material picker header }`() {
        // Given
        whenever(mockViewGroup.id).thenReturn(com.google.android.material.R.id.mtrl_picker_header)

        // When
        assertThat(testedMaterialOptionSelectorDetector.isOptionSelector(mockViewGroup)).isTrue
    }
}
