/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class LayerDrawableExtTest {

    @Mock
    lateinit var mockLayerDrawable: LayerDrawable

    @Mock
    lateinit var mockBitmapDrawable: BitmapDrawable

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return drawable for index W safeGetDrawable()`() {
        // Given
        whenever(mockLayerDrawable.numberOfLayers)
            .thenReturn(1)
        whenever(mockLayerDrawable.getDrawable(0))
            .thenReturn(mockBitmapDrawable)

        // When
        val drawable = mockLayerDrawable.safeGetDrawable(
            index = 0
        )

        // Then
        assertThat(drawable).isEqualTo(mockBitmapDrawable)
    }

    @Test
    fun `M return null W safeGetDrawable() { index below 0 }`() {
        // Given
        whenever(mockLayerDrawable.numberOfLayers)
            .thenReturn(0)
        whenever(mockLayerDrawable.getDrawable(any()))
            .thenThrow(IndexOutOfBoundsException())

        // When
        val drawable = mockLayerDrawable.safeGetDrawable(
            index = -1,
            mockInternalLogger
        )

        // Then
        val captor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            level = any(),
            target = any(),
            captor.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        assertThat(captor.firstValue.invoke())
            .startsWith("Failed to get drawable from layer - invalid index passed")
        assertThat(drawable).isNull()
    }

    @Test
    fun `M return null W safeGetDrawable() { index above number of layers }`() {
        // Given
        whenever(mockLayerDrawable.numberOfLayers)
            .thenReturn(0)
        whenever(mockLayerDrawable.getDrawable(any()))
            .thenThrow(IndexOutOfBoundsException())

        // When
        val drawable = mockLayerDrawable.safeGetDrawable(
            index = 1,
            mockInternalLogger
        )

        // Then
        val captor = argumentCaptor<() -> String>()
        verify(mockInternalLogger).log(
            level = any(),
            target = any(),
            captor.capture(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        assertThat(captor.firstValue.invoke())
            .startsWith("Failed to get drawable from layer - invalid index passed")
        assertThat(drawable).isNull()
    }
}
