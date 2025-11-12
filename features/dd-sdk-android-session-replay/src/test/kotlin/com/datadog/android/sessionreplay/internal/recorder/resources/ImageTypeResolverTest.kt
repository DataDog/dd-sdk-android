/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import com.datadog.android.internal.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ImageTypeResolverTest {
    private lateinit var testedTypeResolver: ImageTypeResolver

    @Mock
    lateinit var mockBitmapDrawable: BitmapDrawable

    @Mock
    lateinit var mockGradientDrawable: GradientDrawable

    @BeforeEach
    fun `set up`() {
        testedTypeResolver = ImageTypeResolver()
    }

    @Test
    fun `M return true W isDrawablePII() { is not gradientDrawable and width above dimensions }`(
        @IntForgery(min = IMAGE_DIMEN_CONSIDERED_PII_IN_DP) fakeWidth: Int,
        @IntForgery(min = 0, max = IMAGE_DIMEN_CONSIDERED_PII_IN_DP) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmapDrawable.intrinsicWidth).thenReturn(fakeWidth)
        whenever(mockBitmapDrawable.intrinsicHeight).thenReturn(fakeHeight)

        // When
        val result = testedTypeResolver.isDrawablePII(mockBitmapDrawable, density = 1f)

        // Then
        assertThat(result).isTrue
    }

    @Test
    fun `M return true W isDrawablePII() { is not gradientDrawable and height is above dimensions }`(
        @IntForgery(min = 0, max = IMAGE_DIMEN_CONSIDERED_PII_IN_DP) fakeWidth: Int,
        @IntForgery(min = IMAGE_DIMEN_CONSIDERED_PII_IN_DP) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmapDrawable.intrinsicWidth).thenReturn(fakeWidth)
        whenever(mockBitmapDrawable.intrinsicHeight).thenReturn(fakeHeight)

        // When
        val result = testedTypeResolver.isDrawablePII(mockBitmapDrawable, density = 1f)

        // Then
        assertThat(result).isTrue
    }

    @Test
    fun `M return false W isDrawablePII() { is gradientDrawable and above dimensions }`(
        @IntForgery(min = IMAGE_DIMEN_CONSIDERED_PII_IN_DP) fakeWidth: Int,
        @IntForgery(min = IMAGE_DIMEN_CONSIDERED_PII_IN_DP) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmapDrawable.intrinsicWidth).thenReturn(fakeWidth)
        whenever(mockBitmapDrawable.intrinsicHeight).thenReturn(fakeHeight)

        // When
        val result = testedTypeResolver.isDrawablePII(mockGradientDrawable, density = 1f)

        // Then
        assertThat(result).isFalse
    }

    @Test
    fun `M return false W isDrawablePII() { not gradientDrawable and dimensions are below PII limit }`(
        @IntForgery(min = 0, max = IMAGE_DIMEN_CONSIDERED_PII_IN_DP) fakeWidth: Int,
        @IntForgery(min = 0, max = IMAGE_DIMEN_CONSIDERED_PII_IN_DP) fakeHeight: Int
    ) {
        // Given
        whenever(mockBitmapDrawable.intrinsicWidth).thenReturn(fakeWidth)
        whenever(mockBitmapDrawable.intrinsicHeight).thenReturn(fakeHeight)

        // When
        val result = testedTypeResolver.isDrawablePII(mockBitmapDrawable, density = 1f)

        // Then
        assertThat(result).isFalse
    }
}
