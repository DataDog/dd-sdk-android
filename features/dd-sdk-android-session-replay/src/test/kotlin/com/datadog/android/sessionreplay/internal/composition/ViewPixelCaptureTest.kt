/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.ViewRasterizer
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ViewPixelCaptureTest {
    private val fakePending = mutableListOf<PendingPixelCapture>()
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
    private val fakeContext = CapturedMappingContext(
        fakeFactory,
        fakeFactory.view(fakeFactory.window("window"), "view"),
        1f,
        bounds = CapturedBounds(0, 0, 100, 50),
        imagePrivacy = ImagePrivacy.MASK_NONE,
        pendingPixelCaptureSink = PendingPixelCaptureSink { fakePending += it }
    )

    @Mock
    lateinit var mockRasterizer: ViewRasterizer

    @Test
    fun `M register the rasterized view for text masking W capture() { unrestricted content }`() {
        // Given
        val stubView = view()
        val mockBitmap: Bitmap = mock()
        whenever(mockRasterizer.rasterize(any())).thenReturn(mockBitmap)

        // When
        val result = ViewPixelCapture(mock(), rasterizer = mockRasterizer).capture(stubView, fakeContext)
            as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.isTerminal).isTrue()
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.Pixel::class.java)
        assertThat(fakePending.single().bitmap).isSameAs(mockBitmap)
        assertThat(fakePending.single().isTextFree).isFalse()
    }

    @Test
    fun `M leave the view to the fallback W capture() { hidden or more strictly masked descendant }`(
        @BoolForgery fakeHidden: Boolean
    ) {
        // Given
        val stubParent: ViewGroup = mock()
        val stubResources = resources()
        whenever(stubParent.resources).thenReturn(stubResources)
        whenever(stubParent.width).thenReturn(100)
        whenever(stubParent.height).thenReturn(50)
        val stubChild = view()
        whenever(stubParent.childCount).thenReturn(1)
        whenever(stubParent.getChildAt(0)).thenReturn(stubChild)
        if (fakeHidden) {
            whenever(stubChild.getTag(R.id.datadog_hidden)).thenReturn(true)
        } else {
            whenever(stubChild.getTag(R.id.datadog_image_privacy)).thenReturn(ImagePrivacy.MASK_ALL.name)
        }

        // When
        val result = ViewPixelCapture(mock(), rasterizer = mockRasterizer).capture(stubParent, fakeContext)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockRasterizer)
        assertThat(fakePending).isEmpty()
    }

    private fun view(): View {
        val stubView: View = mock()
        val stubResources = resources()
        whenever(stubView.resources).thenReturn(stubResources)
        whenever(stubView.width).thenReturn(100)
        whenever(stubView.height).thenReturn(50)
        return stubView
    }

    private fun resources(): Resources {
        val stubResources: Resources = mock()
        whenever(stubResources.displayMetrics).thenReturn(
            DisplayMetrics().apply {
                widthPixels = 1000
                heightPixels = 1000
            }
        )
        return stubResources
    }
}
