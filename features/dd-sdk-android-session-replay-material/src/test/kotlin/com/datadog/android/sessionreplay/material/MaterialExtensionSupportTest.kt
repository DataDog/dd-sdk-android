/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout.TabView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(ExtendWith(MockitoExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
class MaterialExtensionSupportTest {

    lateinit var testedMaterialExtensionSupport: MaterialExtensionSupport

    @BeforeEach
    fun `set up`() {
        testedMaterialExtensionSupport = MaterialExtensionSupport()
    }

    @Test
    fun `M return a SliderMapper W getCustomViewMappers() { ALLOW }`() {
        // When
        val customMappers = testedMaterialExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers.entries.size).isEqualTo(3)
        assertThat(customMappers[SessionReplayPrivacy.ALLOW]?.get(Slider::class.java))
            .isInstanceOf(SliderWireframeMapper::class.java)
    }

    @Test
    fun `M return a MaskSliderMapper W getCustomViewMappers() { MASK }`() {
        // When
        val customMappers = testedMaterialExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers.entries.size).isEqualTo(3)
        assertThat(customMappers[SessionReplayPrivacy.MASK]?.get(Slider::class.java))
            .isInstanceOf(MaskSliderWireframeMapper::class.java)
    }

    @Test
    fun `M return a MaskSliderMapper W getCustomViewMappers() { MASK_USER_INPUT }`() {
        // When
        val customMappers = testedMaterialExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers.entries.size).isEqualTo(3)
        assertThat(customMappers[SessionReplayPrivacy.MASK_USER_INPUT]?.get(Slider::class.java))
            .isInstanceOf(MaskSliderWireframeMapper::class.java)
    }

    @Test
    fun `M return a TabMapper W getCustomViewMappers() { ALLOW }`() {
        // When
        val customMappers = testedMaterialExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers.entries.size).isEqualTo(3)
        assertThat(customMappers[SessionReplayPrivacy.ALLOW]?.get(TabView::class.java))
            .isInstanceOf(TabWireframeMapper::class.java)
    }

    @Test
    fun `M return a MaskTabMapper W getCustomViewMappers() { MASK  }`() {
        // When
        val customMappers = testedMaterialExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers.entries.size).isEqualTo(3)
        assertThat(customMappers[SessionReplayPrivacy.MASK]?.get(TabView::class.java))
            .isInstanceOf(MaskTabWireframeMapper::class.java)
    }

    @Test
    fun `M return a TabWireframeMapper W getCustomViewMappers() { MASK_USER_INPUT  }`() {
        // When
        val customMappers = testedMaterialExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers.entries.size).isEqualTo(3)
        assertThat(customMappers[SessionReplayPrivacy.MASK_USER_INPUT]?.get(TabView::class.java))
            .isInstanceOf(TabWireframeMapper::class.java)
    }

    @Test
    fun `M return a MaterialOptionSelectorDetector W getOptionSelectorDetectors`() {
        // When
        val customDetectors = testedMaterialExtensionSupport.getOptionSelectorDetectors()

        // Then
        assertThat(customDetectors.size).isEqualTo(1)
        assertThat(customDetectors[0]).isInstanceOf(MaterialOptionSelectorDetector::class.java)
    }
}
