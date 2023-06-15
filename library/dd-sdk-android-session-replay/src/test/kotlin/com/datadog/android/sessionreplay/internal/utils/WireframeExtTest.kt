/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@Extensions(ExtendWith(ForgeExtension::class))
@ForgeConfiguration(ForgeConfigurator::class)
internal class WireframeExtTest {

    // region hasOpaqueBackground

    @ParameterizedTest
    @MethodSource("testWireframes")
    fun `M return true W hashOpaqueBackground { shapeStyle fully opaque with color }`(
        fakeWireframe: MobileSegment.Wireframe,
        @StringForgery(regex = "#[0-9A-Fa-f]{6}[fF]{2}") fakeOpaqueColor: String,
        forge: Forge
    ) {
        // Given
        val fakeShapeStyle: MobileSegment.ShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>()
            .copy(
                backgroundColor = fakeOpaqueColor,
                opacity = forge.anElementFrom(listOf(1f, 1))
            )
        val fakeTestWireframe = fakeWireframe.testCopy(shapeStyle = fakeShapeStyle)

        // Then
        assertThat(fakeTestWireframe.hasOpaqueBackground()).isTrue
    }

    @ParameterizedTest
    @MethodSource("testWireframes")
    fun `M return false W hashOpaqueBackground { shapeStyle is null }`(
        fakeWireframe: MobileSegment.Wireframe
    ) {
        // Given
        val fakeTestWireframe = fakeWireframe.testCopy(shapeStyle = null)

        // Then
        assertThat(fakeTestWireframe.hasOpaqueBackground()).isFalse
    }

    @ParameterizedTest
    @MethodSource("testWireframes")
    fun `M return false W hashOpaqueBackground { shapeStyle not fully opaque }`(
        fakeWireframe: MobileSegment.Wireframe,
        @FloatForgery(0f, 1f) fakeOpacity: Float,
        @StringForgery(regex = "#[0-9A-Fa-f]{6}[fF]{2}") fakeOpaqueColor: String
    ) {
        // Given
        val fakeShapeStyle: MobileSegment.ShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>()
            .copy(backgroundColor = fakeOpaqueColor, opacity = fakeOpacity)
        val fakeTestWireframe = fakeWireframe.testCopy(shapeStyle = fakeShapeStyle)

        // Then
        assertThat(fakeTestWireframe.hasOpaqueBackground()).isFalse
    }

    @ParameterizedTest
    @MethodSource("testWireframes")
    fun `M return false W hashOpaqueBackground { shapeStyle color not opaque }`(
        fakeWireframe: MobileSegment.Wireframe,
        @StringForgery(regex = "#[0-9A-Fa-f]{6}[0-9A-Ea-e]{2}") fakeTranslucentColor: String,
        forge: Forge
    ) {
        // Given
        val fakeShapeStyle: MobileSegment.ShapeStyle = forge.getForgery<MobileSegment.ShapeStyle>()
            .copy(
                backgroundColor = fakeTranslucentColor,
                opacity = forge.anElementFrom(listOf(1f, 1))
            )
        val fakeTestWireframe = fakeWireframe.testCopy(shapeStyle = fakeShapeStyle)

        // Then
        assertThat(fakeTestWireframe.hasOpaqueBackground()).isFalse
    }

    // endregion

    // region shapeStyle

    @ParameterizedTest
    @MethodSource("testWireframes")
    fun `M return shapeStyle W shapeStyle()`(
        fakeWireframe: MobileSegment.Wireframe,
        @Forgery fakeShapeStyle: MobileSegment.ShapeStyle?
    ) {
        // Given
        val fakeTestWireframe = fakeWireframe.testCopy(shapeStyle = fakeShapeStyle)

        // Then
        assertThat(fakeTestWireframe.shapeStyle()).isEqualTo(fakeShapeStyle)
    }

    // endregion

    // region copy

    @ParameterizedTest
    @MethodSource("testCopyWireframes")
    fun `M return a copy W copy { with ShapeStyle }`(
        fakeWireframe: MobileSegment.Wireframe,
        fakeShapeStyle: MobileSegment.ShapeStyle?,
        expectedCopyWireframe: MobileSegment.Wireframe
    ) {
        assertThat(fakeWireframe.copy(shapeStyle = fakeShapeStyle))
            .isEqualTo(expectedCopyWireframe)
    }

    // endregion

    // region Internal

    private fun MobileSegment.Wireframe.testCopy(shapeStyle: MobileSegment.ShapeStyle?):
        MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.TextWireframe -> this.copy(shapeStyle = shapeStyle)
            is MobileSegment.Wireframe.ShapeWireframe -> this.copy(shapeStyle = shapeStyle)
            is MobileSegment.Wireframe.ImageWireframe -> this.copy(shapeStyle = shapeStyle)
        }
    }

    // endregion

    companion object {
        val forge = Forge()

        @JvmStatic
        fun testWireframes(): List<MobileSegment.Wireframe> {
            ForgeConfigurator().configure(forge)
            return listOf(
                forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>(),
                forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
            )
        }

        @JvmStatic
        fun testCopyWireframes(): Stream<Arguments> {
            ForgeConfigurator().configure(forge)
            val fakeTextWireframe: MobileSegment.Wireframe.TextWireframe = forge.getForgery()
            val fakeShapeWireframe: MobileSegment.Wireframe.ShapeWireframe = forge.getForgery()
            val expectedTextShapeStyle: MobileSegment.ShapeStyle? = forge.aNullable { getForgery() }
            val expectedShapeShapeStyle: MobileSegment.ShapeStyle? = forge.aNullable { getForgery() }
            val expectedTextWireframe = fakeTextWireframe.copy(shapeStyle = expectedTextShapeStyle)
            val expectedShapeWireframe = fakeShapeWireframe.copy(shapeStyle = expectedShapeShapeStyle)
            return listOf(
                Arguments.of(
                    fakeShapeWireframe,
                    expectedShapeShapeStyle,
                    expectedShapeWireframe
                ),
                Arguments.of(
                    fakeTextWireframe,
                    expectedTextShapeStyle,
                    expectedTextWireframe
                )
            ).stream()
        }
    }
}
