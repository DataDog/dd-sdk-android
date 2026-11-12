/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import coil.compose.AsyncImagePainter
import coil.request.SuccessResult
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.flow.MutableStateFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import coil3.compose.AsyncImagePainter as AsyncImagePainter3

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class ReflectionUtilsTest {

    private lateinit var testedReflectionUtils: ReflectionUtils

    @BeforeEach
    fun `set up`() {
        testedReflectionUtils = ReflectionUtils()
    }

    // region getBrushColors

    @Test
    fun `M return single-element list W getBrushColors { SolidColor }`() {
        // Given
        val fakeColor = Color.Red
        val solidBrush = SolidColor(fakeColor)

        // When
        val result = testedReflectionUtils.getBrushColors(solidBrush)

        // Then
        assertThat(result).containsExactly(fakeColor)
    }

    @Test
    fun `M return Color from SolidColor W getBrushColors { SolidColor with arbitrary color }`() {
        // Given — verify the value round-trips correctly, not just that the list is non-empty
        val fakeColor = Color(0xFF42A5F5.toInt())
        val solidBrush = SolidColor(fakeColor)

        // When
        val result = testedReflectionUtils.getBrushColors(solidBrush)

        // Then — verify the color value round-trips correctly through the list
        assertThat(result).containsExactly(fakeColor)
    }

    @Test
    fun `M return null W getBrushColors { non-SolidColor brush }`() {
        // Given — any non-SolidColor brush hits the reflection path. In the unit-test JVM,
        // ComposeReflection.LinearGradientClass and siblings are null (the internal Compose classes
        // are not on the test classpath), so all three isInstance checks evaluate to false and the
        // when-expression takes the `else -> return null` branch (genuinely unrecognised type).
        // In production, a recognised gradient type with an unreadable colors field would return
        // emptyList() instead, making the two failure modes distinguishable in telemetry.
        val mockBrush = mock<Brush>()

        // When
        val result = testedReflectionUtils.getBrushColors(mockBrush)

        // Then
        assertThat(result).isNull()
    }

    // endregion

    @Test
    fun `M return state painter W getNestedPainter() {Coil AsyncImagePainter}`() {
        // Given
        val mockLoadedPainter = mock<Painter>()
        val fakeState = AsyncImagePainter.State.Success(mockLoadedPainter, mock<SuccessResult>())
        val mockAsyncImagePainter = mock<AsyncImagePainter> {
            on { state } doReturn fakeState
        }

        // When
        val result = testedReflectionUtils.getNestedPainter(mockAsyncImagePainter)

        // Then
        assertThat(result).isSameAs(mockLoadedPainter)
    }

    @Test
    fun `M return null W getNestedPainter() {Coil AsyncImagePainter with empty state}`() {
        // Given
        val mockAsyncImagePainter = mock<AsyncImagePainter> {
            on { state } doReturn AsyncImagePainter.State.Empty
        }

        // When
        val result = testedReflectionUtils.getNestedPainter(mockAsyncImagePainter)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return state painter W getNestedPainter() {Coil 3 AsyncImagePainter}`() {
        // Given
        val mockLoadedPainter = mock<Painter>()
        val fakeState = AsyncImagePainter3.State.Loading(mockLoadedPainter)
        val mockAsyncImagePainter = mock<AsyncImagePainter3> {
            on { state } doReturn MutableStateFlow(fakeState)
        }

        // When
        val result = testedReflectionUtils.getNestedPainter(mockAsyncImagePainter)

        // Then
        assertThat(result).isSameAs(mockLoadedPainter)
    }
}
