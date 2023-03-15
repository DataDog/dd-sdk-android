/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.google.android.material.slider.Slider
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

    lateinit var testeMaterialExtensionSupport: MaterialExtensionSupport

    @BeforeEach
    fun `set up`() {
        testeMaterialExtensionSupport = MaterialExtensionSupport()
    }

    @Test
    fun `M return a SliderMapper W getCustomViewMappers() { for ALLOW_ALL privacy option }`() {
        // When
        val customMappers = testeMaterialExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers.entries.size).isEqualTo(2)
        assertThat(customMappers[SessionReplayPrivacy.ALLOW_ALL]?.get(Slider::class.java))
            .isInstanceOf(SliderWireframeMapper::class.java)
    }

    @Test
    fun `M return a SliderMapper W getCustomViewMappers() { for MASK_ALL privacy option }`() {
        // When
        val customMappers = testeMaterialExtensionSupport.getCustomViewMappers()

        // Then
        assertThat(customMappers.entries.size).isEqualTo(2)
        assertThat(customMappers[SessionReplayPrivacy.MASK_ALL]?.get(Slider::class.java))
            .isInstanceOf(MaskAllSliderWireframeMapper::class.java)
    }
}
