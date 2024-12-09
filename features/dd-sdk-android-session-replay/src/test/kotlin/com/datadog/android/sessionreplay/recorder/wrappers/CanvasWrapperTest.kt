/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.wrappers

import android.graphics.Bitmap
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CanvasWrapperTest {

    lateinit var testedWrapper: CanvasWrapper

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockBitmap: Bitmap

    @BeforeEach
    fun `set up`() {
        testedWrapper = CanvasWrapper(mockLogger)
    }

    @Test
    fun `M return null W createCanvas() {recycled bitmap}`() {
        // Given
        whenever(mockBitmap.isRecycled) doReturn true

        // When
        val canvas = testedWrapper.createCanvas(mockBitmap)

        // Then
        assertThat(canvas).isNull()
    }

    @Test
    fun `M return null W createCanvas() {immutable bitmap}`() {
        // Given
        whenever(mockBitmap.isRecycled) doReturn false
        whenever(mockBitmap.isMutable) doReturn false

        // When
        val canvas = testedWrapper.createCanvas(mockBitmap)

        // Then
        assertThat(canvas).isNull()
    }
}
