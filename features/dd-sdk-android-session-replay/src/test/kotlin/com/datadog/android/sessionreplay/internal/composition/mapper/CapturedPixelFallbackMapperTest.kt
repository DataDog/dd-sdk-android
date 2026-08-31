/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
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
    fun `M delegate to fallback W map { view will not draw }`(@StringForgery fakeScope: String) {
        // Given: a plain layout container - no background, no overridden onDraw - exactly what
        // ConstraintLayout, ScrollView, LinearLayout, etc. report when they have no background.
        val mockView = eligibleView()
        whenever(mockView.willNotDraw()).thenReturn(true)
        val mappingContext = mappingContext(ImagePrivacy.MASK_ALL, scope = fakeScope)

        // When
        testedMapper.map(mockView, mappingContext)

        // Then: never even considered for a placeholder or pixel capture.
        verify(mockFallbackMapper).map(mockView, mappingContext)
        verifyNoInteractions(mockViewBoundsResolver)
    }

    @Test
    fun `M delegate to fallback W map { edge-effect-only container with no background }`(
        @StringForgery fakeScope: String
    ) {
        // Given: a real ScrollView always reports willNotDraw() == false (its onDraw()/draw()
        // override exists solely for the overscroll edge-glow), even with no background of its
        // own - exactly the case that used to turn every scrollable screen into one giant
        // full-bounds placeholder covering all of its real children underneath.
        val mockScrollView: ScrollView = mock()
        whenever(mockScrollView.width).thenReturn(100)
        whenever(mockScrollView.height).thenReturn(100)
        val mappingContext = mappingContext(ImagePrivacy.MASK_ALL, scope = fakeScope)

        // When
        testedMapper.map(mockScrollView, mappingContext)

        // Then: never even considered for a placeholder or pixel capture.
        verify(mockFallbackMapper).map(mockScrollView, mappingContext)
        verifyNoInteractions(mockViewBoundsResolver)
    }

    @Test
    fun `M still consider for capture W map { edge-effect-only container with a real background }`(
        @StringForgery fakeScope: String
    ) {
        // Given: a ScrollView someone explicitly gave a (non-solid) background to - that
        // background is genuine, persistent content, so it must not be waved through as if it
        // were an ordinary backgroundless scrolling container.
        val mockScrollView: ScrollView = mock()
        val mockDrawable: Drawable = mock()
        whenever(mockScrollView.width).thenReturn(100)
        whenever(mockScrollView.height).thenReturn(100)
        whenever(mockScrollView.background).thenReturn(mockDrawable)
        whenever(mockScrollView.getGlobalVisibleRect(any())).thenAnswer { invocation ->
            invocation.getArgument<Rect>(0).set(0, 0, 100, 100)
            true
        }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockScrollView, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        val mappingContext = mappingContext(ImagePrivacy.MASK_ALL, scope = fakeScope)

        // When
        val result = testedMapper.map(mockScrollView, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then: still reaches the privacy gate - it has real content to protect.
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
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
        // fallbackMapper is consulted first (and here returns None, i.e. no solid background) -
        // only then does the privacy-gated placeholder path apply.
        verify(mockFallbackMapper).map(mockView, mappingContext)
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
        verify(mockFallbackMapper).map(mockView, mappingContext)
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
        verify(mockFallbackMapper).map(mockView, mappingContext)
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
        // A leaf's whole-view rasterization bakes in everything it paints - there's no separate
        // content left for the traversal to visit independently afterward.
        assertThat(result.pixelFallbackTerminal).isTrue()
    }

    @Test
    fun `M rasterize only the background and report non-terminal W map { ViewGroup, children, background }`(
        @StringForgery fakeScope: String
    ) {
        // Given: a themed container (Toolbar, CardView, ...) whose background alone can't reduce
        // to a solid color. Its children must still be captured by their own proper mapper -
        // rasterizing the whole view here would bake them into one opaque bitmap that
        // PixelFallbackSnapshotProcessor's text-detection safety net would then have to
        // blanket-mask, instead of letting a child TextView's real text show through masked (or
        // not) per TextAndInputPrivacy as usual.
        val mockViewGroup: ViewGroup = mock()
        val mockChild: View = mock()
        val mockDrawable: Drawable = mock()
        val mockBackgroundOnlyBitmap: Bitmap = mock()
        whenever(mockViewGroup.width).thenReturn(100)
        whenever(mockViewGroup.height).thenReturn(100)
        whenever(mockViewGroup.background).thenReturn(mockDrawable)
        whenever(mockViewGroup.childCount).thenReturn(1)
        whenever(mockViewGroup.getChildAt(0)).thenReturn(mockChild)
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
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockViewGroup, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        val registered = mutableListOf<PendingPixelCapture>()
        val testedMapperWithBackgroundRasterizer = CapturedPixelFallbackMapper(
            fallbackMapper = mockFallbackMapper,
            internalLogger = mockInternalLogger,
            viewBoundsResolver = mockViewBoundsResolver,
            viewRasterizer = ViewRasterizer { error("the whole-view rasterizer must not be used here") },
            backgroundRasterizer = ViewBackgroundRasterizer { mockBackgroundOnlyBitmap }
        )
        val mappingContext = mappingContext(
            ImagePrivacy.MASK_NONE,
            sink = PendingPixelCaptureSink { registered += it },
            scope = fakeScope
        )

        // When
        val result = testedMapperWithBackgroundRasterizer.map(mockViewGroup, mappingContext)
            as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.pixelFallbackTerminal).isFalse()
        val pixel = result.wireframes.single() as CapturedWireframe.Pixel
        assertThat(pixel.resource).isEqualTo(PixelResource.Unresolved)
        assertThat(registered).hasSize(1)
        assertThat(registered.single().bitmap).isSameAs(mockBackgroundOnlyBitmap)
        assertThat(registered.single().isTextFree).isTrue()
    }

    @Test
    fun `M rasterize the whole view and report terminal W map { ViewGroup, no children, background }`(
        @StringForgery fakeScope: String
    ) {
        // Given: a childless ViewGroup has nothing else for the traversal to visit independently
        // afterward, so it keeps the original whole-view rasterization.
        val mockViewGroup: ViewGroup = mock()
        val mockDrawable: Drawable = mock()
        whenever(mockViewGroup.width).thenReturn(100)
        whenever(mockViewGroup.height).thenReturn(100)
        whenever(mockViewGroup.background).thenReturn(mockDrawable)
        whenever(mockViewGroup.childCount).thenReturn(0)
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
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockViewGroup, 1f))
            .thenReturn(GlobalBounds(0, 0, 100, 100))
        val registered = mutableListOf<PendingPixelCapture>()
        val mappingContext = mappingContext(
            ImagePrivacy.MASK_NONE,
            sink = PendingPixelCaptureSink { registered += it },
            scope = fakeScope
        )

        // When
        val result = testedMapper.map(mockViewGroup, mappingContext) as CapturedViewMapperResult.Wireframes

        // Then
        assertThat(result.pixelFallbackTerminal).isTrue()
        assertThat(registered.single().bitmap).isSameAs(mockBitmap)
        assertThat(registered.single().isTextFree).isFalse()
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
