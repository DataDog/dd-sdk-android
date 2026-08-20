/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCaptureSink
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedPixelFallbackMapperTest {

    private val mockFallbackMapper: CapturedViewMapper<View> = mock()
    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockInternalLogger: InternalLogger = mock()
    private val mockBitmap: Bitmap = mock()
    private val fakeViewRasterizer = ViewRasterizer { mockBitmap }
    private val testedMapper = CapturedPixelFallbackMapper(
        fallbackMapper = mockFallbackMapper,
        internalLogger = mockInternalLogger,
        viewBoundsResolver = mockViewBoundsResolver,
        viewRasterizer = fakeViewRasterizer
    )

    private fun mappingContext(
        imagePrivacy: ImagePrivacy,
        sink: PendingPixelCaptureSink = PendingPixelCaptureSink.NoOp,
        scope: String = "scope"
    ): CapturedMappingContext {
        val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(scope))
        val owner = factory.view(factory.window("window"), "owner")
        return CapturedMappingContext(
            identityFactory = factory,
            ownerIdentity = owner,
            screenDensity = 1f,
            imagePrivacy = imagePrivacy,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            pendingPixelCaptureSink = sink
        )
    }

    private fun eligibleView(width: Int = 100, height: Int = 100): View {
        val mockView: View = mock()
        val resources: Resources = mock()
        val displayMetrics = DisplayMetrics().apply {
            widthPixels = 1000
            heightPixels = 1000
        }
        whenever(mockView.width).thenReturn(width)
        whenever(mockView.height).thenReturn(height)
        whenever(mockView.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
        whenever(mockView.getGlobalVisibleRect(any())).thenAnswer { invocation ->
            val rect = invocation.getArgument<Rect>(0)
            rect.set(0, 0, width, height)
            true
        }
        return mockView
    }

    @Test
    fun `M delegate to fallback W map { zero width }`(@StringForgery fakeScope: String) {
        // Given
        val mockView = eligibleView(width = 0)
        val mappingContext = mappingContext(ImagePrivacy.MASK_NONE, scope = fakeScope)

        // When
        testedMapper.map(mockView, mappingContext)

        // Then
        verify(mockFallbackMapper).map(mockView, mappingContext)
    }

    @Test
    fun `M delegate to fallback W map { not globally visible }`(@StringForgery fakeScope: String) {
        // Given
        val mockView = eligibleView()
        whenever(mockView.getGlobalVisibleRect(any())).thenReturn(false)
        val mappingContext = mappingContext(ImagePrivacy.MASK_NONE, scope = fakeScope)

        // When
        testedMapper.map(mockView, mappingContext)

        // Then
        verify(mockFallbackMapper).map(mockView, mappingContext)
    }

    @Test
    fun `M emit a placeholder without drawing W map { imagePrivacy is MASK_ALL }`(@StringForgery fakeScope: String) {
        // Given
        val mockView = eligibleView()
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        val mappingContext = mappingContext(ImagePrivacy.MASK_ALL, scope = fakeScope)

        // When
        val result = testedMapper.map(mockView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes).hasSize(1)
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        verifyNoInteractions(mockFallbackMapper)
    }

    @Test
    fun `M emit a placeholder without drawing W map { MASK_LARGE_ONLY and large bounds }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val mockView = eligibleView()
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, 1f))
            .thenReturn(GlobalBounds(0, 0, 200, 50))
        val mappingContext = mappingContext(ImagePrivacy.MASK_LARGE_ONLY, scope = fakeScope)

        // When
        val result = testedMapper.map(mockView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        verifyNoInteractions(mockFallbackMapper)
    }

    @Test
    fun `M register a pending capture and emit an unresolved pixel W map { MASK_LARGE_ONLY and small bounds }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val mockView = eligibleView()
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, 1f))
            .thenReturn(GlobalBounds(0, 0, 50, 50))
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            ImagePrivacy.MASK_LARGE_ONLY,
            sink = PendingPixelCaptureSink { registered += it },
            scope = fakeScope
        )

        // When
        val result = testedMapper.map(mockView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        val pixel = result.wireframes.single() as CapturedWireframe.Pixel
        assertThat(pixel.resource).isEqualTo(PixelResource.Unresolved)
        assertThat(registered).hasSize(1)
        assertThat(registered.single().wireframeIdentity).isEqualTo(pixel.identity)
        assertThat(registered.single().ownerIdentity).isEqualTo(mappingContext.ownerIdentity)
        assertThat(registered.single().bitmap).isSameAs(mockBitmap)
        verifyNoInteractions(mockFallbackMapper)
    }

    @Test
    fun `M delegate to fallback W map { rasterization fails }`(@StringForgery fakeScope: String) {
        // Given
        val mockView = eligibleView()
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        val testedMapperWithFailingRasterizer = CapturedPixelFallbackMapper(
            fallbackMapper = mockFallbackMapper,
            internalLogger = mockInternalLogger,
            viewBoundsResolver = mockViewBoundsResolver,
            viewRasterizer = ViewRasterizer { null }
        )
        val mappingContext = mappingContext(ImagePrivacy.MASK_NONE, scope = fakeScope)

        // When
        testedMapperWithFailingRasterizer.map(mockView, mappingContext)

        // Then
        verify(mockFallbackMapper).map(mockView, mappingContext)
    }

    @Test
    fun `M register a pending capture and emit an unresolved pixel W map { MASK_NONE }`(
        @StringForgery fakeScope: String
    ) {
        // Given
        val mockView = eligibleView()
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            ImagePrivacy.MASK_NONE,
            sink = PendingPixelCaptureSink { registered += it },
            scope = fakeScope
        )

        // When
        val result = testedMapper.map(mockView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat((result.wireframes.single() as CapturedWireframe.Pixel).resource)
            .isEqualTo(PixelResource.Unresolved)
        assertThat(registered).hasSize(1)
    }

    @Test
    fun `M delegate to fallback W map { view area exceeds capturable limit }`(@StringForgery fakeScope: String) {
        // Given: 1000x1000 screen, 8x cap = 8,000,000px - this view is 12,000,000px.
        val mockView = eligibleView(width = 4000, height = 3000)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, 1f))
            .thenReturn(GlobalBounds(0, 0, 4000, 3000))
        val mappingContext = mappingContext(ImagePrivacy.MASK_NONE, scope = fakeScope)

        // When
        testedMapper.map(mockView, mappingContext)

        // Then
        verify(mockFallbackMapper).map(mockView, mappingContext)
    }

    @Test
    fun `M delegate to fallback W map { view is a hardware surface }`(@StringForgery fakeScope: String) {
        // Given
        val mockSurfaceView: SurfaceView = mock()
        whenever(mockSurfaceView.width).thenReturn(100)
        whenever(mockSurfaceView.height).thenReturn(100)
        val resources: Resources = mock()
        val displayMetrics = DisplayMetrics().apply {
            widthPixels = 1000
            heightPixels = 1000
        }
        whenever(mockSurfaceView.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
        whenever(mockSurfaceView.getGlobalVisibleRect(any())).thenAnswer { invocation ->
            invocation.getArgument<Rect>(0).set(0, 0, 100, 100)
            true
        }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockSurfaceView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        val mappingContext = mappingContext(ImagePrivacy.MASK_NONE, scope = fakeScope)

        // When
        testedMapper.map(mockSurfaceView, mappingContext)

        // Then
        verify(mockFallbackMapper).map(mockSurfaceView, mappingContext)
    }

    @Test
    fun `M delegate to fallback W map { subtree contains a hardware surface }`(@StringForgery fakeScope: String) {
        // Given
        val mockViewGroup: ViewGroup = mock()
        val mockChildSurfaceView: SurfaceView = mock()
        whenever(mockViewGroup.width).thenReturn(100)
        whenever(mockViewGroup.height).thenReturn(100)
        val resources: Resources = mock()
        val displayMetrics = DisplayMetrics().apply {
            widthPixels = 1000
            heightPixels = 1000
        }
        whenever(mockViewGroup.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)
        whenever(mockViewGroup.getGlobalVisibleRect(any())).thenAnswer { invocation ->
            invocation.getArgument<Rect>(0).set(0, 0, 100, 100)
            true
        }
        whenever(mockViewGroup.childCount).thenReturn(1)
        whenever(mockViewGroup.getChildAt(0)).thenReturn(mockChildSurfaceView)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockViewGroup, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        val mappingContext = mappingContext(ImagePrivacy.MASK_NONE, scope = fakeScope)

        // When
        testedMapper.map(mockViewGroup, mappingContext)

        // Then
        verify(mockFallbackMapper).map(mockViewGroup, mappingContext)
    }
}
