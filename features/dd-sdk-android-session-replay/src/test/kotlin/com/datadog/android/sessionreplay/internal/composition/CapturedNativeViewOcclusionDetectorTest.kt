/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.GlobalBounds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Random
import java.util.concurrent.atomic.AtomicLong

internal class CapturedNativeViewOcclusionDetectorTest {
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
    private val fakeWindow = fakeFactory.window("window")
    private val fakeContext = CaptureGenerationContext(1L, 0L, 100L, CaptureTimeProvider { 0L })
    private val testedDetector = CapturedNativeViewOcclusionDetector()
    private val fakeBounds = GlobalBounds(0, 0, 100, 100)

    @Test
    fun `M cull covered visuals across branches W buildWindow`() {
        // Given: the covering visual is a descendant of a different sibling container.
        val fakeRoot = view("root", -1, GlobalBounds(0, 0, 200, 200))
        val fakeBack = view("back", 0)
        val fakeText = view("text", 1)
        val fakeFront = view("front", 0)
        val fakeCover = view("cover", 3, opaque = true)
        val fakeViews = listOf(fakeRoot, fakeBack, fakeText, fakeFront, fakeCover)
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val result = testedBuilder.buildWindow(CapturedNativeWindow(1f, fakeViews), fakeContext)!!

        // Then: skip mapping, retain the hierarchy and the covering visual.
        verify(fakeText.mapping, never()).map(any())
        assertThat(result.layers).hasSize(fakeViews.size)
        assertThat(result.wireframes.map { it.identity }).containsExactly(
            fakeFactory.shapeWireframe(fakeRoot.identity),
            fakeFactory.shapeWireframe(fakeCover.identity)
        )
        assertThat(DefaultCapturedTreeValidator().validate(snapshot(result))).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M combine opaque rectangles W occludedViews`() {
        // Given: neither covering view contains the lower visual on its own.
        val fakeViews = listOf(
            view("root", -1),
            view("target", 0),
            view("left", 0, GlobalBounds(0, 0, 50, 100), opaque = true),
            view("right", 0, GlobalBounds(50, 0, 50, 100), opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isTrue()
        assertThat(result[2]).isFalse()
        assertThat(result[3]).isFalse()
    }

    @Test
    fun `M preserve visible gap W occludedViews { normalized replay bounds hide a pixel gap }`() {
        // Given: normalized replay bounds coincide, but the actual cover leaves one screen pixel.
        val fakeTarget = view("target", 0, GlobalBounds(0, 0, 101, 100)).copy(
            bounds = CapturedBounds(0, 0, 33, 33)
        )
        val fakeCover = view("cover", 0, GlobalBounds(0, 0, 100, 100), opaque = true).copy(
            bounds = fakeTarget.bounds
        )

        // When
        val result = testedDetector.occludedViews(
            CapturedNativeWindow(3f, listOf(view("root", -1, GlobalBounds(0, 0, 200, 200)), fakeTarget, fakeCover)),
            fakeContext
        )

        // Then
        assertThat(result[1]).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain uncovered overflow W occludedViews { branch is clipped to its own bounds }`(
        fakeClipBranch: Boolean
    ) {
        // Given: a cover contains the group, but not its overflowing child.
        val fakeViews = listOf(
            view("root", -1, GlobalBounds(0, 0, 200, 200)),
            view("back", 0).copy(clipToBounds = fakeClipBranch),
            view("overflow", 1, GlobalBounds(90, 0, 50, 50)).copy(clipToBounds = false),
            view("cover", 0, opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then: clipping confines the visual only when that parent actually clips children.
        assertThat(result[1]).isTrue()
        assertThat(result[2]).isEqualTo(fakeClipBranch)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M apply inherited rectangular clips W occludedViews`(fakePaddingClip: Boolean) {
        // Given: the opaque child's apparent full rectangle is clipped to half of it by its ancestor.
        val fakeClip = GlobalBounds(0, 0, 50, 100)
        val fakeParent = view("front", 0).copy(
            pixelClip = fakeClip.takeUnless { fakePaddingClip },
            pixelChildPaddingClip = fakeClip.takeIf { fakePaddingClip }
        )
        val fakeViews = listOf(
            view("root", -1),
            view("target", 0),
            fakeParent,
            view("cover", 2, opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isFalse()
    }

    @Test
    fun `M preserve all visible siblings W occludedViews { opaque cover has a restrictive clip }`() {
        // Given
        val fakeCover = view("cover", 0, opaque = true).copy(pixelClip = GlobalBounds(0, 0, 50, 100))

        // When
        val result = testedDetector.occludedViews(
            CapturedNativeWindow(1f, listOf(view("root", -1), view("target", 0), fakeCover)),
            fakeContext
        )

        // Then
        assertThat(result[1]).isFalse()
    }

    @Test
    fun `M reject inherited translucent coverage W occludedViews`() {
        // Given: the child itself is opaque, but its whole layer is blended by the parent.
        val fakeViews = listOf(
            view("root", -1),
            view("target", 0),
            view("front", 0).copy(alpha = 0.5),
            view("cover", 2, opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isFalse()
    }

    @Test
    fun `M retain custom mapper output W buildWindow { output extends outside the view bounds }`() {
        // Given
        val fakeCustom = view("custom", 0, bounded = false)
        val fakeCover = view("cover", 0, opaque = true)
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val result = testedBuilder.buildWindow(
            CapturedNativeWindow(1f, listOf(view("root", -1), fakeCustom, fakeCover)),
            fakeContext
        )!!

        // Then: no bounds contract is inferred from arbitrary custom results.
        verify(fakeCustom.mapping).map(any())
        assertThat(result.wireframes.map { it.identity }).contains(fakeFactory.shapeWireframe(fakeCustom.identity))
    }

    @Test
    fun `M retain uncertain geometry and its descendants W occludedViews`() {
        // Given
        val fakeViews = listOf(
            view("root", -1),
            view("uncertain", 0).copy(isGeometryReliable = false),
            view("descendant", 1),
            view("cover", 0, opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isFalse()
        assertThat(result[2]).isFalse()
    }

    @Test
    fun `M disable coverage W occludedViews { child drawing order is unreliable }`() {
        // Given
        val fakeViews = listOf(view("root", -1), view("target", 0), view("cover", 0, opaque = true))

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews, false), fakeContext)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M preserve missing pixel geometry W occludedViews`() {
        // Given: density-normalized bounds are not a safe replacement.
        val fakeViews = listOf(
            view("root", -1).copy(pixelBounds = null),
            view("target", 0),
            view("cover", 0, opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isFalse()
    }

    @Test
    fun `M retain uncovered content W occludedViews { contributor limit is reached }`() {
        // Given
        val testedDetector = CapturedNativeViewOcclusionDetector(maxCoveringBounds = 1)
        val fakeViews = listOf(
            view("root", -1),
            view("target", 0),
            view("left", 0, GlobalBounds(0, 0, 50, 100), opaque = true),
            view("right", 0, GlobalBounds(50, 0, 50, 100), opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then: the cap may miss a union, but never treats an unrecorded rectangle as coverage.
        assertThat(result[1]).isFalse()
    }

    @Test
    fun `M retain uncovered content W occludedViews { subtraction exceeds fragment limit }`() {
        // Given: a center strip splits the query into two pieces.
        val testedDetector = CapturedNativeViewOcclusionDetector(maxQueryFragments = 1)
        val fakeViews = listOf(
            view("root", -1),
            view("target", 0),
            view("strip", 0, GlobalBounds(40, 0, 20, 100), opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isFalse()
    }

    @Test
    fun `M discard expired analysis W buildWindow`() {
        // Given
        val fakeClock = AtomicLong()
        val fakeGeneration = CaptureGenerationContext(1L, 0L, 5L, CaptureTimeProvider { fakeClock.incrementAndGet() })
        val fakeViews = listOf(view("root", -1)) +
            List(20) { index -> view(index.toString(), 0, opaque = index == 19) }
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val result = testedBuilder.buildWindow(CapturedNativeWindow(1f, fakeViews), fakeGeneration)

        // Then
        assertThat(result).isNull()
        fakeViews.forEach { verify(it.mapping, never()).map(any()) }
    }

    @Test
    fun `M never remove a visible pixel W occludedViews { randomized rectangle unions }`() {
        // Given: exact unit-pixel coverage provides an independent oracle for overlapping unions and gaps.
        val fakeRandom = Random(8613L)
        repeat(500) {
            val fakeCovers = List(12) {
                val left = fakeRandom.nextInt(12) - 1
                val top = fakeRandom.nextInt(12) - 1
                GlobalBounds(
                    left.toLong(),
                    top.toLong(),
                    (1 + fakeRandom.nextInt(8)).toLong(),
                    (
                        1 +
                            fakeRandom.nextInt(8)
                        ).toLong()
                )
            }
            val fakeViews = listOf(
                view("root", -1, GlobalBounds(-10, -10, 40, 40)),
                view("target", 0, GlobalBounds(0, 0, 10, 10))
            ) + fakeCovers.mapIndexed { index, bounds -> view(index.toString(), 0, bounds, opaque = true) }
            val fullyCovered = (0 until 10).all { x ->
                (0 until 10).all { y ->
                    fakeCovers.any { x >= it.x && x < it.x + it.width && y >= it.y && y < it.y + it.height }
                }
            }

            // When
            val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

            // Then
            if (result[1]) assertThat(fullyCovered).isTrue()
        }
    }

    private fun view(
        fakeId: String,
        fakeParent: Int,
        fakePixels: GlobalBounds = fakeBounds,
        opaque: Boolean = false,
        bounded: Boolean = true
    ): CapturedNativeView {
        val fakeIdentity = if (fakeParent < 0) fakeWindow else fakeFactory.view(fakeWindow, fakeId)
        val mockMapping: CapturedViewData = mock()
        whenever(mockMapping.isOcclusionBounded).thenReturn(bounded)
        whenever(mockMapping.hasOpaqueBackground).thenReturn(opaque)
        val fakeWireframe = CapturedWireframe.Shape(
            fakeFactory.shapeWireframe(fakeIdentity),
            CapturedBounds(fakePixels.x, fakePixels.y, fakePixels.width, fakePixels.height)
        )
        whenever(mockMapping.map(any())).thenReturn(CapturedViewMapperResult.Wireframes(listOf(fakeWireframe)))
        return CapturedNativeView(
            fakeIdentity,
            fakeParent,
            fakeWireframe.bounds,
            1.0,
            mockMapping,
            true,
            null,
            null,
            pixelBounds = fakePixels,
            isGeometryReliable = true
        )
    }

    private fun snapshot(fakeWindow: CapturedWindow): CapturedFullSnapshot = CapturedFullSnapshot(
        1L,
        fakeFactory.scope,
        CapturedLayer(
            fakeFactory.screenRoot(),
            CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
            fakeWindow.rootLayer.bounds,
            listOf(CapturedChild.Layer(fakeWindow.rootLayer.identity))
        ),
        fakeWindow.layers,
        fakeWindow.wireframes
    )
}
