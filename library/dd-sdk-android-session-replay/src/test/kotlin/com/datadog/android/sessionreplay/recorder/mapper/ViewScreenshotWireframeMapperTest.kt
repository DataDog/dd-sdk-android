/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.aMockView
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ViewScreenshotWireframeMapperTest : BaseWireframeMapperTest() {

    lateinit var testedWireframeMapper: ViewScreenshotWireframeMapper

    @BeforeEach
    fun `set up`() {
        testedWireframeMapper = ViewScreenshotWireframeMapper()
    }

    @Test
    fun `M resolve a ShapeWireframe with border W map()`(forge: Forge) {
        // Given
        val mockView: View = forge.aMockView()
        // When
        val shapeWireframe = testedWireframeMapper.map(mockView, fakePixelDensity)

        // Then
        val expectedWireframe = mockView.toShapeWireframe()
            .copy(border = MobileSegment.ShapeBorder("#000000FF", 1))
        assertThat(shapeWireframe).isEqualTo(expectedWireframe)
    }
}
