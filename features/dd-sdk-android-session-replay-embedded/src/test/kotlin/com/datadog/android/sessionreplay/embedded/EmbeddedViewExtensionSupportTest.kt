/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded

import com.datadog.android.sessionreplay.embedded.EmbeddedViewExtensionSupport.Companion.EMBEDDED_VIEW_EXTENSION_SUPPORT_NAME
import com.datadog.android.sessionreplay.embedded.internal.EmbeddedViewWireframeMapper
import com.example.embedded.AnotherEmbeddedEngineView
import com.example.embedded.EmbeddedEngineView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EmbeddedViewExtensionSupportTest {

    @Mock
    lateinit var mockEmbeddedEngineView: EmbeddedEngineView

    @Mock
    lateinit var mockAnotherEmbeddedEngineView: AnotherEmbeddedEngineView

    @Test
    fun `M return a mapper matching the given class name W getCustomViewMappers()`() {
        // Given
        val testedEmbeddedViewExtensionSupport = EmbeddedViewExtensionSupport(
            EmbeddedEngineView::class.java.name
        )

        // When
        val customMappers = testedEmbeddedViewExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers).hasSize(1)
        val wrapper = customMappers[0]
        assertThat(wrapper.supportsView(mockEmbeddedEngineView)).isTrue
        assertThat(wrapper.getUnsafeMapper()).isInstanceOf(EmbeddedViewWireframeMapper::class.java)
    }

    @Test
    fun `M return a mapper for each given class name W getCustomViewMappers() {multiple classes}`() {
        // Given
        val testedEmbeddedViewExtensionSupport = EmbeddedViewExtensionSupport(
            listOf(EmbeddedEngineView::class.java.name, AnotherEmbeddedEngineView::class.java.name)
        )

        // When
        val customMappers = testedEmbeddedViewExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers).hasSize(2)
        assertThat(customMappers.any { it.supportsView(mockEmbeddedEngineView) }).isTrue
        assertThat(customMappers.any { it.supportsView(mockAnotherEmbeddedEngineView) }).isTrue
    }

    @Test
    fun `M skip a class name that cannot be resolved W getCustomViewMappers()`() {
        // Given
        val testedEmbeddedViewExtensionSupport = EmbeddedViewExtensionSupport(
            "com.example.embedded.NonExistentEngineView"
        )

        // When
        val customMappers = testedEmbeddedViewExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers).isEmpty()
    }

    @Test
    fun `M return no OptionSelectorDetector W getOptionSelectorDetectors()`() {
        // Given
        val testedEmbeddedViewExtensionSupport = EmbeddedViewExtensionSupport(
            EmbeddedEngineView::class.java.name
        )

        // When
        val customDetectors = testedEmbeddedViewExtensionSupport.getOptionSelectorDetectors()

        // Then
        assertThat(customDetectors).isEmpty()
    }

    @Test
    fun `M return no DrawableToColorMapper W getCustomDrawableMapper()`() {
        // Given
        val testedEmbeddedViewExtensionSupport = EmbeddedViewExtensionSupport(
            EmbeddedEngineView::class.java.name
        )

        // When
        val customDrawableMappers = testedEmbeddedViewExtensionSupport.getCustomDrawableMapper()

        // Then
        assertThat(customDrawableMappers).isEmpty()
    }

    @Test
    fun `M return name of extension W name()`() {
        // Given
        val testedEmbeddedViewExtensionSupport = EmbeddedViewExtensionSupport(
            EmbeddedEngineView::class.java.name
        )

        // Then
        assertThat(testedEmbeddedViewExtensionSupport.name()).isEqualTo(EMBEDDED_VIEW_EXTENSION_SUPPORT_NAME)
    }
}
