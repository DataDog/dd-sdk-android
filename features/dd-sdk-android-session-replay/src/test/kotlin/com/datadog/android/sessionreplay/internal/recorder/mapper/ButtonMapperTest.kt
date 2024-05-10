/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.Button
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ButtonMapperTest : BaseWireframeMapperTest() {

    private lateinit var testedButtonMapper: ButtonMapper

    @Mock
    lateinit var mockTextWireframeMapper: TextViewMapper

    @Forgery
    lateinit var fakeTextWireframes: List<MobileSegment.Wireframe.TextWireframe>

    @Mock
    lateinit var mockButton: Button

    @BeforeEach
    fun `set up`() {
        whenever(
            mockTextWireframeMapper.map(
                eq(mockButton),
                eq(fakeMappingContext),
                any(),
                eq(mockInternalLogger)
            )
        ).thenReturn(fakeTextWireframes)
        testedButtonMapper = ButtonMapper(mockTextWireframeMapper)
    }

    @Test
    fun `M resolve textWireframes W map(){textMapper returns wireframes with border}`(
        forge: Forge
    ) {
        // Given
        fakeTextWireframes = fakeTextWireframes.map { it.copy(border = forge.getForgery()) }
        whenever(
            mockTextWireframeMapper.map(
                eq(mockButton),
                eq(fakeMappingContext),
                any(),
                eq(mockInternalLogger)
            )
        ).thenReturn(fakeTextWireframes)

        // When
        val buttonWireframes = testedButtonMapper.map(
            mockButton,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(buttonWireframes).isEqualTo(fakeTextWireframes)
    }

    @Test
    fun `M resolve textWireframes W map(){textMapper returns wireframes with shapeStyle}`(
        forge: Forge
    ) {
        // Given
        fakeTextWireframes = fakeTextWireframes.map { it.copy(shapeStyle = forge.getForgery()) }
        whenever(
            mockTextWireframeMapper.map(
                eq(mockButton),
                eq(fakeMappingContext),
                any(),
                eq(mockInternalLogger)
            )
        ).thenReturn(fakeTextWireframes)

        // When
        val buttonWireframes = testedButtonMapper.map(
            mockButton,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(buttonWireframes).isEqualTo(fakeTextWireframes)
    }

    @Test
    fun `M add a default border W map(){textMapper returns wireframes with no border, shapeStyle}`() {
        // Given
        fakeTextWireframes = fakeTextWireframes.map { it.copy(shapeStyle = null, border = null) }
        whenever(
            mockTextWireframeMapper.map(
                eq(mockButton),
                eq(fakeMappingContext),
                any(),
                eq(mockInternalLogger)
            )
        ).thenReturn(fakeTextWireframes)

        val expectedWireframes = fakeTextWireframes.map {
            it.copy(border = MobileSegment.ShapeBorder(ButtonMapper.BLACK_COLOR, 1))
        }

        // When
        val buttonWireframes = testedButtonMapper.map(
            mockButton,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(buttonWireframes).isEqualTo(expectedWireframes)
    }
}
