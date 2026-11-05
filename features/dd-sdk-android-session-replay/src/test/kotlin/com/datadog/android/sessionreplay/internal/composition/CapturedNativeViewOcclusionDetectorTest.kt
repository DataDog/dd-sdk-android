/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicLong

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedNativeViewOcclusionDetectorTest {
    private lateinit var fakeFactory: DefaultCapturedIdentityFactory
    private lateinit var fakeWindow: CapturedIdentity
    private val fakeContext = CaptureGenerationContext(1L, 0L, 100L, CaptureTimeProvider { 0L })
    private val testedDetector = CapturedNativeViewOcclusionDetector()
    private val fakeBounds = GlobalBounds(0, 0, 100, 100)

    @BeforeEach
    fun setUp(@StringForgery fakeScopeValue: String, @StringForgery fakeWindowName: String) {
        fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScopeValue))
        fakeWindow = fakeFactory.window(fakeWindowName)
    }

    @Test
    fun `M cull covered visuals across branches W buildWindow`() {
        // Given
        val fakeRoot = view("root", -1, GlobalBounds(0, 0, 200, 200))
        val fakeBack = view("back", 0)
        val fakeText = view("text", 1)
        val fakeFront = view("front", 0)
        val fakeCover = view("cover", 3, opaque = true)
        val fakeViews = listOf(fakeRoot, fakeBack, fakeText, fakeFront, fakeCover)
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val result = testedBuilder.buildWindow(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        val window = checkNotNull(result) { "Expected the copied window to be built" }
        verify(fakeText.viewData, never()).map(any())
        assertThat(window.layers).hasSize(fakeViews.size)
        assertThat(window.wireframes.map { it.identity }).containsExactly(
            fakeFactory.shapeWireframe(fakeRoot.identity),
            fakeFactory.shapeWireframe(fakeCover.identity)
        )
        assertThat(DefaultCapturedTreeValidator().validate(snapshot(window))).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M combine opaque rectangles W occludedViews`() {
        // Given
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
        // Given
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
    @CsvSource("2, false", "2, true", "3, false", "3, true")
    fun `M retain replay-visible content W buildWindow { pixel coverage disagrees after normalization }`(
        fakeDensity: Float,
        fakeVertical: Boolean
    ) {
        // Given
        val fakePixelOrigin = fakeDensity.toLong()
        val fakeTargetBounds = if (fakeVertical) {
            GlobalBounds(0, fakePixelOrigin, 12, fakePixelOrigin)
        } else {
            GlobalBounds(fakePixelOrigin, 0, fakePixelOrigin, 12)
        }
        val fakeCoverBounds = if (fakeVertical) {
            GlobalBounds(0, fakePixelOrigin - 1, 12, fakePixelOrigin + 1)
        } else {
            GlobalBounds(fakePixelOrigin - 1, 0, fakePixelOrigin + 1, 12)
        }
        val fakeTarget = view("target", 1, fakeTargetBounds, density = fakeDensity)
        val fakeViews = listOf(
            view("root", -1, GlobalBounds(0, 0, 30, 30), density = fakeDensity),
            view("back", 0, density = fakeDensity),
            fakeTarget,
            view("front", 0, density = fakeDensity),
            view("cover", 3, fakeCoverBounds, opaque = true, density = fakeDensity)
        )

        // When
        val result = CapturedNativeSnapshotBuilder().buildWindow(
            CapturedNativeWindow(fakeDensity, fakeViews),
            fakeContext
        )

        // Then
        val window = checkNotNull(result) { "Expected the copied window to be built" }
        verify(fakeTarget.viewData).map(isA())
        assertThat(window.wireframes.map { it.identity }).contains(fakeFactory.shapeWireframe(fakeTarget.identity))
        assertThat(DefaultCapturedTreeValidator().validate(snapshot(window))).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M retain replay-visible gap W occludedViews { pixel union covers the target }`() {
        // Given
        val fakeViews = listOf(
            view("root", -1, density = 2f),
            view("target", 0, GlobalBounds(2, 0, 4, 10), density = 2f),
            view("left", 0, GlobalBounds(1, 0, 3, 10), opaque = true, density = 2f),
            view("right", 0, GlobalBounds(4, 0, 2, 10), opaque = true, density = 2f)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(2f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain replay-visible content W occludedViews { inherited clip rounds differently }`(
        fakePaddingClip: Boolean
    ) {
        // Given
        val fakePixelClip = GlobalBounds(-1, 0, 4, 4)
        val fakeReplayClip = CapturedBounds(0, 0, 1, 2)
        val fakeFront = view("front", 0, GlobalBounds(-4, 0, 8, 4), density = 2f).copy(
            pixelClip = fakePixelClip.takeUnless { fakePaddingClip },
            explicitClip = fakeReplayClip.takeUnless { fakePaddingClip },
            pixelChildPaddingClip = fakePixelClip.takeIf { fakePaddingClip },
            childPaddingClip = fakeReplayClip.takeIf { fakePaddingClip }
        )
        val fakeViews = listOf(
            view("root", -1, GlobalBounds(-20, -20, 40, 40), density = 2f),
            view("target", 0, GlobalBounds(-1, 0, 4, 4), density = 2f),
            fakeFront,
            view("cover", 2, GlobalBounds(-4, 0, 8, 4), opaque = true, density = 2f)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(2f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain uncovered overflow W occludedViews { branch is clipped to its own bounds }`(
        fakeClipBranch: Boolean
    ) {
        // Given
        val fakeViews = listOf(
            view("root", -1, GlobalBounds(0, 0, 200, 200)),
            view("back", 0).copy(clipToBounds = fakeClipBranch),
            view("overflow", 1, GlobalBounds(90, 0, 50, 50)).copy(clipToBounds = false),
            view("cover", 0, opaque = true)
        )

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        assertThat(result[1]).isTrue()
        assertThat(result[2]).isEqualTo(fakeClipBranch)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M apply inherited rectangular clips W occludedViews`(fakePaddingClip: Boolean) {
        // Given
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
        // Given
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
        )

        // Then
        val window = checkNotNull(result) { "Expected the copied window to be built" }
        verify(fakeCustom.viewData).map(isA())
        assertThat(window.wireframes.map { it.identity }).contains(fakeFactory.shapeWireframe(fakeCustom.identity))
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
        // Given
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

        // Then
        assertThat(result[1]).isFalse()
    }

    @Test
    fun `M retain uncovered content W occludedViews { subtraction exceeds fragment limit }`() {
        // Given
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
        fakeViews.forEach { verify(it.viewData, never()).map(any()) }
    }

    @RepeatedTest(500)
    fun `M never remove a visible pixel W occludedViews { randomized rectangle unions }`(forge: Forge) {
        // Given
        val fakeCovers = List(12) {
            val fakeLeft = forge.anInt(min = -1, max = 10)
            val fakeTop = forge.anInt(min = -1, max = 10)
            GlobalBounds(
                fakeLeft.toLong(),
                fakeTop.toLong(),
                (forge.anInt(min = 1, max = 8)).toLong(),
                forge.anInt(min = 1, max = 8).toLong()
            )
        }
        val fakeViews = listOf(
            view("root", -1, GlobalBounds(-10, -10, 40, 40)),
            view("target", 0, GlobalBounds(0, 0, 10, 10))
        ) + fakeCovers.mapIndexed { index, bounds -> view(index.toString(), 0, bounds, opaque = true) }
        val fakeFullyCovered = (0 until 10).all { x ->
            (0 until 10).all { y ->
                fakeCovers.any { x >= it.x && x < it.x + it.width && y >= it.y && y < it.y + it.height }
            }
        }

        // When
        val result = testedDetector.occludedViews(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        if (result[1]) assertThat(fakeFullyCovered).isTrue()
    }

    @ParameterizedTest
    @CsvSource(
        "100, true, true, NONE, false",
        "50, true, false, NONE, false",
        "100, false, false, NONE, false",
        "100, true, true, OPACITY, false",
        "100, true, true, SHADOW, false",
        "100, true, true, BLUR, false",
        "100, true, true, SHADOW, true",
        "100, true, true, BLUR, true",
        "50, true, false, SHADOW, false",
        "50, true, false, BLUR, false",
        "50, true, false, SHADOW, true",
        "50, true, false, BLUR, true"
    )
    fun `M cull fully covered compositions after host clipping W buildWindow`(
        fakeCoverWidth: Long,
        fakeGeometryReliable: Boolean,
        fakeCovered: Boolean,
        fakeEffect: CompositionEffect,
        fakeNestedEffect: Boolean
    ) {
        // Given
        val fakeRoot = view("root", -1, GlobalBounds(0, 0, 200, 200))
        val fakeHostIdentity = fakeFactory.composeHost(fakeWindow, "host")
        val fakeNodeIdentity = fakeFactory.composeNode(fakeHostIdentity, "node")
        val fakeInteropIdentity = fakeFactory.composeNode(fakeHostIdentity, "interop")
        val fakeShapeIdentity = fakeFactory.shapeWireframe(fakeNodeIdentity)
        val fakeModifiers = when (fakeEffect) {
            CompositionEffect.NONE -> emptyList()
            CompositionEffect.OPACITY -> listOf(CapturedModifier.Opacity(0.5))
            CompositionEffect.SHADOW -> listOf(CapturedModifier.Shadow("#000000FF", 10.0, 0.0, 5.0))
            CompositionEffect.BLUR -> listOf(CapturedModifier.GaussianBlur(5.0))
        }
        val fakeNode = CapturedLayer(
            fakeNodeIdentity,
            CapturedLayerKind.COMPOSE_NODE,
            CapturedBounds(0, 0, 100, 100),
            listOf(CapturedChild.Wireframe(fakeShapeIdentity)),
            modifiers = if (fakeNestedEffect) emptyList() else fakeModifiers
        )
        val fakeNative = view("native", -1).copy(
            identity = fakeInteropIdentity,
            kind = CapturedLayerKind.NATIVE_VIEW
        )
        val fakeInteropWireframe = CapturedWireframe.Shape(
            fakeFactory.shapeWireframe(fakeInteropIdentity),
            fakeNative.bounds
        )
        whenever(fakeNative.viewData.map(any()))
            .thenReturn(CapturedViewMapperResult.Wireframes(fakeInteropWireframe))
        val fakeNativeViews = mutableListOf(fakeNative)
        if (fakeNestedEffect) {
            val fakeNestedHost = fakeFactory.composeHost(fakeWindow, "nested-host")
            val fakeNestedNode = fakeFactory.composeNode(fakeNestedHost, "nested-node")
            val fakeNestedShape = fakeFactory.shapeWireframe(fakeNestedNode)
            fakeNativeViews += view("nested-host", 0).copy(
                identity = fakeNestedHost,
                kind = CapturedLayerKind.COMPOSE_HOST,
                viewData = CapturedViewData.Mapped(CapturedViewMapperResult.None),
                composition = CapturedComposition(
                    CompositionHostDecomposeResult(
                        listOf(CapturedChild.Layer(fakeNestedNode)),
                        listOf(
                            CapturedLayer(
                                fakeNestedNode,
                                CapturedLayerKind.COMPOSE_NODE,
                                fakeNode.bounds,
                                listOf(CapturedChild.Wireframe(fakeNestedShape)),
                                fakeModifiers
                            )
                        ),
                        listOf(CapturedWireframe.Shape(fakeNestedShape, fakeNode.bounds))
                    ),
                    emptyList()
                )
            )
        }
        val fakeHost = view("host", 1).copy(
            identity = fakeHostIdentity,
            kind = CapturedLayerKind.COMPOSE_HOST,
            viewData = CapturedViewData.Mapped(CapturedViewMapperResult.None),
            isGeometryReliable = fakeGeometryReliable,
            composition = CapturedComposition(
                CompositionHostDecomposeResult(
                    listOf(CapturedChild.Layer(fakeNodeIdentity), CapturedChild.Layer(fakeInteropIdentity)),
                    listOf(fakeNode),
                    listOf(CapturedWireframe.Shape(fakeShapeIdentity, fakeNode.bounds))
                ),
                listOf(CapturedNativeWindow(1f, fakeNativeViews))
            )
        )
        val fakeViews = listOf(
            fakeRoot,
            view("back", 0),
            fakeHost,
            view("front", 0),
            view("cover", 3, GlobalBounds(0, 0, fakeCoverWidth, 100), opaque = true)
        )
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val result = testedBuilder.buildWindow(CapturedNativeWindow(1f, fakeViews), fakeContext)

        // Then
        val window = checkNotNull(result)
        val identities = window.wireframes.map { it.identity }
        val hostLayer = window.layers.single { it.identity == fakeHostIdentity }
        assertThat(hostLayer.modifiers).containsExactly(CapturedModifier.Clip("M0 0 H100 V100 H0 Z"))
        if (fakeCovered) {
            assertThat(identities).doesNotContain(fakeShapeIdentity, fakeInteropWireframe.identity)
            assertThat(window.layers.map { it.identity }).doesNotContain(fakeNodeIdentity, fakeInteropIdentity)
            verifyNoInteractions(fakeNative.viewData)
        } else {
            assertThat(identities).contains(fakeShapeIdentity, fakeInteropWireframe.identity)
            verify(fakeNative.viewData).map(isA())
            val effectLayer = if (fakeNestedEffect) {
                val nestedHost = fakeFactory.composeHost(fakeWindow, "nested-host")
                assertThat(window.layers.single { it.identity == nestedHost }.modifiers)
                    .containsExactly(CapturedModifier.Clip("M0 0 H100 V100 H0 Z"))
                fakeFactory.composeNode(nestedHost, "nested-node")
            } else {
                fakeNodeIdentity
            }
            assertThat(window.layers.single { it.identity == effectLayer }.modifiers).isEqualTo(fakeModifiers)
        }
        assertThat(DefaultCapturedTreeValidator().validate(snapshot(window))).isEqualTo(CaptureValidationResult.Valid)
    }

    private fun view(
        fakeId: String,
        fakeParent: Int,
        fakePixels: GlobalBounds = fakeBounds,
        opaque: Boolean = false,
        bounded: Boolean = true,
        density: Float = 1f
    ): CapturedNativeView {
        val fakeIdentity = if (fakeParent < 0) fakeWindow else fakeFactory.view(fakeWindow, fakeId)
        val fakeWireframe = CapturedWireframe.Shape(
            fakeFactory.shapeWireframe(fakeIdentity),
            CapturedBounds(
                (fakePixels.x / density).toLong(),
                (fakePixels.y / density).toLong(),
                (fakePixels.width / density).toLong(),
                (fakePixels.height / density).toLong()
            )
        )
        val mockMapping: CapturedViewData = mock {
            on { isOcclusionBounded } doReturn bounded
            on { hasOpaqueBackground } doReturn opaque
            on { map(any()) } doReturn CapturedViewMapperResult.Wireframes(listOf(fakeWireframe))
        }
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
    internal enum class CompositionEffect {
        NONE,
        OPACITY,
        SHADOW,
        BLUR
    }
}
