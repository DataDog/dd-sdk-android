/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import android.os.Build
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WebPImageCompressionTest {
    private lateinit var testedImageCompression: WebPImageCompression

    @Mock
    lateinit var mockBitmap: Bitmap

    @BeforeEach
    fun setup() {
        testedImageCompression = WebPImageCompression()
    }

    // region compressBitmapToStream

    @Test
    @TestTargetApi(Build.VERSION_CODES.R)
    fun `M call with webp_lossy W compressBitmapToStream() { api is R or gt }`() {
        // Given
        val captor = argumentCaptor<Bitmap.CompressFormat>()

        // When
        testedImageCompression.compressBitmap(mockBitmap)

        verify(mockBitmap).compress(
            captor.capture(),
            any(),
            any()
        )

        // Then
        assertThat(captor.firstValue).isEqualTo(Bitmap.CompressFormat.WEBP_LOSSY)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `M call with webp W compressBitmapToStream() { api lt R }`() {
        // Given
        val captor = argumentCaptor<Bitmap.CompressFormat>()

        // When
        testedImageCompression.compressBitmap(mockBitmap)

        verify(mockBitmap).compress(
            captor.capture(),
            any(),
            any()
        )

        // Then
        @Suppress("DEPRECATION")
        assertThat(captor.firstValue).isEqualTo(Bitmap.CompressFormat.WEBP)
    }

    @Test
    fun `M return empty bytearray W compressBitmap { bitmap was already recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        val result = testedImageCompression.compressBitmap(mockBitmap)

        // Then
        assertThat(result).isEmpty()
    }

    // endregion
}
