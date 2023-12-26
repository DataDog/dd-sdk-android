/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import android.webkit.WebView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.aMockView
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
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
internal class WebViewWireframeMapperTest : BaseWireframeMapperTest() {
    private lateinit var testedWebViewWireframeMapper: WebViewWireframeMapper

    @BeforeEach
    fun `set up`() {
        testedWebViewWireframeMapper = WebViewWireframeMapper()
    }

    @Test
    fun `M resolve a WebViewWireframe W map()`(forge: Forge) {
        // Given
        val mockView: WebView = forge.aMockView()
        val expectedWireframes = listOf(mockView.toWebViewWireframe())

        // When
        val mappedWireframes = testedWebViewWireframeMapper.map(mockView, fakeMappingContext)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedWireframes)
    }

    private fun View.toWebViewWireframe(): MobileSegment.Wireframe.WebviewWireframe {
        val coordinates = IntArray(2)
        val screenDensity = fakeMappingContext.systemInformation.screenDensity
        this.getLocationOnScreen(coordinates)
        val x = coordinates[0].densityNormalized(screenDensity).toLong()
        val y = coordinates[1].densityNormalized(screenDensity).toLong()
        return MobileSegment.Wireframe.WebviewWireframe(
            System.identityHashCode(this).toLong(),
            x = x,
            y = y,
            width = width.toLong().densityNormalized(screenDensity),
            height = height.toLong().densityNormalized(screenDensity),
            slotId = System.identityHashCode(this).toString()
        )
    }
}
