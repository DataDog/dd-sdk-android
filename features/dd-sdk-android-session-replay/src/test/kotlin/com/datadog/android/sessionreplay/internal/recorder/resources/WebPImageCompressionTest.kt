/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.Bitmap
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.system.BuildSdkVersionProvider
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WebPImageCompressionTest {
    private lateinit var testedImageCompression: WebPImageCompression

    @Mock
    lateinit var mockBitmap: Bitmap

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var logger: InternalLogger

    @BeforeEach
    fun setup() {
        testedImageCompression = WebPImageCompression(logger, mockBuildSdkVersionProvider)
    }

    // region compressBitmapToStream

    @Test
    fun `M call with webp_lossy W compressBitmapToStream() { api is R+ }`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastR) doReturn true
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
    fun `M call with webp W compressBitmapToStream() { api below R }`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastR) doReturn false
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
