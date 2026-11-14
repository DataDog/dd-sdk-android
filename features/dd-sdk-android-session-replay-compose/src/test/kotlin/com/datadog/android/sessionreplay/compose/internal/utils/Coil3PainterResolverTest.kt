/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.MutableStateFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class Coil3PainterResolverTest {

    @Mock
    lateinit var mockAsyncImagePainter: AsyncImagePainter

    @Mock
    lateinit var mockLoadedPainter: Painter

    private lateinit var testedResolver: Coil3PainterResolver

    @BeforeEach
    fun `set up`() {
        testedResolver = Coil3PainterResolver()
    }

    @Test
    fun `M return state painter W resolveStatePainter() {state with painter}`() {
        // Given
        val fakeState = AsyncImagePainter.State.Loading(mockLoadedPainter)
        whenever(mockAsyncImagePainter.state) doReturn MutableStateFlow(fakeState)

        // When
        val result = testedResolver.resolveStatePainter(mockAsyncImagePainter)

        // Then
        assertThat(result).isSameAs(mockLoadedPainter)
    }

    @Test
    fun `M return null W resolveStatePainter() {empty state}`() {
        // Given
        whenever(mockAsyncImagePainter.state) doReturn MutableStateFlow(AsyncImagePainter.State.Empty)

        // When
        val result = testedResolver.resolveStatePainter(mockAsyncImagePainter)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W resolveStatePainter() {not an AsyncImagePainter}`() {
        // When
        val result = testedResolver.resolveStatePainter(mockLoadedPainter)

        // Then
        assertThat(result).isNull()
    }
}
