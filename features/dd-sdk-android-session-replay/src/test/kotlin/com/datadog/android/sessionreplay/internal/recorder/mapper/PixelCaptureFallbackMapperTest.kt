/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Rect
import android.view.View
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.PixelCapture
import com.datadog.android.sessionreplay.internal.recorder.aMockView
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.PixelCaptureCallback
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.max

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PixelCaptureFallbackMapperTest : LegacyBaseWireframeMapperTest() {

    @Mock
    lateinit var mockFallbackMapper: ViewWireframeMapper

    @Mock
    lateinit var mockPixelCaptureCallback: PixelCaptureCallback

    /** A distinct mock from [mockPixelCaptureCallback] — [PixelCapture] is the concrete type the
     * synchronous placeholder-decision fast path checks for via `as?`; see that test's doc. */
    @Mock
    lateinit var mockPixelCapture: PixelCapture

    private lateinit var fakeFallbackWireframes: List<MobileSegment.Wireframe>

    private lateinit var mockView: View

    /** [mockView]'s location, per [aMockView]'s own `getLocationOnScreen` stub. */
    private lateinit var viewLocationOnScreen: IntArray

    /** dp bounds of [mockView]'s full (not just visible) screen rect — what's always captured. */
    private lateinit var fakeGlobalBounds: GlobalBounds

    private lateinit var testedMapper: PixelCaptureFallbackMapper

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMappingContext = fakeMappingContext.copy(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            pixelCaptureCallback = mockPixelCaptureCallback
        )

        mockView = forge.aMockView()
        // Bounded, unlike aMockView()'s own unbounded anInt(min=...) stubs for these — this
        // class does arithmetic (location + size) to build Rects, which silently overflows
        // Int and produces a bogus (isEmpty) rect if the Forgery-picked values are too large.
        whenever(mockView.width).thenReturn(forge.anInt(min = 1, max = 2_000))
        whenever(mockView.height).thenReturn(forge.anInt(min = 1, max = 2_000))
        val fakeLocationOnScreen = intArrayOf(forge.anInt(min = 0, max = 500), forge.anInt(min = 0, max = 500))
        whenever(mockView.getLocationOnScreen(any())).thenAnswer {
            val location = it.arguments[0] as IntArray
            location[0] = fakeLocationOnScreen[0]
            location[1] = fakeLocationOnScreen[1]
            null
        }

        viewLocationOnScreen = IntArray(2)
        mockView.getLocationOnScreen(viewLocationOnScreen)

        // Fully on-screen by default — individual tests override with a smaller visible rect.
        stubVisibleRect(fullViewRect())

        fakeGlobalBounds = resolveExpectedBounds(fullViewRect(), fakeMappingContext.systemInformation.screenDensity)
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeMappingContext.systemInformation.screenDensity)
        ).thenReturn(fakeGlobalBounds)

        fakeFallbackWireframes = forge.aList { getForgery<MobileSegment.Wireframe.ShapeWireframe>() }
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        testedMapper = PixelCaptureFallbackMapper(
            fallbackMapper = mockFallbackMapper,
            viewIdentifierResolver = mockViewIdentifierResolver,
            colorStringFormatter = mockColorStringFormatter,
            viewBoundsResolver = mockViewBoundsResolver,
            drawableToColorMapper = mockDrawableToColorMapper
        )
    }

    /** [mockView]'s full screen rect — location-on-screen plus its full width/height. */
    private fun fullViewRect(): Rect = Rect(
        viewLocationOnScreen[0],
        viewLocationOnScreen[1],
        viewLocationOnScreen[0] + mockView.width,
        viewLocationOnScreen[1] + mockView.height
    )

    private fun stubVisibleRect(visibleRect: Rect) {
        doAnswer {
            val rect = it.arguments[0] as Rect
            rect.set(visibleRect)
            true
        }.whenever(mockView).getGlobalVisibleRect(any())
    }

    private fun resolveExpectedBounds(rect: Rect, screenDensity: Float): GlobalBounds {
        val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity
        return GlobalBounds(
            x = (rect.left * inverseDensity).toLong(),
            y = (rect.top * inverseDensity).toLong(),
            width = (rect.width() * inverseDensity).toLong(),
            height = (rect.height() * inverseDensity).toLong()
        )
    }

    /** Mirrors [PixelCaptureFallbackMapper]'s own max-overflow-per-edge clip computation. */
    private fun resolveExpectedClip(
        fullRect: Rect,
        visibleRect: Rect,
        screenDensity: Float
    ): MobileSegment.WireframeClip? {
        val inverseDensity = if (screenDensity == 0f) 1f else 1f / screenDensity
        val clipTop = max(0, visibleRect.top - fullRect.top)
        val clipBottom = max(0, fullRect.bottom - visibleRect.bottom)
        val clipLeft = max(0, visibleRect.left - fullRect.left)
        val clipRight = max(0, fullRect.right - visibleRect.right)
        if (clipTop == 0 && clipBottom == 0 && clipLeft == 0 && clipRight == 0) return null
        return MobileSegment.WireframeClip(
            top = (clipTop * inverseDensity).toLong(),
            bottom = (clipBottom * inverseDensity).toLong(),
            left = (clipLeft * inverseDensity).toLong(),
            right = (clipRight * inverseDensity).toLong()
        )
    }

    @Test
    fun `M delegate to fallbackMapper W map() {no pixelCaptureCallback}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(pixelCaptureCallback = null)
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCaptureCallback)
    }

    @Test
    fun `M delegate to fallbackMapper W map() {textAndInputPrivacy stricter than MASK_SENSITIVE_INPUTS}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL)
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCaptureCallback)
    }

    @Test
    fun `M register a pending capture W map() {imagePrivacy is MASK_ALL, deferred to content-aware check}`() {
        // Given — MASK_ALL no longer disables capture up front (PixelCaptureEligibility); content
        // known only once TextDetector's OCR pass completes decides whether this specific capture
        // needs a placeholder instead (see ImageContentDetector, CaptureOutcome).
        fakeMappingContext = fakeMappingContext.copy(imagePrivacy = ImagePrivacy.MASK_ALL)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).hasSize(1)
        val wireframe = result.first() as MobileSegment.Wireframe.ImageWireframe
        val expectedIsolationClipRect = Rect(0, 0, mockView.width, mockView.height)
        verify(mockPixelCaptureCallback).registerPendingCapture(
            nodeId = eq(wireframe.id),
            dpBounds = eq(fakeGlobalBounds),
            isolationView = eq(mockView),
            isolationClipRect = eq(expectedIsolationClipRect),
            wireframe = eq(wireframe),
            wireframeSlot = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            textAndInputPrivacy = eq(fakeMappingContext.textAndInputPrivacy),
            imagePrivacy = eq(ImagePrivacy.MASK_ALL)
        )
    }

    @Test
    fun `M delegate to fallbackMapper W map() {view has no visible area at all}`() {
        // Given — scrolled (or otherwise clipped) entirely out of view: nothing to capture.
        stubVisibleRect(Rect(0, 0, 0, 0))

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCaptureCallback)
    }

    @Test
    fun `M delegate to fallbackMapper W map() {getGlobalVisibleRect returns false}`() {
        // Given — e.g. detached from window.
        doAnswer { false }.whenever(mockView).getGlobalVisibleRect(any())

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCaptureCallback)
    }

    @Test
    fun `M return a placeholder W map() {imagePrivacy is MASK_LARGE_ONLY, view too large}`() {
        // Given — the view's full bounds (what's actually captured/uploaded) are PII-sized,
        // regardless of how much of it is currently visible. Rejected by PixelCaptureEligibility,
        // but that must not fall through to fallbackMapper the way any OTHER rejection reason
        // does — a custom, unmapped view's onDraw content is invisible to it, so it would return
        // nothing at all (blank space) rather than a real, labeled placeholder (mirrors
        // DefaultImageWireframeHelper's own MASK_LARGE_ONLY+large+contextual placeholder).
        fakeMappingContext = fakeMappingContext.copy(imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY)
        val fakeLargeBounds = fakeGlobalBounds.copy(
            width = com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP.toLong(),
            height = com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP.toLong()
        )
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeMappingContext.systemInformation.screenDensity)
        ).thenReturn(fakeLargeBounds)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).hasSize(1)
        val wireframe = result.first() as MobileSegment.Wireframe.PlaceholderWireframe
        assertThat(wireframe.x).isEqualTo(fakeLargeBounds.x)
        assertThat(wireframe.y).isEqualTo(fakeLargeBounds.y)
        assertThat(wireframe.width).isEqualTo(fakeLargeBounds.width)
        assertThat(wireframe.height).isEqualTo(fakeLargeBounds.height)
        assertThat(wireframe.label).isEqualTo(DefaultImageWireframeHelper.MASK_CONTEXTUAL_CONTENT_LABEL)
        verifyNoInteractions(mockPixelCaptureCallback)
        verifyNoInteractions(mockFallbackMapper)
    }

    @Test
    fun `M delegate to fallbackMapper W map() {view's full area far exceeds the screen's}`() {
        // Given — a view many times larger (in px) than the device's own screen, simulating a
        // long non-virtualized scrollable Compose Column with an enormous full height, rather
        // than a normal few-screens-tall page.
        fakeMappingContext = fakeMappingContext.copy(
            systemInformation = fakeMappingContext.systemInformation.copy(
                screenBounds = GlobalBounds(x = 0, y = 0, width = 400, height = 800),
                screenDensity = 1f
            )
        )
        whenever(mockView.width).thenReturn(2_000)
        whenever(mockView.height).thenReturn(2_000)
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCaptureCallback)
    }

    @Test
    fun `M register a pending capture of the view's full bounds W map() {eligible, fully visible}`() {
        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).hasSize(1)
        val wireframe = result.first() as MobileSegment.Wireframe.ImageWireframe
        assertThat(wireframe.x).isEqualTo(fakeGlobalBounds.x)
        assertThat(wireframe.y).isEqualTo(fakeGlobalBounds.y)
        assertThat(wireframe.width).isEqualTo(fakeGlobalBounds.width)
        assertThat(wireframe.height).isEqualTo(fakeGlobalBounds.height)
        assertThat(wireframe.clip).isNull()
        assertThat(wireframe.isEmpty).isTrue()

        val expectedIsolationClipRect = Rect(0, 0, mockView.width, mockView.height)
        verify(mockPixelCaptureCallback).registerPendingCapture(
            nodeId = eq(wireframe.id),
            dpBounds = eq(fakeGlobalBounds),
            isolationView = eq(mockView),
            isolationClipRect = eq(expectedIsolationClipRect),
            wireframe = eq(wireframe),
            wireframeSlot = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            textAndInputPrivacy = eq(fakeMappingContext.textAndInputPrivacy),
            imagePrivacy = eq(fakeMappingContext.imagePrivacy)
        )
    }

    @Test
    fun `M register a pending capture of the view's full bounds W map() {view partially visible}`() {
        // Given — e.g. a Compose host taller than its scrolling NestedScrollView ancestor: only
        // the top part of the view is currently on screen.
        val fullRect = fullViewRect()
        val visibleHeight = mockView.height / 2
        val partiallyVisibleRect = Rect(
            fullRect.left,
            fullRect.top,
            fullRect.right,
            fullRect.top + visibleHeight
        )
        stubVisibleRect(partiallyVisibleRect)
        val density = fakeMappingContext.systemInformation.screenDensity
        val expectedClip = resolveExpectedClip(fullRect, partiallyVisibleRect, density)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then — still captures the view's FULL bounds (same as the fully-visible case) so the
        // captured bitmap's size — and therefore PixelCapture's content cache — doesn't depend on
        // scroll position; only the reported clip changes.
        assertThat(result).hasSize(1)
        val wireframe = result.first() as MobileSegment.Wireframe.ImageWireframe
        assertThat(wireframe.x).isEqualTo(fakeGlobalBounds.x)
        assertThat(wireframe.y).isEqualTo(fakeGlobalBounds.y)
        assertThat(wireframe.width).isEqualTo(fakeGlobalBounds.width)
        assertThat(wireframe.height).isEqualTo(fakeGlobalBounds.height)
        assertThat(wireframe.clip).isEqualTo(expectedClip)

        val expectedIsolationClipRect = Rect(0, 0, mockView.width, mockView.height)
        verify(mockPixelCaptureCallback).registerPendingCapture(
            nodeId = eq(wireframe.id),
            dpBounds = eq(fakeGlobalBounds),
            isolationView = eq(mockView),
            isolationClipRect = eq(expectedIsolationClipRect),
            wireframe = eq(wireframe),
            wireframeSlot = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            textAndInputPrivacy = eq(fakeMappingContext.textAndInputPrivacy),
            imagePrivacy = eq(fakeMappingContext.imagePrivacy)
        )
    }

    @Test
    fun `M swap the returned wireframe W map() {eligible} and wireframeSlot replace() is called`() {
        // Given
        var capturedSlot: WireframeSlot? = null
        doAnswer {
            capturedSlot = it.getArgument(5)
            Unit
        }.whenever(mockPixelCaptureCallback).registerPendingCapture(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        val placeholder = MobileSegment.Wireframe.PlaceholderWireframe(
            id = (result.first() as MobileSegment.Wireframe.ImageWireframe).id,
            x = fakeGlobalBounds.x,
            y = fakeGlobalBounds.y,
            width = fakeGlobalBounds.width,
            height = fakeGlobalBounds.height,
            label = "Content"
        )
        capturedSlot?.replace(placeholder)

        // Then — the slot writes back into the exact same list the mapper returned
        assertThat(result).containsExactly(placeholder)
    }

    @Test
    fun `M return a placeholder directly W map() {a fresh placeholder decision is already cached}`() {
        // Given — see PixelCapture.hasFreshPlaceholderDecision's doc: a decision from a previous
        // cycle can only ever reach CompositionTreeBuilder's output if it's emitted synchronously
        // here, rather than registered as a pending capture the way every other outcome is
        fakeMappingContext = fakeMappingContext.copy(pixelCaptureCallback = mockPixelCapture)
        val expectedIsolationClipRect = Rect(0, 0, mockView.width, mockView.height)
        val isViewDirty = mockView.isDirty
        whenever(
            mockPixelCapture.hasFreshPlaceholderDecision(
                any(),
                eq(expectedIsolationClipRect.width()),
                eq(expectedIsolationClipRect.height()),
                eq(isViewDirty)
            )
        ).thenReturn(true)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).hasSize(1)
        val wireframe = result.first() as MobileSegment.Wireframe.PlaceholderWireframe
        assertThat(wireframe.x).isEqualTo(fakeGlobalBounds.x)
        assertThat(wireframe.y).isEqualTo(fakeGlobalBounds.y)
        assertThat(wireframe.width).isEqualTo(fakeGlobalBounds.width)
        assertThat(wireframe.height).isEqualTo(fakeGlobalBounds.height)
        assertThat(wireframe.label).isEqualTo(DefaultImageWireframeHelper.MASK_ALL_CONTENT_LABEL)
        verify(mockPixelCapture, never()).registerPendingCapture(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }
}
